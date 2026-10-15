# canal binlog 回调 Demo（Spring Boot 4 · JDK 21 · MySQL 8 · canal 1.1.8）

MySQL 数据变更（INSERT/UPDATE/DELETE）→ MySQL binlog → canal-server 抓取 → 本应用 canal 客户端长轮询 → 触发 `CanalEventListener` 回调（当前实现为结构化日志打印）。

**MySQL 与 canal-server 均为 Docker 部署**，一条 `docker compose up -d` 全部拉起。

```
┌──────────────────┐  binlog   ┌───────────────────────┐  TCP:11111  ┌──────────────────────┐
│ MySQL 8.0 容器    │ ────────> │ canal-server 容器      │ ──────────> │ Spring Boot 4 应用    │
│ mysql:3306       │           │ canal/canal-server     │ getWithoutAck│ CanalEventListener   │
│ 宿主机端口 3307   │           │ 宿主机端口 11111        │ ack         │  → 日志回调(可替换)    │
└──────────────────┘           └───────────────────────┘             └──────────────────────┘
        └────────────── compose 内部网络：canal -> mysql:3306 ──────┘
```

## 组件与端口

| 组件 | 部署方式 | 地址 |
|---|---|---|
| MySQL 8.0 | Docker Compose | 宿主机 `localhost:3307`（3306 已被本机 MySQL 占用），容器内 3306，root/root |
| canal-server 1.1.8 | Docker Compose（`canal/canal-server:v1.1.8`） | 宿主机 `localhost:11111`；容器内经 compose 网络连 `mysql:3306`，账号 canal/canal（仅复制权限） |
| 本应用 | JDK 21 + Maven，本机运行 | 无 web 端口，纯后台订阅 |

## 快速开始

```bat
:: 1. 一键启动 MySQL + canal-server（首次自动建库表 demo_user、创建 canal 复制账号）
docker compose up -d

:: 2. 启动应用
mvn -DskipTests package
java -jar target\canal-binlog-demo-0.0.1-SNAPSHOT.jar
```

应用启动后会自动向 `demo_user` 插入一条演示数据（`canal.auto-insert-demo-row=false` 可关），控制台随即出现 `BINLOG-CALLBACK` 日志。之后可手动变更数据观察回调：

```sql
UPDATE canal_demo.demo_user SET balance = balance + 50 WHERE id = 1;
DELETE FROM canal_demo.demo_user WHERE id = 2;
INSERT INTO canal_demo.demo_user(name, email, balance) VALUES ('dave', 'dave@demo.com', 1.00);
```

## 接入自己的业务回调

实现 `CanalEventListener` 并注册为 Spring Bean，替换 `LoggingCanalEventListener` 即可，每行变更触发一次：

```java
@Component
public class MyListener implements CanalEventListener {
    public void onChange(CanalEventMessage event) {
        // event.database() / table() / eventType() / before() / after()
    }
}
```

## 关键配置

- 应用侧 `src/main/resources/application.yml`：canal 地址、destination、订阅过滤（默认 `canal_demo\..*`）、batch-size。
- canal 侧 [docker/canal/instance.properties](docker/canal/instance.properties)：以只读方式挂载进容器 `/home/admin/canal-server/conf/example/instance.properties`，MySQL 地址为 compose 服务名 `mysql:3306`，订阅过滤 `canal_demo\\..*`。
- MySQL 侧 `docker/mysql/conf/my.cnf`：`binlog_format=ROW`、`binlog_row_image=FULL`（canal 硬性要求）。

## 架构说明：Canal 接入模式与 Kafka 选型

### 当前实现：CanalConnector（TCP）模式

- 架构：Docker 运行 MySQL、Canal（无 Kafka）；Spring Boot 在本机运行，通过 `CanalConnector` 以 TCP 长轮询连接 Canal（`getWithoutAck()` 拉取、处理成功后 `ack()`），直接对接 Canal，不经 Kafka，也不连接 MySQL。
- 未确认（未 ack）的数据在断线/异常后可能重放，因此业务处理要支持幂等。
- Canal 内部有容量有限的缓冲，能应对短时积压。

### Canal Kafka 模式（当前未部署，可选扩展）

- 架构：Docker 运行 MySQL、Canal、Kafka；Spring Boot 在本机运行，只监听 Kafka（`@KafkaListener` 消费），不连接 MySQL。
- Canal 读取 MySQL binlog，解析后投递到 Kafka。
- Kafka 提供更长的消息保留和恢复窗口，也方便重放、扩展及多个消费组独立消费。
- 两种是不同的接入方式；若两种都要用，通常需要部署两个 Canal Server。
- 本地单 Broker 示例仅用于演示解耦，不具备 Broker 故障冗余。

### 是否需要 Kafka

- 单应用、需求简单时，直接用 CanalConnector 可能更省事（当前 demo 即是）。
- 需要应用与 Canal 解耦、多个消费者、或更长的积压恢复窗口时，Kafka 更合适。

## 停止与清理

```bat
:: 停应用：Ctrl+C
docker compose down        # 停并移除 MySQL + canal 容器，保留数据卷
docker compose down -v     # 连数据卷一起删
```

## 目录结构

```
docker-compose.yml            # MySQL 8.0(3307->3306) + canal-server(11111)
docker/mysql/conf/my.cnf      # ROW + FULL binlog 配置
docker/mysql/init/01-init.sql # 建库建表 + canal 账号 + 预置数据
docker/canal/instance.properties # 挂载进 canal 容器的 instance 配置
src/main/java/com/example/canaldemo/
  ├── canal/CanalClientContainer.java   # 长轮询订阅、ack、断线重连
  ├── canal/CanalEntryParser.java       # Entry -> 行变更事件
  ├── canal/CanalEventListener.java     # 回调接口
  ├── canal/CanalEventMessage.java      # 回调事件模型
  ├── listener/LoggingCanalEventListener.java # 默认回调：日志打印
  ├── bootstrap/DemoRowSeeder.java      # 启动插入演示数据
  └── config/CanalProperties.java       # canal.* 配置
canal-server/                 # （遗留）本机直跑方式的解压目录，Docker 方案下可删
canal-server-dl/              # （遗留）安装包，可删（约 110MB）
```

## 附：本机直跑 canal 的踩坑记录（遗留方式，已改用 Docker）

早期版本 canal-server 在 Windows 本机直跑，踩过以下坑，若需回归本机方式可参考（解压目录 `canal-server/` 中的 `bin/startup.bat` 已做对应适配）：

1. `bin/startup.bat` 三处适配：优先用 `%JAVA_HOME%\bin\java`（PATH 无 java）；去掉 JDK21 不再支持的 `-XX:PermSize`；启动前 `cd` 到 bin 目录（canal 的 `../conf`、`../logs` 全是相对路径，从别处启动会导致实例起不来、日志写错位置）。
2. canal.client 1.1.8 不传递依赖 `canal.protocol`（`CanalEntry`/`Message` 所在包），pom 里需显式声明。
3. 非 web 的 Spring Boot 应用默认主线程结束即退出，本 demo 用非 daemon 的订阅线程常驻。
