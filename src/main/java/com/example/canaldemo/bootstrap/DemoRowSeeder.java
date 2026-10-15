package com.example.canaldemo.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时向 demo_user 插入一条演示数据，用于直接验证 binlog 回调链路。
 * canal.auto-insert-demo-row=false 可关闭。
 */
@Component
@ConditionalOnProperty(prefix = "canal", name = "auto-insert-demo-row", havingValue = "true")
public class DemoRowSeeder
        implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRowSeeder.class);

    private final JdbcTemplate jdbcTemplate;

    public DemoRowSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String name = "user-" + System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO demo_user(name, email, balance) VALUES (?, ?, ?)", name, name + "@demo.com", 100.00);
        log.info("已插入演示数据: {}", name);
    }
}
