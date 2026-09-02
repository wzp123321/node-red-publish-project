# Node-RED 自动注册机制 — 流程说明

> 适用场景：多现场 node-red 实例统一注册到中心平台，实现集中状态监测
> （在线/离线、已绑定/未绑定）与实例生命周期管理。
> 相关：完整部署手册见 [deploy.md](./deploy.md)「七、自动注册机制」。

## 一、整体流程

### 1. 构建阶段（构建机，有外网）

1. 进入 `deploy/` 目录执行 `docker build -t node-red-custom:5.0.4 .`
2. 镜像内烧入三样东西：
   - 预装第三方节点（原逻辑不变）
   - `agent/main.js` —— 自动注册 Agent 主程序（零依赖，仅用 Node 内置 http/https）
   - `agent-entrypoint.sh` —— 容器启动入口（后台拉起 agent，再 exec 官方 entrypoint 启动 node-red）
3. `docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz` 导出离线包带到现场

### 2. 现场部署阶段（现场，离线）

1. `docker load -i node-red-custom-5.0.4.tar.gz` 导入镜像
2. `docker run` 启动，**显式加 3 个环境变量启用注册**（见下表）
3. 不加 `AGENT_*` 环境变量 → agent 启动即退出，行为与老镜像完全一致，现有部署命令无需改动

### 3. 容器启动后（agent 自动执行）

1. 入口脚本后台拉起 agent，随后 exec 官方入口启动 node-red（两进程相互独立）
2. agent 携带预授权凭证调 `POST /agent/register` → 注册成功 → 平台记录 **在线/未绑定**
   - 注册失败：指数退避重试（5s → 60s 封顶），**不阻塞** node-red 启动
3. 注册成功后每 30s 发一次心跳 `POST /agent/heartbeat`，平台刷新最后心跳时间

### 4. 运行期状态流转（平台侧负责）

| 事件                             | 平台动作                                      |
| -------------------------------- | --------------------------------------------- |
| 心跳正常                         | 保持**在线**                                  |
| 超 2×心跳周期无心跳              | 标记**离线**                                  |
| 心跳恢复                         | 自动回**在线**                                |
| 长时间离线（如 24h，平台定阈值） | 自动**注销**                                  |
| 管理员手动注销                   | 实例变**已注销**                              |
| 容器重启 / 断网重连              | agent 重新注册（幂等），自动恢复**在线**      |
| 实例被注销后 agent 再发心跳      | 平台回 404 / `code=4001` → agent 自动重新注册 |

## 二、环境变量

| 变量                       | 必填       | 说明                                                               |
| -------------------------- | ---------- | ------------------------------------------------------------------ |
| `AGENT_ENABLED`            | 否         | `true` 启用注册，默认不启用                                        |
| `AGENT_API_BASE`           | 启用时必填 | 平台 API 基础地址，如 `http://平台IP:8080/api/v1`                  |
| `AGENT_TOKEN`              | 启用时必填 | **预授权凭证**，平台预生成、一实例一个，防止恶意注册               |
| `AGENT_INSTANCE_ID`        | 否         | 实例 ID；缺省自动生成并持久化 `/data/.agent-instance-id`，重启不变 |
| `AGENT_HEARTBEAT_INTERVAL` | 否         | 心跳间隔（秒），默认 30                                            |

> 凭证安全：`AGENT_TOKEN` 通过环境变量传入，不写进镜像与流程文件；平台可随时吊销。
> 若需长期停用某实例，吊销其 Token 即可阻止重新注册。

## 三、路径与变量来源

### 1. 固定路径（镜像约定 + Dockerfile 决定）

| 路径                              | 来源                                                                                                    |
| --------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `/usr/src/node-red/agent`         | 官方镜像工作目录为 `/usr/src/node-red`，Dockerfile `COPY agent/ /usr/src/node-red/agent/` 将 agent 拷入 |
| `/usr/src/node-red/entrypoint.sh` | 官方镜像**自带**入口脚本，agent-entrypoint.sh 末尾 `exec` 它来正常启动 node-red                         |
| `/data/.agent-instance-id`        | `/data` 是官方镜像声明的数据卷，现场 `-v $(pwd)/data:/data` 挂到宿主机；实例 ID 持久化于此，重启不变    |

### 2. 环境变量（docker run 时注入）

- `AGENT_*` 全部来自启动命令的 `-e AGENT_XXX=...`，由**部署人员**填写
- 其中 `AGENT_TOKEN` 的值由**平台预先生成**（一实例一个），不是脚本自造的
- `PORT`（Node-RED 监听端口，docker run 时与 `-p` 映射保持一致显式传入，见 deploy.md）：agent 与 node-red 同容器共享环境变量，注册时一并上报给平台；未传时按官方默认 1880 上报

### 3. 运行时自动获取（Node/系统内置，无需配置）

| 值                                                      | 来源                                                    |
| ------------------------------------------------------- | ------------------------------------------------------- |
| `NODE_RED_VERSION`                                      | 官方镜像预置的环境变量（`ENV NODE_RED_VERSION=v5.0.4`） |
| 容器主机名（`os.hostname()`）                           | Node 内置 os 模块读取                                   |
| 本机 IP（`os.networkInterfaces()`）                     | Node 读取容器网卡                                       |
| `process.platform` / `process.arch` / `process.version` | Node 运行时自身信息                                     |

## 四、平台接口

统一请求头：`Authorization: Bearer <AGENT_TOKEN>`；
统一响应：`{ "code": 0, "message": "ok", "data": {...} }`，`code != 0` 为业务失败。

**Agent → 平台：**

| 方法 | 路径                | 请求体                                                                                   | 说明                                                             |
| ---- | ------------------- | ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| POST | `/agent/register`   | `{ instanceId, name, ip, port, platform, arch, nodeVersion, nodeRedVersion, startTime }` | 注册/重新注册（幂等）；HTTP 401 = 凭证无效                       |
| POST | `/agent/heartbeat`  | `{ instanceId }`                                                                         | 心跳；HTTP 404 或 `code=4001` 表示实例不存在，agent 自动重新注册 |
| POST | `/agent/deregister` | `{ instanceId }`                                                                         | 主动注销（agent 退出时尽力调用）                                 |

**管理后台（平台自实现）：**

| 方法   | 路径                   | 说明                                               |
| ------ | ---------------------- | -------------------------------------------------- |
| GET    | `/instances`           | 实例列表（在线/离线、已绑定/未绑定、最后心跳时间） |
| PUT    | `/instances/{id}/bind` | 绑定（未绑定 → 已绑定）                            |
| DELETE | `/instances/{id}`      | 手动注销                                           |

平台定时任务：`lastHeartbeat` 超过 2×心跳周期 → 离线；超过注销阈值（如 24h）→ 自动注销。

## 五、注意事项

- **必须重新打镜像**：agent 烧在镜像层，老镜像（含官方镜像）不支持自动注册。
- **现场网络**：启用注册要求现场容器能出站访问 `AGENT_API_BASE`（HTTP/HTTPS 出站）。
- **docker stop 场景**：SIGTERM 只发给 PID 1（node-red），agent 主动注销属尽力而为，平台最终以心跳超时为准。
- **node-red 编程交互**：本机制只负责实例生命周期管理；如需平台远程下发/查看流程，另走 Node-RED Admin API（配 `adminAuth` Token），两者互不依赖。
