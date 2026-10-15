package com.example.canaldemo.canal;

import java.util.Map;

/**
 * 解析后的 binlog 行变更事件，即业务回调的入参（一行一条）。
 *
 * @param database    库名
 * @param table       表名
 * @param eventType   INSERT / UPDATE / DELETE
 * @param executeTime 事件在 MySQL 执行的时间戳（毫秒）
 * @param before      变更前行镜像（列名 -> 值），INSERT 时为空
 * @param after       变更后行镜像（列名 -> 值），DELETE 时为空
 */
public record CanalEventMessage(String database,
                                String table,
                                String eventType,
                                long executeTime,
                                Map<String, String> before,
                                Map<String, String> after) {
}
