package com.example.canaldemo.canal;

/**
 * binlog 行变更回调接口：业务方实现并注册为 Spring Bean 即可收到数据变更。
 */
@FunctionalInterface
public interface CanalEventListener {

    void onChange(CanalEventMessage event);
}
