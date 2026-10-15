package com.example.canaldemo.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.example.canaldemo.config.CanalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * canal 客户端容器：与 canal-server 保持长连接，拉取 binlog 并逐行回调监听器。
 * 断线自动重连；未 ack 的批次先 rollback 再重连，避免丢事件。
 */
@Component
public class CanalClientContainer
        implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CanalClientContainer.class);
    private static final long EMPTY_BATCH_WAIT_MS = 1000;
    private static final long RECONNECT_WAIT_MS = 3000;

    private final CanalProperties properties;
    private final CanalEntryParser parser;
    private final CanalEventListener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread worker;

    public CanalClientContainer(CanalProperties properties,
                                CanalEntryParser parser,
                                CanalEventListener listener) {
        this.properties = properties;
        this.parser = parser;
        this.listener = listener;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = new Thread(this::consumeLoop, "canal-client");
        // 非 daemon：binlog 订阅是本应用的常驻主业务，线程存活即应用存活
        worker.setDaemon(false);
        worker.start();
        log.info("canal 客户端线程已启动, {}:{}", properties.host(), properties.port());
    }

    private void consumeLoop() {
        while (running.get()) {
            CanalConnector connector = CanalConnectors.newSingleConnector(new InetSocketAddress(properties.host(), properties.port()),
                                                                          properties.destination(),
                                                                          properties.username(),
                                                                          properties.password());
            try {
                connector.connect();
                connector.subscribe(properties.subscribe());
                log.info("canal 客户端已连接, destination={}, 订阅过滤: {}", properties.destination(), properties.subscribe());
                poll(connector);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 容器停止，正常退出
            } catch (Exception e) {
                if (running.get()) {
                    log.warn("canal 连接异常, {}ms 后重连: {}", RECONNECT_WAIT_MS, e.getMessage());
                    rollbackQuietly(connector);
                    sleepQuietly(RECONNECT_WAIT_MS);
                }
            } finally {
                try {
                    connector.disconnect();
                } catch (Exception ignore) {
                    // 连接可能早已断开，忽略
                }
            }
        }
        log.info("canal 客户端线程已退出");
    }

    private void poll(CanalConnector connector) throws InterruptedException {
        while (running.get()) {
            Message message = connector.getWithoutAck(properties.batchSize());
            long batchId = message.getId();
            List<CanalEntry.Entry> entries = message.getEntries();
            if (batchId == -1 || entries.isEmpty()) {
                Thread.sleep(EMPTY_BATCH_WAIT_MS);
                continue;
            }
            for (CanalEventMessage event : parser.parse(entries)) {
                try {
                    listener.onChange(event);
                } catch (Exception e) {
                    // 单条回调异常不阻塞订阅主流程，ack 后继续消费
                    log.error("binlog 回调处理失败, event={}", event, e);
                }
            }
            connector.ack(batchId);
        }
    }

    private void rollbackQuietly(CanalConnector connector) {
        try {
            connector.rollback();
        } catch (Exception ignore) {
            // 连接已失效时 rollback 必然失败，直接重连即可
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        running.set(false);
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
            worker = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
