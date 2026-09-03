# Node-Server — Node-RED 中心管理平台

Java Spring Boot 服务，对应 `docs/auto-register.md` 中的「平台侧」。
实现 Agent 调用的注册 / 心跳 / 注销三大接口，并提供管理后台的实例与 Token 管理接口。

## 一、技术栈

| 组件         | 版本                           | 说明                          |
| ------------ | ------------------------------ | ----------------------------- |
| Spring Boot  | 3.2.5                          | Web + Scheduling + Validation |
| MyBatis-Plus | 3.5.5                          | ORM                           |
| 数据库       | H2（默认） / MySQL 8.x（生产） | 内置 H2，零配置启动           |
| Hutool       | 5.8.27                         | 工具集                        |
| Lombok       | -                              | 简化代码                      |
| Java         | 17                             | -                             |

## 二、运行

```bash
cd node-server
mvn spring-boot:run
```

或打包：

```bash
mvn clean package -DskipTests
java -jar target/node-server.jar
```

> 默认端口 8080，接口无统一前缀（对外前缀由 nginx 等网关配置），详见下节。
> H2 控制台：`http://localhost:8080/h2-console`
> JDBC URL：`jdbc:h2:file:./data/nodedb` 用户名：`sa` 密码：空

## 三、数据库

| 表               | 作用          |
| ---------------- | ------------- |
| `t_instance`     | Node-RED 实例 |
| `t_token`        | 预授权凭证    |
| `t_instance_log` | 操作日志      |

脚本见 [src/main/resources/db/schema.sql](src/main/resources/db/schema.sql)，
首次启动自动执行 [data.sql](src/main/resources/db/data.sql) 写入演示 Token。

### 切换到 MySQL

修改 `application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/node_server?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: xxxxx
```

schema.sql / data.sql 已使用 `MODE=MySQL` 兼容语法，可直接复用。

## 四、接口

完整列表与字段对齐 `docs/auto-register.md` 的「四、平台接口」。

### Agent → 平台（需 `Authorization: Bearer <TOKEN>`）

| 方法 | 路径                       | 说明                    |
| ---- | -------------------------- | ----------------------- |
| POST | `/api/v1/agent/register`   | 注册 / 重新注册（幂等） |
| POST | `/api/v1/agent/heartbeat`  | 心跳                    |
| POST | `/api/v1/agent/deregister` | 主动注销                |

### 管理后台

| 方法   | 路径                          | 说明                                             |
| ------ | ----------------------------- | ------------------------------------------------ |
| GET    | `/api/v1/instances`           | 实例列表（支持 `?status=&bindStatus=&keyword=`） |
| GET    | `/api/v1/instances/{id}`      | 实例详情                                         |
| PUT    | `/api/v1/instances/{id}/bind` | 绑定                                             |
| DELETE | `/api/v1/instances/{id}`      | 手动注销                                         |
| GET    | `/api/v1/instances/_stats`    | 概览统计                                         |
| GET    | `/api/v1/instances/{id}/logs` | 实例操作日志                                     |
| GET    | `/api/v1/tokens`              | Token 列表                                       |
| POST   | `/api/v1/tokens`              | 创建 Token（body: `{ "remark": "..." }`）        |
| POST   | `/api/v1/tokens/{id}/revoke`  | 吊销 Token                                       |
| POST   | `/api/v1/tokens/{id}/enable`  | 重新启用                                         |
| DELETE | `/api/v1/tokens/{id}`         | 删除 Token                                       |

> 管理后台暂未接入账号体系，生产环境务必在网关层做 IP 白名单 / 加一层统一登录。

## 五、Agent 错误响应约定

| HTTP    | 业务 code   | Agent 行为                        |
| ------- | ----------- | --------------------------------- |
| 200     | 0           | 成功                              |
| 200     | ≠0          | 业务失败，Agent 不重试            |
| **401** | 2001 / 2002 | 凭证无效 / 已吊销，Agent 重新注册 |
| **404** | 4001        | 实例不存在，Agent 重新注册        |
| **410** | 4002        | 实例已注销，Agent 重新注册        |

## 六、定时任务

`HeartbeatCheckTask` 每 30s 扫描一次（可通过 `platform.scan-interval-seconds` 调整）：

1. `lastHeartbeat < now - 2 × 心跳间隔(30s)` 的在线实例 → 标记 `offline`
2. `lastHeartbeat < now - 24h` 的离线实例 → 自动 `deregistered`

## 七、curl 验证示例

> 演示 Token 在 data.sql 中：`demo-token-001`

```bash
# 1. 注册
curl -X POST http://localhost:8080/agent/register \
  -H "Authorization: Bearer demo-token-001" \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "nr-test-001",
    "name": "test-host",
    "ip": "192.168.1.100",
    "port": 1880,
    "platform": "linux",
    "arch": "x64",
    "nodeVersion": "v18.19.0",
    "nodeRedVersion": "5.0.4",
    "startTime": "2026-09-02 10:00:00"
  }'

# 2. 心跳
curl -X POST http://localhost:8080/agent/heartbeat \
  -H "Authorization: Bearer demo-token-001" \
  -H "Content-Type: application/json" \
  -d '{"instanceId":"nr-test-001"}'

# 3. 查看实例
curl http://localhost:8080/instances

# 4. 统计
curl http://localhost:8080/instances/_stats
```

## 八、目录结构

```
node-server/
├── pom.xml
├── src/main/java/com/platform/
│   ├── NodeServerApplication.java     # 启动类
│   ├── common/                         # 公共响应 / 异常 / 业务码
│   ├── config/                         # Web / MyBatis-Plus 配置
│   ├── controller/                     # AgentController / InstanceController / TokenController / InstanceLogController
│   ├── dto/                            # Agent 请求体
│   ├── entity/                         # Instance / Token / InstanceLog
│   ├── interceptor/                    # AgentAuthInterceptor（Token 鉴权）
│   ├── mapper/                         # MyBatis-Plus Mapper
│   ├── service/                        # 业务接口与实现
│   └── task/                           # HeartbeatCheckTask 定时巡检
└── src/main/resources/
    ├── application.yml
    └── db/
        ├── schema.sql
        └── data.sql
```
