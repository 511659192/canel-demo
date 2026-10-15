-- demo 库与表（compose 的 MYSQL_DATABASE 也会建库，这里幂等再建一次）
CREATE DATABASE IF NOT EXISTS canal_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE canal_demo;

CREATE TABLE IF NOT EXISTS demo_user (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name       VARCHAR(64)     NOT NULL COMMENT '姓名',
  email      VARCHAR(128)    NULL COMMENT '邮箱',
  balance    DECIMAL(10, 2)  NOT NULL DEFAULT 0.00 COMMENT '余额',
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT = 'binlog 回调演示表';

-- 预置两条数据，方便手动 SELECT 对照
INSERT INTO demo_user (name, email, balance) VALUES
  ('alice', 'alice@demo.com', 100.00),
  ('bob',   'bob@demo.com',    50.00);

-- canal 复制专用账号：
-- 1) mysql_native_password：canal 1.1.x 对 MySQL8 默认的 caching_sha2_password 兼容不稳，直接用旧插件省事
-- 2) 复制所需最小权限：SELECT + REPLICATION SLAVE + REPLICATION CLIENT
CREATE USER IF NOT EXISTS 'canal'@'%' IDENTIFIED WITH mysql_native_password BY 'canal';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
