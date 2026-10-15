package com.example.canaldemo.listener;

import com.example.canaldemo.canal.CanalEventMessage;
import com.example.canaldemo.canal.CanalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认回调实现：把收到的行变更结构化打印到控制台。
 * 想接自己的业务，另写一个 CanalEventListener 实现并替换本类即可。
 */
@Component
public class LoggingCanalEventListener
        implements CanalEventListener {

    private static final Logger log = LoggerFactory.getLogger("BINLOG-CALLBACK");

    @Override
    public void onChange(CanalEventMessage event) {
        log.info("收到 binlog 变更 -> {}.{} [{}] executeTime={}", event.database(), event.table(), event.eventType(), event.executeTime());
        switch (event.eventType()) {
            case "INSERT" -> log.info("    新数据: {}", event.after());
            case "DELETE" -> log.info("    旧数据: {}", event.before());
            case "UPDATE" -> {
                log.info("    旧数据: {}", event.before());
                log.info("    新数据: {}", event.after());
            }
            default -> log.info("    before={}, after={}", event.before(), event.after());
        }
    }
}
