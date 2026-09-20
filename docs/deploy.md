# Node-RED Docker 部署手册

## 一、在线部署（服务器能连外网）

官方镜像只含核心节点，不含 dashboard 等第三方节点。

> **先准备配置文件（在** **`mkdir -p data`** **之前）**：本方式需要 `settings.js` 与 `Dockerfile` 两个文件，按下面的目录结构放好后再执行启动命令。

**部署目录结构**（将仓库 `deploy/` 目录整体拷贝到服务器，作为工作目录）：

```text
deploy/                      # 部署工作目录
├── Dockerfile               # 预装节点定义（方式 A 直跑官方镜像，此文件预留备用）
├── agent-entrypoint.sh      # 容器启动入口（启动自动注册 agent 后再启动 node-red）
├── agent/
│   └── main.js              # 自动注册 agent 主程序（零依赖，随镜像烧入）
├── settings/
│   └── settings.js          # 双账户认证配置（方式 A 挂载到容器内非 /data 路径；
│                            #   方式 B 构建期烧入镜像，现场无需携带）
└── data/                    # 数据持久化目录（由下方 mkdir -p data 生成）
```

- `settings.js` 放在 `deploy/settings/settings.js`。
- `Dockerfile` 放在工作目录根 `deploy/Dockerfile`。

> **settings.js 为什么不挂到 `/data/settings.js`**：node-red 在 `/data` 找不到 settings.js 时，会把自带的默认配置**复制一份到 `/data`**；宿主机 `./data` 属主不是 1000 时这一步直接 EACCES，容器反复重启。所以 settings.js 一律走 `--settings` 绝对路径加载，与 `/data` 的挂载、权限完全解耦。

**settings/settings.js 内容说明**（完整版见仓库 `deploy/settings/settings.js`，要点如下）：

| 配置项             | 作用                                                                                                                                        |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `credentialSecret` | 凭据加密密钥。当前为注释状态（未显式设置），由 node-red 首次启动自动生成并存入 `/data/.config.runtime.json`，故备份 `./data` 即可（见 4.2） |
| `adminAuth`        | 编程页面 `/` 登录认证：`admin` / `3er4#ER$`（完全权限）                                                                                     |
| `httpNodeAuth`     | 前端页面 `/dashboard` 浏览器 Basic 认证：`user` / `Nts@1234`                                                                                |
| `uiPort`           | `process.env.PORT \|\| 1880`：端口由启动命令 `-e PORT` 指定（多实例可共用同一份 settings.js），需与 `-p` 端口映射保持一致                   |
| `externalModules`  | `autoInstall: true` 允许编辑器在线安装节点（仅在线场景需要）                                                                                |

**Dockerfile 内容说明**（完整版见仓库 `deploy/Dockerfile`，方式 A 不参与构建）：

```dockerfile
FROM nodered/node-red:5.0.4   # 官方基座，方式 A 默认不预装第三方节点
USER node-red
# 需要预装节点时按此格式追加（锁定版本，构建前用 npm view <包名> version 查最新）：
# RUN npm install --no-audit --no-fund <node-package>@<version>
```

> 方式 A 直接使用官方镜像启动，`Dockerfile` 仅预留；需要预装 dashboard 等节点时改用方式 B。

```bash
# 1. 创建数据目录
mkdir -p data

# 2. 拉取并启动官方镜像（数据卷持久化 + 双账户认证配置）
#    settings.js 挂到容器内非 /data 路径，命令末尾用 --settings 指定（官方镜像不含本仓库的 settings.js，必须挂载）
# $(pwd)表示当前目录，即/deploy目录
docker run -d --name node-red -p 1880:1880  -e PORT=1880  -e TZ=Asia/Shanghai  -v $(pwd)/data:/data  -v $(pwd)/settings/settings.js:/usr/src/node-red/settings.js  --restart unless-stopped  nodered/node-red:5.0.4  --settings /usr/src/node-red/settings.js

# 3. 验证
docker ps | grep node-red      # 状态应为 Up
docker logs node-red           # 出现 "Server now running"，且其中一行 Settings file : /usr/src/node-red/settings.js
curl -I http://localhost:1880  # 返回 200
```

浏览器访问 `http://服务器IP:1880`，登录页出现即成功（账户见第三章）。

---

## 二、镜像打包与离线部署（给现场）

### 2.1 打包需要哪些文件

| 文件                 | 作用                         | 构建机（打包） | 现场（运行） |
| -------------------- | ---------------------------- | -------------- | ------------ |
| Dockerfile           | 定义镜像内容（预装哪些节点） | ✅ 需要        | ❌ 不需要    |
| settings/settings.js | 双账户认证配置（构建期烧入） | ✅ 需要        | ❌ 不需要    |
| data/                | 数据持久化目录               | 自动生成       | ✅ 需要      |

> 方式 B 的 settings.js 在构建期就烧进镜像了（`COPY settings/settings.js /usr/src/node-red/settings.js`），现场只需带镜像包 + 数据目录，不需要再携带/挂载 settings.js。

### 2.2 构建机（有外网）执行哪些命令

```bash
# 1. 上传 deploy/ 目录，进入后构建镜像
cd deploy
docker build -t node-red-custom:5.0.4 .

# 2. （可选）先验证一次：启动 → 浏览器确认登录页和节点 → 停止
# 需要指定域名nginxIn.tshlms.com
docker run -d --name node-red-custom-1881 -p 1881:1881 --add-host nginxIn.tshlms.com:192.168.50.177  -e TZ=Asia/Shanghai  -e PORT=1881  -v $(pwd)/data-1881:/data   --restart unless-stopped  node-red-custom:5.0.4

docker stop node-red-custom && docker rm node-red-custom

# 3. 导出离线包
docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz
```

**产出物（带到现场）**：`node-red-custom-5.0.4.tar.gz` + 整个 `deploy/` 目录。

### 2.3 现场（离线）执行哪些命令

```bash
# 1. 导入镜像（本地导入，全程不联网）
docker load < node-red-custom-5.0.4.tar.gz

# 2. 进入 deploy/ 目录，准备数据目录（settings.js 已烧入镜像，无需携带）
cd deploy
mkdir -p data

# 3. 若已有同名容器先清理
docker rm -f node-red-custom 2>/dev/null || true

# 4. 数据卷权限（若容器反复重启，多为此问题）
sudo chown -R 1000:1000 ./data/

# 5. 启动（仅挂载数据持久化；认证配置由镜像内 settings.js 提供）
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  -v $(pwd)/data:/data \
  --restart unless-stopped \
  node-red-custom:5.0.4

# 6. 验证
docker ps | grep node-red-custom       # 状态应为 Up
docker logs node-red-custom            # 出现 "Server now running" 且 Settings file : /usr/src/node-red/settings.js
```

浏览器访问 `http://现场IP:1880`。

> 若容器反复重启，多为数据卷权限问题：`sudo chown -R 1000:1000 ./data/` 后重新启动。
> 不想每次 chown 的话可改用命名卷：`-v node-red-data:/data`。首次启动 Docker 会用镜像内 /data 的属主（1000:1000）初始化，天然无权限问题；代价是备份/迁移要改用 `docker cp` 或临时容器打包，不再直接操作宿主机目录。

### 2.3.1 修改访问端口

改用其他端口时，`-p` 与 `-e PORT` 需保持一致（示例改为 1890；`-e PORT` 未传时容器内回落 1880）：

```bash
docker rm -f node-red-custom
docker run -d --name node-red-custom -p 1890:1890  -e TZ=Asia/Shanghai  -e PORT=1890   -v $(pwd)/data:/data  --restart unless-stopped   node-red-custom:5.0.4
```

> Windows PowerShell 把 `$(pwd)` 换成 `${PWD}`；`bash` 脚本同理可用。

### 2.4 现场需要新增节点怎么办

现场禁止安装节点。需在**构建机**上改 Dockerfile 加一行，重新打包：

```bash
# 构建机：在 Dockerfile 追加（锁定版本，构建前用 npm view <包名> version 查最新版）
#   RUN npm install --no-audit --no-fund <包名>@<版本>
docker build -t node-red-custom:5.0.4 .
docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz
```

将新包带到现场：`docker load -i` → 备份 `./data` → 按 2.3 重新启动。

### 2.5 修改 settings.js（认证账户 / 端口等）

settings.js 已烧入镜像，改配置需**重新构建镜像**（不再是"改宿主机文件重启即可"）：

1. 构建机修改 `deploy/settings/settings.js`
2. `docker build -t node-red-custom:5.0.4 .` → `docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz`
3. 现场 `docker load -i` → `docker rm -f node-red-custom` → 按 2.3 启动（`./data` 不动）

不重新构建的临时覆盖（现场排障用）：把文件挂到容器内任意路径，并在命令末尾用 `--settings` 指定：

```bash
docker run -d --name node-red-custom -p 1880:1880 -e PORT=1880 \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/tmp/settings.js \
  --restart unless-stopped \
  node-red-custom:5.0.4 --settings /tmp/settings.js
```

> 命令末尾的参数会替换镜像内 CMD，从而顶掉默认的 `--settings /usr/src/node-red/settings.js`。
> 注意 `/data/settings.js` 不再参与任何加载流程：`--settings` 的优先级高于 userDir 下的 settings.js，老现场遗留在 `/data` 里的旧 settings.js 会被忽略（可放心删除）。

---

## 三、双入口认证（部署后登录用）

| 入口     | 地址                       | 账户    | 密码       | 认证方式          | 权限                        |
| -------- | -------------------------- | ------- | ---------- | ----------------- | --------------------------- |
| 编程页面 | `http://IP:1880/`          | `admin` | `3er4#ER$` | Node-RED 登录页   | 完全权限（可编辑/部署流程） |
| 前端页面 | `http://IP:1880/dashboard` | `user`  | `Nts@1234` | 浏览器 Basic 弹窗 | 查看 UI 界面                |

---

## 四、数据持久化

- 数据卷映射：`./data:/data`，容器内数据（flows、凭据）均落盘到宿主机 `./data`。
- 预装节点与 `settings.js` 都在镜像层，容器重建不影响；改 `settings.js` 需重新构建镜像（见 2.5）。数据卷内容（`./data`）需单独备份。
- **备份**：先 `docker stop node-red-custom` 停容器（保证文件写入完整）→ 打包 `./data` 目录 → 再 `docker start node-red-custom`。
- **迁移**：新服务器放置 `./data` 与交付文件后按 2.3 启动。
- **删除容器不丢数据**：`docker rm -f node-red-custom` 只删容器，`./data` 保留。

### 4.1 sqlite 数据库文件路径（务必配置绝对路径）

sqlite 节点不做相对路径解析，db 文件位置取决于你在节点配置 `Database` 里填的路径：

| 填写的路径              | db 文件实际位置                                          | 持久化                  |
| ----------------------- | -------------------------------------------------------- | ----------------------- |
| `mydb.db`（相对）       | 容器内 `/usr/src/node-red/mydb.db`（工作目录，非数据卷） | ❌ 容器重建即丢失       |
| `./data/mydb.db`        | 容器内 `/usr/src/node-red/data/`                         | ❌ 同上                 |
| `/data/mydb.db`（绝对） | 容器内 `/data/` → 宿主机 `./data/`                       | ✅ 随 `./data` 备份迁移 |

**务必填绝对路径**，例如 `/data/mydb.db`，db 文件才会落在宿主机 `./data` 下并与 flows、凭据一起持久化。现场离线场景如需带已有 db 文件，直接放入 `./data/` 后启动即可。

> **通用原则**：凡是要落盘的业务数据（sqlite、file 节点、context 等），路径统一写 `/data/` 开头的绝对路径；任何相对路径或指向镜像目录（如 `/usr/src/node-red`）的写入，容器重建即丢失。

### 4.2 备份与迁移注意事项

- **先停后备份**：容器运行中直接拷贝 `./data` 可能拷到写入一半的文件（flows.json、sqlite），导致备份损坏。务必按第四章"备份"步骤先 `docker stop node-red-custom`。
- **sqlite WAL 附属文件**：若 db 启用了 WAL 模式（`journal_mode=WAL`），还会生成 `xxx.db-wal`、`xxx.db-shm`，只拷 `.db` 主文件会丢未合并数据。停容器后 WAL 自动合并；确需在线备份可用 sqlite3 的 `.backup` 命令。
- **凭据备份**：`flows_cred.json` 由加密密钥解密。当前 `settings.js` 未显式设置 `credentialSecret`，node-red 首次启动时会自动生成密钥并存到 `./data/.config.runtime.json` —— 密钥本身就在数据卷里，因此**只备份 `./data`（含隐藏文件）即可**。若将来在 `settings.js` 里显式配置了 `credentialSecret`，则新镜像必须沿用同一值，否则旧 `flows_cred.json` 无法解密。

---

## 五、安装节点（仅在线部署需要）

现场离线场景禁止执行，节点已预装在镜像中。

- 界面安装：登录编程页面 → 右上角菜单 → Manage palette → Install → 搜索安装
- 命令安装：

```bash
docker exec -u node-red -w /data node-red npm install @flowfuse/node-red-dashboard
docker restart node-red
```

> 命令安装的节点写入 `/data/node_modules`（已持久化），重启/重建容器不丢。

---

## 六、常用运维命令

| 操作     | 命令                                     |
| -------- | ---------------------------------------- |
| 查看状态 | `docker ps`                              |
| 查看日志 | `docker logs -f node-red`                |
| 重启     | `docker restart node-red`                |
| 停止     | `docker stop node-red`                   |
| 升级镜像 | 备份 `./data` → 重新构建/拉取 → 重新启动 |

---

## 七、自动注册机制（集中管理场景）

> 适用场景：多现场 node-red 实例统一注册到中心平台，实现集中状态监测
> （在线/离线、已绑定/未绑定）与实例生命周期管理。未传 `AGENT_API_BASE`
> 时 agent 启动即退出，行为与老镜像完全一致。

### 7.1 架构与状态机

- 实例侧：镜像内置自动注册 Agent（`deploy/agent/main.js`，零依赖），随容器启动：
  注册 → 定时心跳 → 退出时注销（尽力而为）。
- 平台侧：提供注册/心跳/注销接口 + 管理后台。状态机：

```text
未注册 ──注册──▶ 在线/未绑定 ──平台绑定──▶ 在线/已绑定
                    ▲                          │
        心跳恢复/重新注册│                        │ 心跳超时（2×心跳周期）
                    │                          ▼
                    └────── 离线 ──长时间离线（如24h）──▶ 自动注销
                                平台手动注销 ───────────────▶ 已注销
```

- 实例重启/断网重连：重新注册（幂等，同一 instanceId）即自动恢复在线。

### 7.2 环境变量

| 变量                       | 必填                   | 默认值     | 说明                                                                                                                                                       |
| -------------------------- | ---------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AGENT_API_BASE`           | **启用自动注册时必填** | 无         | 平台 API 基础地址，如 `http://平台IP:8080`（经 nginx 代理时含代理前缀，如 `http://平台IP:8899/zp-api`）。**不传则 agent 启动即退出**，行为与老镜像完全一致 |
| `AGENT_HEARTBEAT_INTERVAL` | 否                     | `60`（秒） | agent 上报心跳的轮询间隔，整数秒，≥1。传 100 就是 100 秒，依此类推；传 `0` 或空串走默认；含小数非整数部分被 `Number()` 截断为整数                          |
| `PORT`                     | 否                     | `1880`     | Node-RED 监听端口，同时作为实例 ID 后缀 `nodered-${PORT}`（实例 ID = `Node-RED-${PORT}`）。需与 `-p` 端口映射保持一致                                      |

#### 7.2.1 启动传参示例

启用自动注册（标准场景）：

```bash
docker run -d --name node-red-custom \
  -p 1888:1888 \
  -e TZ=Asia/Shanghai \
  -e PORT=1888 \
  -e AGENT_API_BASE=http://192.168.41.198:8200 \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

启用注册 + 自定义心跳间隔（100 秒）：

```bash
docker run -d --name node-red-custom \
  -p 1888:1888 \
  -e TZ=Asia/Shanghai \
  -e PORT=1888 \
  -e AGENT_API_BASE=http://192.168.41.198:8200 \
  -e AGENT_HEARTBEAT_INTERVAL=100 \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

不启用注册（与老镜像一致，纯本地运行）：

```bash
docker run -d --name node-red-custom \
  -p 1888:1888 \
  -e TZ=Asia/Shanghai \
  -e PORT=1888 \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

**验证日志**：`docker logs -f node-red-custom`

- 启用时见：`[agent] 自动注册启动：instanceId=Node-RED-1888 心跳间隔=60s API=http://...`
- 未启用则**完全没有** `[agent]` 相关行（agent 检测到缺 `AGENT_API_BASE` 后立即退出）

### 7.3 平台接口规范（Agent → 平台）

统一响应：`{ "code": 0, "message": "ok", "data": {...} }`，`code != 0` 为业务失败。

| 方法 | 路径                        | 请求体                     | 说明                                                             |
| ---- | --------------------------- | -------------------------- | ---------------------------------------------------------------- |
| POST | `/tenant/nodeRed/register`  | `{ instanceId, ip, port }` | 注册/重新注册（幂等）                                            |
| POST | `/tenant/nodeRed/heartbeat` | `{ instanceId }`           | 心跳；HTTP 404 或 `code=4001` 表示实例不存在，Agent 自动重新注册 |

### 7.4 平台管理接口（管理后台自实现）

| 方法   | 路径                   | 说明                                               |
| ------ | ---------------------- | -------------------------------------------------- |
| GET    | `/instances`           | 实例列表（在线/离线、已绑定/未绑定、最后心跳时间） |
| PUT    | `/instances/{id}/bind` | 绑定（未绑定 → 已绑定）                            |
| DELETE | `/instances/{id}`      | 手动注销                                           |

平台定时任务：`lastHeartbeat` 超过 2×心跳周期 → 标记离线；超过注销阈值（如 24h）→ 自动注销。

### 7.5 部署示例（启用注册）

```bash
docker rm -f node-red-custom
docker run -d --name node-red-custom \
  -p 1890:1890 \
  -e TZ=Asia/Shanghai \
  -e PORT=1890 \
  -e AGENT_API_BASE=http://192.168.1.10:8080 \
  -e AGENT_HEARTBEAT_INTERVAL=60 \
  -v $(pwd)/data:/data \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

验证：`docker logs -f node-red-custom` 出现 `[agent] 注册成功`；平台实例列表出现该实例（在线/未绑定）。

### 7.6 影响与注意事项

- **镜像变更**：自动注册依赖新镜像（agent 已烧入镜像层），老镜像不支持；未传 `AGENT_API_BASE` 时行为与老镜像一致，现有部署命令无需改动。
- **现场网络**：启用注册要求现场容器能出站访问 `AGENT_API_BASE`（HTTP/HTTPS 出站）。
- **手动注销后**：Agent 下一次心跳收到 404/4001 会自动重新注册；若需长期停用，在平台管理后台手动删除该实例即可。
- **docker stop 场景**：SIGTERM 只发给 PID 1（node-red），Agent 主动注销是尽力而为，平台侧最终以心跳超时为准。
- **node-red 编程交互**：本机制只负责实例生命周期管理；如需平台远程下发/查看流程，另走 Node-RED Admin API（配 `adminAuth` Token），两者互不依赖。

---

## 八、注意事项

- 服务器防火墙需放行 1880 端口（`sudo ufw allow 1880`），云服务器另需安全组放行。
- Dashboard 认证为浏览器 Basic 弹窗（非登录页）；其 socket.io 实时推送不受认证保护，公网/严格场景建议加 nginx 反向代理。
- 请妥善保管 `./data/.config.runtime.json`（凭据加密密钥所在，见 4.2），丢失后 `flows_cred.json` 无法解密。
- 端口如需变更，修改 `-p` 映射并保持 `-e PORT` 一致后重启容器（如 `-p 8080:1880`，示例见 2.3.1）。
- **settings.js 加载方式**：本仓库镜像已把 settings.js 烧到 `/usr/src/node-red/settings.js`，由镜像内 CMD 的 `--settings` 指定，与 `/data` 卷无关。`NODE_RED_SETTINGS` 环境变量**并不存在**（官方镜像未定义、node-red 也不识别），不要再用。方式 A 直跑官方镜像时容器内没有这份文件，必须自己挂载并在命令末尾加 `--settings <路径>`。
- **确认配置是否生效**：`docker logs node-red-custom | grep "Settings file"`，应输出 `Settings file : /usr/src/node-red/settings.js`；若 `/` 和 `/dashboard` 都不弹登录页，说明加载的不是这份配置。
