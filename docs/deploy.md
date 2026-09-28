# Node-RED 镜像构建与部署手册

> 覆盖：打包文件说明 → 构建镜像 → 现场部署 → 登录认证 → 自动注册 → 数据持久化与备份 → 常用运维命令 → 注意事项 → 常见问题。
> 相关目录：`deploy/`（Dockerfile、settings、agent、入口脚本）。

## 一、打包文件说明

```text
deploy/
├── Dockerfile              # 镜像定义：预装节点 + 烧入 settings.js/agent + 覆盖入口
├── settings/
│   └── settings.js         # 双入口认证配置（烧到 /usr/src/node-red/settings.js）
├── agent/
│   └── main.js             # 自动注册 agent（零依赖，仅用 Node 内置 http/https）
└── agent-entrypoint.sh     # 容器启动入口：后台拉起 agent，再 exec 官方 entrypoint.sh
```

| 文件                 | 作用                                                                              | 镜像内落地位置                          | 生效时点                                  |
| -------------------- | --------------------------------------------------------------------------------- | --------------------------------------- | ----------------------------------------- |
| Dockerfile           | 定义镜像：`npm install` 预装节点，COPY 下列 3 个文件，覆盖 ENTRYPOINT/CMD         | 不进镜像（构建脚本）                    | 构建期                                    |
| settings/settings.js | 双入口认证（`adminAuth` / `httpNodeAuth`）、`uiPort = process.env.PORT \|\| 1880` | `/usr/src/node-red/settings.js`         | 容器启动，由镜像 CMD 的 `--settings` 加载 |
| agent/main.js        | 启动时注册 + 定时心跳，向平台上报实例状态                                         | `/usr/src/node-red/agent/main.js`       | 容器启动，由入口脚本后台拉起              |
| agent-entrypoint.sh  | 先拉起 agent，再 `exec` 官方 entrypoint.sh 启动 node-red                          | `/usr/src/node-red/agent-entrypoint.sh` | 容器启动（ENTRYPOINT）                    |

- 后 3 个文件都**烧进镜像层**，现场不需要携带，也**改了必须重新构建镜像**才生效（见第八章）。
- `deploy/Dockerfile` 本身不进镜像，只是构建脚本，改预装节点改它。
- `data/` 不属于打包文件，是宿主机上的运行时数据目录（现场生成）。

**容器启动链路**：

```text
agent-entrypoint.sh（后台拉起 agent）→ 官方 /usr/src/node-red/entrypoint.sh
→ node node_modules/node-red/red.js --userDir /data flows.json --settings /usr/src/node-red/settings.js
```

**settings.js 要点**（完整见 [settings.js](../deploy/settings/settings.js)）：

| 配置项             | 作用                                                                                                            |
| ------------------ | --------------------------------------------------------------------------------------------------------------- |
| `credentialSecret` | 保持注释（未显式设置），由 node-red 首次启动自动生成密钥并存入 `/data/.config.runtime.json`，备份 `./data` 即可 |
| `adminAuth`        | 编程页面 `/` 登录认证：`admin` / `3er4#ER$`（完全权限）                                                         |
| `httpNodeAuth`     | 前端页面 `/dashboard` 浏览器 Basic 认证：`user` / `Nts@1234`                                                    |
| `uiPort`           | `process.env.PORT \|\| 1880`，端口由启动命令 `-e PORT` 指定，需与 `-p` 映射一致                                 |
| `externalModules`  | `autoInstall: true`（仅在线场景需要）                                                                           |

> settings.js 刻意烧到 `/data` 之外：`/data` 会被 `-v $(pwd)/data:/data` 挂载遮蔽，且 node-red 在 `/data` 找不到 settings.js 时会往 `/data` 复制默认配置，宿主机目录属主非 1000 时直接 EACCES、容器反复重启。改用 `--settings` 绝对路径加载后与数据卷完全解耦。

## 二、构建镜像（构建机，需外网）

```bash
cd deploy
docker build -t node-red-custom:5.0.4 .

# 可选：本机先验证一次（浏览器确认登录页与节点），随后删除
docker run -d --name node-red-test -p 1880:1880 -e PORT=1880 -e TZ=Asia/Shanghai \
  -v $(pwd)/data-test:/data --restart unless-stopped node-red-custom:5.0.4
docker rm -f node-red-test

# 导出离线包
docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz
```

**镜像内预装节点**（Dockerfile 内一次 `npm install`，版本锁定）：

| 节点包                       | 版本   |
| ---------------------------- | ------ |
| @flowfuse/node-red-dashboard | 1.30.2 |
| node-red-node-sqlite         | 2.0.1  |
| node-red-contrib-modbus      | 5.60.1 |
| node-red-contrib-s7          | 3.1.3  |

**新增/升级节点**：在 Dockerfile 的 `npm install` 参数列表中追加 `包名@版本`（版本用 `npm view <包名> version` 查询后锁定），重新 `docker build` → `docker save`。现场禁止安装节点。

**产出物**：`node-red-custom-5.0.4.tar.gz`。

## 三、现场部署（离线）

```bash
# 1. 导入镜像
docker load -i node-red-custom-5.0.4.tar.gz

# 2. 准备数据目录并授权（权限不对容器会反复重启）
mkdir -p data && sudo chown -R 1000:1000 ./data/

# 3. 清理同名容器
docker rm -f node-red-custom 2>/dev/null || true

# 4. 启动
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  --add-host nginxIn.tshlms.com:192.168.50.177 \
  --add-host nginxin.tshlms.com:192.168.50.177 \
  -v $(pwd)/data:/data \
  --restart unless-stopped \
  node-red-custom:5.0.4

# 5. 验证
docker ps | grep node-red-custom                     # 状态 Up
docker logs node-red-custom | grep "Settings file"   # /usr/src/node-red/settings.js
```

**启动命令字段说明**

| 字段                                     | 作用                                                                      |
| ---------------------------------------- | ------------------------------------------------------------------------- |
| `--name node-red-custom`                 | 容器名；多实例时每个实例取不同名字                                        |
| `-p 1880:1880`                           | 端口映射「宿主机:容器」；改动后必须与 `-e PORT` 一致（如 `-p 1890:1890`） |
| `-e TZ=Asia/Shanghai`                    | 容器时区（影响日志时间戳）                                                |
| `-e PORT=1880`                           | node-red 监听端口，同时决定实例 ID `Node-RED-${PORT}`，需与 `-p` 一致     |
| `--add-host nginxIn.tshlms.com:<内网IP>` | 域名映射，**实例注册必须**；大小写两个域名都要加（见下）                  |
| `-v $(pwd)/data:/data`                   | 数据卷，flows/凭据/密钥持久化（每实例一个独立目录）                       |
| `--restart unless-stopped`               | 随 Docker 自启、异常退出自动重启                                          |
| `node-red-custom:5.0.4`                  | 镜像名:标签                                                               |

**多实例（一台机器跑多个 node-red）**：每个实例用独立的容器名、端口、数据目录，目录都要读写权限；不能共用（flows.json 会互相覆盖）。

```bash
mkdir -p data-1880 data-1888 && sudo chown -R 1000:1000 data-1880 data-1888
docker run -d --name node-red-1880 -p 1880:1880 -e TZ=Asia/Shanghai -e PORT=1880 \
  --add-host nginxIn.tshlms.com:192.168.50.177 \
  --add-host nginxin.tshlms.com:192.168.50.177 \
  -v $(pwd)/data-1880:/data --restart unless-stopped node-red-custom:5.0.4
docker run -d --name node-red-1888 -p 1888:1888 -e TZ=Asia/Shanghai -e PORT=1888 \
  --add-host nginxIn.tshlms.com:192.168.50.177 \
  --add-host nginxin.tshlms.com:192.168.50.177 \
  -v $(pwd)/data-1888:/data --restart unless-stopped node-red-custom:5.0.4
```

**域名是实例注册必须的**：agent 的注册地址是域名（默认 `http://nginxIn.tshlms.com:10130/energy/tenant-api`），容器必须能解析该域名，否则注册失败、平台看不到实例；flows 里的内网域名同理。故启动命令用 `--add-host <域名>:<内网IP>` 指定：

- **大小写两个域名都要加**：`--add-host` 是往容器 `/etc/hosts` 写静态映射，Linux 解析时大小写敏感，只加 `nginxIn` 或只加 `nginxin`（flows 里写另一种大小写时）都会解析失败，所以两条都写：

```bash
--add-host nginxIn.tshlms.com:<内网IP> --add-host nginxin.tshlms.com:<内网IP>
```

- 示例 IP 仅作参考，务必换成现场实际内网地址；不加会报 `ENOTFOUND`。

> `$(pwd)` 为当前目录；Windows PowerShell 用 `${PWD}`。

## 四、登录认证

| 入口     | 地址                       | 账户    | 密码       | 认证方式          | 权限                        |
| -------- | -------------------------- | ------- | ---------- | ----------------- | --------------------------- |
| 编程页面 | `http://IP:1880/`          | `admin` | `3er4#ER$` | Node-RED 登录页   | 完全权限（可编辑/部署流程） |
| 前端页面 | `http://IP:1880/dashboard` | `user`  | `Nts@1234` | 浏览器 Basic 弹窗 | 查看 UI 界面                |

## 五、自动注册

容器启动即拉起 agent 并注册，**没有开关**：未传 `AGENT_API_BASE` 时使用代码内置默认地址 `http://nginxIn.tshlms.com:10130/energy/tenant-api`（末尾斜杠自动去除）。

| 环境变量                   | 必填 | 默认值                                                   | 说明                                                                         |
| -------------------------- | ---- | -------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `AGENT_API_BASE`           | 否   | 内置 `http://nginxIn.tshlms.com:10130/energy/tenant-api` | 平台 API 基础地址；经 nginx 代理时含代理前缀，如 `http://平台IP:8899/zp-api` |
| `AGENT_HEARTBEAT_INTERVAL` | 否   | `60`（秒）                                               | 心跳间隔，传 `0`/空/非数字走默认，最小 1 秒                                  |
| `PORT`                     | 否   | `1880`                                                   | Node-RED 监听端口，同时决定实例 ID `Node-RED-${PORT}`，需与 `-p` 一致        |

**Agent 行为**

1. 启动即 `POST {AGENT_API_BASE}/tenant/nodeRed/register`，请求体 `{ instanceId, ip, port }`。
2. 响应 `success` 为真 → 注册成功进入心跳；平台返回 `errmsg = "相同IP和端口的实例已存在"` 同样视为成功。
3. 其他情况 → 打印 `注册失败 {...}` 后 agent 退出（`不进入心跳轮询`），**不重试**，需重启容器再注册；不影响 node-red 本身运行。
4. 注册成功后每 `AGENT_HEARTBEAT_INTERVAL` 秒 `POST {AGENT_API_BASE}/tenant/nodeRed/heartbeat`，请求体 `{ instanceId }`；失败只告警，不触发重新注册。
5. Agent **不主动注销**；实例离线/注销由平台按心跳超时判定（超 2×心跳周期置离线，超阈值如 24h 自动注销）。
6. 容器重建后重新注册（幂等，实例 ID 不变）即恢复在线。

**启动示例**

```bash
# 使用指定平台地址 + 自定义心跳间隔（100 秒）
docker run -d --name node-red-custom \
  -p 1888:1888 \
  -e TZ=Asia/Shanghai \
  -e PORT=1888 \
  -e AGENT_API_BASE=http://192.168.41.198:8200 \
  -e AGENT_HEARTBEAT_INTERVAL=100 \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

**日志与排查**：`docker logs -f node-red-custom | grep agent`

| 现象/日志                             | 说明与处理                                                                                           |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `启动自动注册 agent` / `自动注册启动` | 入口脚本已拉起 agent，含 instanceId、心跳间隔、API 地址                                              |
| `注册成功 {...}`                      | 注册成功，平台出现该实例（在线/未绑定）                                                              |
| `注册失败 {...}` + `agent 退出`       | 按 status 排查：`0`（ECONNREFUSED/ETIMEDOUT）出站不通；`404` 代理前缀写错；`401/403` 平台白名单/鉴权 |
| `心跳通道确认正常（耗时 xx ms）`      | 仅首次成功心跳打印，之后成功心跳静默（看不到 ≠ 没跳，去平台侧确认）                                  |
| `心跳失败 {...}`                      | 心跳异常，仅记录日志，等待容器重启后重新注册                                                         |

> 容器内无 curl，可用 node 验证连通性：`docker exec node-red-custom node -e "fetch('http://平台IP:端口/代理前缀/instances').then(r=>r.text()).then(console.log)"`

## 六、数据持久化与备份

- 数据卷：`./data:/data`，flows、凭据、密钥均落盘到宿主机 `./data`；`docker rm -f` 只删容器，数据不丢。
- **路径一律写 `/data/` 绝对路径**：sqlite、file 节点、context 等；相对路径或指向 `/usr/src/node-red` 的写入，容器重建即丢失。
- **备份**：`docker stop node-red-custom` → 打包 `./data`（含隐藏文件）→ `docker start node-red-custom`。运行中直接拷贝可能拷到写入一半的文件。
- **sqlite WAL**：启用 WAL 时会生成 `xxx.db-wal`、`xxx.db-shm`，只拷主文件会丢未合并数据；停容器后 WAL 自动合并。
- **凭据密钥**：`credentialSecret` 未显式设置，密钥自动生成在 `./data/.config.runtime.json`，因此备份 `./data` 即可；若将来显式配置 `credentialSecret`，新镜像必须沿用同一值，否则旧 `flows_cred.json` 无法解密。

## 七、常用运维命令

| 操作     | 命令                                          |
| -------- | --------------------------------------------- |
| 查看状态 | `docker ps`                                   |
| 查看日志 | `docker logs -f node-red-custom`              |
| 重启     | `docker restart node-red-custom`              |
| 停止     | `docker stop node-red-custom`                 |
| 升级镜像 | 备份 `./data` → `docker load` 新包 → 重新启动 |

## 八、注意事项

- **改配置必须重打镜像**：settings.js、agent、预装节点都在镜像层，现场不能装节点、不能只改宿主机文件；流程为「构建机改 → build → save → 现场 load → `docker rm -f` → 按第三章启动（`./data` 不动）」。
- settings.js 由镜像 CMD 的 `--settings` 加载，`/data/settings.js` 不参与加载（老现场遗留的可删除）；`NODE_RED_SETTINGS` 环境变量并不存在，不要使用。
- 数据卷权限：容器反复重启多为 `./data` 属主问题，`sudo chown -R 1000:1000 ./data/`；不想每次 chown 可改用命名卷 `-v node-red-data:/data`（代价是备份/迁移改走 `docker cp`）。
- 端口需放行：服务器防火墙（`sudo ufw allow 1880`）与云安全组。
- Dashboard 为浏览器 Basic 弹窗（非登录页），其 socket.io 实时推送不受认证保护，公网/严格场景建议加 nginx 反向代理。
- 启用注册要求容器能出站访问 `AGENT_API_BASE`（HTTP/HTTPS）。
- 请妥善保管 `./data/.config.runtime.json`，丢失后 `flows_cred.json` 无法解密。

## 九、常见问题及解决办法

| 现象                                                                       | 原因                                   | 解决办法                                                                                                                                                                                             |
| -------------------------------------------------------------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Error: EACCES: permission denied, mkdir '/data/node_modules'`             | 数据卷目录没有读写权限                 | 给数据目录授权后重启容器：`sudo chown -R 1000:1000 ./data/`（属主须为容器内 node-red 用户 UID 1000；多实例逐个目录授权）                                                                             |
| 注册失败 `{"status":0,"error":"getaddrinfo ENOTFOUND nginxin.tshlms.com"}` | 启动时未配置域名映射，容器解析不了域名 | 启动命令补上 `--add-host`（大小写两条域名都要加），重建容器：`docker rm -f node-red-custom` 后按第三章重新启动，确保 `--add-host nginxIn.tshlms.com:<内网IP> --add-host nginxin.tshlms.com:<内网IP>` |

> 说明：`EACCES ... mkdir '/data/node_modules'` 出现在编辑器安装节点或启动加载节点时，本质都是 `/data` 卷不具备写权限；注册失败的 `ENOTFOUND` 同理，容器无法解析域名（含 agent 的注册地址与 flows 里的内网域名）都会报此错。

## 附：直跑官方镜像（仅在线，无预装节点、无自动注册）

```bash
mkdir -p data
docker run -d --name node-red -p 1880:1880 -e PORT=1880 -e TZ=Asia/Shanghai \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/usr/src/node-red/settings.js \
  --restart unless-stopped \
  nodered/node-red:5.0.4 --settings /usr/src/node-red/settings.js
```

官方镜像不含本仓库的 settings.js 与 agent，必须自行挂载配置并追加 `--settings` 参数；需要 dashboard 等节点或自动注册时改用自定义镜像。
