# Node-RED Docker 部署手册

## 一、在线部署（服务器能连外网）

官方镜像只含核心节点，不含 dashboard 等第三方节点。

> **先准备配置文件（在** **`mkdir -p data`** **之前）**：本方式需要 `settings.js` 与 `Dockerfile` 两个文件，按下面的目录结构放好后再执行启动命令。

**部署目录结构**（将仓库 `deploy/` 目录整体拷贝到服务器，作为工作目录）：

```text
deploy/                      # 部署工作目录
├── Dockerfile               # 预装节点定义（方式 A 直跑官方镜像，此文件预留备用）
├── settings/
│   └── settings.js          # 双账户认证配置（必须存在，缺少则挂载失败、认证不生效）
└── data/                    # 数据持久化目录（由下方 mkdir -p data 生成）
```

- `settings.js` 放在 `deploy/settings/settings.js`（对应命令中 `-v $(pwd)/settings/settings.js`）。
- `Dockerfile` 放在工作目录根 `deploy/Dockerfile`。

**settings/settings.js 内容说明**（完整版见仓库 `deploy/settings/settings.js`，要点如下）：

| 配置项                | 作用                                                 |
| ------------------ | -------------------------------------------------- |
| `credentialSecret` | 凭据加密密钥，必须与 `flows_cred.json` 成对备份，丢失后无法解密          |
| `adminAuth`        | 编程页面 `/` 登录认证：`admin` / `3er4#ER$`（完全权限）           |
| `httpNodeAuth`     | 前端页面 `/dashboard` 浏览器 Basic 认证：`user` / `Nts@1234` |
| `uiPort`           | 容器内监听端口，需与 `-p` 端口映射保持一致                           |
| `externalModules`  | `autoInstall: true` 允许编辑器在线安装节点（仅在线场景需要）           |

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

# 2. 拉取并启动官方镜像（挂载数据持久化 + 双账户认证配置）
#    ⚠ 必须显式设置 NODE_RED_SETTINGS=/data/settings.js，否则 5.0 镜像不会加载认证配置
# $(pwd)表示当前目录，即/deploy目录
docker run -d --name node-red -p 1880:1880  -e PORT=1880  -v $(pwd)/data:/data  -v $(pwd)/settings/settings.js:/data/settings.js  -e NODE_RED_SETTINGS=/data/settings.js  -e TZ=Asia/Shanghai  --restart unless-stopped  nodered/node-red:5.0.4

# 3. 验证
docker ps | grep node-red      # 状态应为 Up
docker logs node-red           # 出现 "Server now running"
curl -I http://localhost:1880  # 返回 200
```

浏览器访问 `http://服务器IP:1880`，登录页出现即成功（账户见第三章）。

***

## 二、镜像打包与离线部署（给现场）

### 2.1 打包需要哪些文件

| 文件                   | 作用             | 构建机（打包） | 现场（运行） |
| -------------------- | -------------- | ------- | ------ |
| Dockerfile           | 定义镜像内容（预装哪些节点） | ✅ 需要    | ❌ 不需要  |
| settings/settings.js | 双账户认证配置        | ✅ 验证时用  | ✅ 需要   |
| data/                | 数据持久化目录        | 自动生成    | ✅ 需要   |

### 2.2 构建机（有外网）执行哪些命令

```bash
# 1. 上传 deploy/ 目录，进入后构建镜像
cd deploy
docker build -t node-red-custom:5.0.4 .

# 2. （可选）先验证一次：启动 → 浏览器确认登录页和节点 → 停止
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  -e NODE_RED_SETTINGS=/data/settings.js \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/data/settings.js \
  --restart unless-stopped \
  node-red-custom:5.0.4
docker stop node-red-custom && docker rm node-red-custom

# 3. 导出离线包
docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz
```

**产出物（带到现场）**：`node-red-custom-5.0.4.tar.gz` + 整个 `deploy/` 目录。

### 2.3 现场（离线）执行哪些命令

```bash
# 1. 导入镜像（本地导入，全程不联网）
docker load < node-red-custom-5.0.4.tar.gz

# 2. 进入 deploy/ 目录，准备数据目录与配置（settings.js 必须存在，否则容器启动失败）----直接将整个deploy文件夹上传到服务器的话则不需要这一步
cd deploy
mkdir -p data
ls settings/settings.js        # 确认文件存在，缺失则把 deploy/settings/settings.js 复制到 settings/ 下

# 3. 若已有同名容器先清理
docker rm -f node-red-custom 2>/dev/null || true

# 4. 数据卷权限（若容器反复重启，多为此问题）
sudo chown -R 1000:1000 ./data/

# 5. 启动（挂载数据持久化 + 双账户认证配置）
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  -e NODE_RED_SETTINGS=/data/settings.js \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/data/settings.js \
  --restart unless-stopped \
  node-red-custom:5.0.4

# 6. 验证
docker ps | grep node-red-custom       # 状态应为 Up
docker logs node-red-custom            # 出现 "Server now running"
```

浏览器访问 `http://现场IP:1880`。

> 若容器反复重启，多为数据卷权限问题：`sudo chown -R 1000:1000 ./data/` 后重新启动。

### 2.3.1 仅改外部访问端口

容器内仍是 1880，只改对外映射（示例改为 1890，`-p` 与 `-e PORT` 需保持一致）：

```bash
docker rm -f node-red-custom
docker run -d --name node-red-custom -p 1890:1890  -e TZ=Asia/Shanghai  -e PORT=1890   -e NODE_RED_SETTINGS=/data/settings.js   -v $(pwd)/data:/data   -v $(pwd)/settings/settings.js:/data/settings.js  --restart unless-stopped   node-red-custom:5.0.4
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

***

## 三、双入口认证（部署后登录用）

| 入口   | 地址                         | 账户      | 密码         | 认证方式         | 权限             |
| ---- | -------------------------- | ------- | ---------- | ------------ | -------------- |
| 编程页面 | `http://IP:1880/`          | `admin` | `3er4#ER$` | Node-RED 登录页 | 完全权限（可编辑/部署流程） |
| 前端页面 | `http://IP:1880/dashboard` | `user`  | `Nts@1234` | 浏览器 Basic 弹窗 | 查看 UI 界面       |

***

## 四、数据持久化

- 数据卷映射：`./data:/data`，容器内数据（flows、凭据）均落盘到宿主机 `./data`。
- 预装节点在镜像层，容器重建不影响；数据卷内容（`./data`）需单独备份。
- **备份**：先 `docker stop node-red-custom` 停容器（保证文件写入完整）→ 打包 `./data` 目录 → 再 `docker start node-red-custom`。
- **迁移**：新服务器放置 `./data` 与交付文件后按 2.3 启动。
- **删除容器不丢数据**：`docker rm -f node-red-custom` 只删容器，`./data` 保留。

### 4.1 sqlite 数据库文件路径（务必配置绝对路径）

sqlite 节点不做相对路径解析，db 文件位置取决于你在节点配置 `Database` 里填的路径：

| 填写的路径               | db 文件实际位置                                  | 持久化               |
| ------------------- | ------------------------------------------ | ----------------- |
| `mydb.db`（相对）       | 容器内 `/usr/src/node-red/mydb.db`（工作目录，非数据卷） | ❌ 容器重建即丢失         |
| `./data/mydb.db`    | 容器内 `/usr/src/node-red/data/`              | ❌ 同上              |
| `/data/mydb.db`（绝对） | 容器内 `/data/` → 宿主机 `./data/`               | ✅ 随 `./data` 备份迁移 |

**务必填绝对路径**，例如 `/data/mydb.db`，db 文件才会落在宿主机 `./data` 下并与 flows、凭据一起持久化。现场离线场景如需带已有 db 文件，直接放入 `./data/` 后启动即可。

> **通用原则**：凡是要落盘的业务数据（sqlite、file 节点、context 等），路径统一写 `/data/` 开头的绝对路径；任何相对路径或指向镜像目录（如 `/usr/src/node-red`）的写入，容器重建即丢失。

### 4.2 备份与迁移注意事项

- **先停后备份**：容器运行中直接拷贝 `./data` 可能拷到写入一半的文件（flows.json、sqlite），导致备份损坏。务必按第四章"备份"步骤先 `docker stop node-red-custom`。
- **sqlite WAL 附属文件**：若 db 启用了 WAL 模式（`journal_mode=WAL`），还会生成 `xxx.db-wal`、`xxx.db-shm`，只拷 `.db` 主文件会丢未合并数据。停容器后 WAL 自动合并；确需在线备份可用 sqlite3 的 `.backup` 命令。
- **凭据成对备份**：`flows_cred.json` 依赖 `settings.js` 中的 `credentialSecret` 解密，两者必须**成对**备份/迁移。只拷 `./data` 不拷 `./settings/settings.js`，现场将无法解密凭据。

***

## 五、安装节点（仅在线部署需要）

现场离线场景禁止执行，节点已预装在镜像中。

- 界面安装：登录编程页面 → 右上角菜单 → Manage palette → Install → 搜索安装
- 命令安装：

```bash
docker exec -u node-red -w /data node-red npm install @flowfuse/node-red-dashboard
docker restart node-red
```

> 命令安装的节点写入 `/data/node_modules`（已持久化），重启/重建容器不丢。

***

## 六、常用运维命令

| 操作   | 命令                           |
| ---- | ---------------------------- |
| 查看状态 | `docker ps`                  |
| 查看日志 | `docker logs -f node-red`    |
| 重启   | `docker restart node-red`    |
| 停止   | `docker stop node-red`       |
| 升级镜像 | 备份 `./data` → 重新构建/拉取 → 重新启动 |

***

## 七、注意事项

- 服务器防火墙需放行 1880 端口（`sudo ufw allow 1880`），云服务器另需安全组放行。
- Dashboard 认证为浏览器 Basic 弹窗（非登录页）；其 socket.io 实时推送不受认证保护，公网/严格场景建议加 nginx 反向代理。
- 请妥善保管 `settings.js` 中的 `credentialSecret`，丢失后 `flows_cred.json` 无法解密。
- 端口如需变更，修改 `-p` 映射并保持 `-e PORT` 一致后重启容器（如 `-p 8080:1880`，示例见 2.3.1）。
- **Node-RED 5.0 镜像必须显式设置** **`-e NODE_RED_SETTINGS=/data/settings.js`**，否则挂载的 settings.js 不会被加载，adminAuth / httpNodeAuth 全部失效（表现为 `/` 和 `/dashboard` 都不弹登录页）。docker run 部署必须在命令中手动加上。

