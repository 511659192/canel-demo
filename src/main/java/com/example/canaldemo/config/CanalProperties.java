package com.example.canaldemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * canal 客户端配置（application.yml 的 canal.* 前缀）。
 */
@ConfigurationProperties(prefix = "canal")
public record CanalProperties(String host,
                              int port,
                              String destination,
                              String username,
                              String password,
                              String subscribe,
                              int batchSize,
                              boolean autoInsertDemoRow) {

    public CanalProperties {
        // yml 未配置时的兜底值
        if (host == null) {
            host = "127.0.0.1";
        }
        if (destination == null) {
            destination = "example";
        }
        if (subscribe == null) {
            subscribe = "canal_demo\\..*";
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
    }
}
