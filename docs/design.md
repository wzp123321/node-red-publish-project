# Node-RED 部署设计文档

| 项目     | 内容                                          |
| -------- | --------------------------------------------- |
| 文档版本 | v1.4                                          |
| 目标版本 | Node-RED 5.0.4（官方最新版，2026-07-30 发布） |
| 部署形态 | 仅 Docker 部署（基于官方最新版镜像）          |
| 部署环境 | Linux（Ubuntu/Debian 系），Docker 引擎        |
| 文档状态 | 待评审                                        |

---

## 1. 项目概述

本项目为 Node-RED 流式编程平台的 **Docker 部署**交付工程：

- 基于官方镜像 `nodered/node-red` 编写 Dockerfile **预装第三方节点**，构建自定义镜像，现场完全离线部署；服务器可联网时亦可直接在线部署（详见第 9 章）。
- 数据卷持久化，可随容器迁移。
- 启用**双入口独立认证**：编程编辑器（`/`）与前端 Dashboard（`/dashboard`）各自独立的账户体系。

## 2. 需求分析

| 编号 | 需求                              | 验收标准                                       |
| ---- | --------------------------------- | ---------------------------------------------- |
| R1   | 官网最新版容器化部署并运行        | `http://ip:1880` 可访问编辑器；版本为 5.0.x    |
| R2   | 封装 Docker 版本并实现数据持久化  | 容器删除重建后 flows/凭据/已装节点不丢失       |
| R3   | 前端页面账户 `user` / `Nts@1234`  | 访问 `/dashboard` 需登录，登录后可查看 UI 界面 |
| R4   | 编程页面账户 `admin` / `3er4#ER$` | 访问 `/` 编辑器需登录，登录后可编辑并部署流程  |

### 2.1 非功能需求

- 服务异常自动重启、宿主重启后自动拉起。
- 数据可备份、可迁移。
- 密码不以明文落盘（bcrypt 哈希存储）。

## 3. 技术选型

| 组件        | 选型                              | 理由                                                                |
| ----------- | --------------------------------- | ------------------------------------------------------------------- |
| Node-RED    | 5.0.4                             | 当前官网最新稳定版                                                  |
| Docker 镜像 | `node-red-custom:5.0.4`（自定义） | 官方基座 + 构建期预装节点，内置 Node.js 24 运行环境，现场离线零安装 |
| 编排        | docker-compose                    | 单服务编排，配置可版本化管理，便于挂载与持久化                      |
| 密码哈希    | bcrypt（cost=10）                 | Node-RED 原生认证机制指定算法                                       |
| 数据持久化  | bind mount（`./data:/data`）      | 容器内用户数据目录 `/data` 映射至宿主机目录，备份即复制             |

> 说明：以官方镜像为基座编写 Dockerfile，**构建期预装第三方节点（锁定版本）**，产出自定义镜像 `node-red-custom`。认证配置与数据仍通过运行期挂载注入，与镜像解耦。现场完全离线，不允许安装节点。

## 4. 总体架构

### 4.1 逻辑视图

```
                    ┌─────────────────────────────┐
   浏览器            │         Linux 宿主机          │
  ┌──────┐          │  ┌───────────────────────┐  │
  │ User │──HTTP───▶│  │  Node-RED 容器 5.0.4   │  │
  │ Admin│  :1880   │  │  ├─ 登录页(/auth/login) │  │
  └──────┘          │  │  └─ 编辑器(Editor)      │  │
                    │  └──────────┬────────────┘  │
                    │             │ 读写          │
                    │  ┌──────────▼────────────┐  │
                    │  │ 数据卷（宿主机 ./data） │  │
                    │  │ flows.json            │  │
                    │  │ flows_cred.json       │  │
                    │  │ 已安装节点            │  │
                    │  └───────────────────────┘  │
                    └─────────────────────────────┘
```

### 4.2 Docker 部署要点

| 要素     | 说明                                                 |
| -------- | ---------------------------------------------------- |
| 端口映射 | 宿主机 `1880` → 容器 `1880`                          |
| 数据卷   | `./data:/data`，容器内一切写入落盘至宿主机           |
| 配置注入 | `./settings/settings.js:/data/settings.js`           |
| 自启策略 | `restart: unless-stopped`，异常退出/宿主重启自动拉起 |
| 时区     | `TZ=Asia/Shanghai`                                   |

### 4.3 镜像构建与离线分发

现场完全离线（不允许安装节点、无外网），镜像在**构建机**（有外网）制作，离线包带到现场导入：

```
构建机（有外网）                   现场（完全离线）
┌────────────────────────┐       ┌────────────────────────┐
│ docker build            │       │ docker load            │
│  官方基座 + 预装节点     │  tar  │ docker compose up -d   │
│ docker save | gzip ─────┼──────▶│  零安装、零联网、开箱即用 │
└────────────────────────┘       └────────────────────────┘
```

要点：

- 构建机：`docker build`（Dockerfile 预装节点）→ `docker save | gzip` 导出离线包。
- 现场：`docker load` 导入 → `docker compose up -d`，无需联网、无需安装任何节点。
- 预装节点固化于镜像层 `/usr/src/node-red/node_modules`，与 `/data` 数据卷互不干扰。
- 节点版本在 Dockerfile 中锁定，保证构建可复现；增删节点需重新构建并分发新镜像。

## 5. 认证与安全设计（核心）

### 5.1 双入口认证架构

Node-RED 原生支持**两套独立的认证体系**，分别保护编程编辑器与前端 Dashboard：

| 入口     | 地址         | 保护对象          | 认证机制       | 表现形态                |
| -------- | ------------ | ----------------- | -------------- | ----------------------- |
| 编程页面 | `/`          | 编辑器 + 管理 API | `adminAuth`    | Node-RED 登录页（表单） |
| 前端页面 | `/dashboard` | Dashboard UI      | `httpNodeAuth` | 浏览器 HTTP Basic 弹窗  |

```
浏览器访问
    │
    ├── http://ip:1880/          ──▶ adminAuth 登录页 ──▶ admin/3er4#ER$ ──▶ 编程编辑器
    │
    └── http://ip:1880/dashboard ──▶ Basic 弹窗认证  ──▶ user/Nts@1234 ──▶ 前端 UI
```

### 5.2 账户设计

| 账户    | 密码       | 认证机制       | 适用范围               | 角色定位                |
| ------- | ---------- | -------------- | ---------------------- | ----------------------- |
| `admin` | `3er4#ER$` | `adminAuth`    | 编辑器 `/`             | 编程/管理角色，完全权限 |
| `user`  | `Nts@1234` | `httpNodeAuth` | Dashboard `/dashboard` | 前端查看角色，仅访问 UI |

配置要点：

- `adminAuth.type = "credentials"`，`admin` 用户权限 `*`（完全访问，可编辑部署流程）。
- `httpNodeAuth` 只支持**单个用户**，恰与需求（仅 `user`）吻合；`pass` 为 bcrypt 哈希。
- 两套认证互不影响：`admin` 无法直接访问 dashboard 管理逻辑（dashboard 只读前端），`user` 无法进入编辑器。

### 5.3 已知限制与说明

| 限制       | 说明                                                                                         |
| ---------- | -------------------------------------------------------------------------------------------- |
| Basic 弹窗 | dashboard 认证是浏览器原生弹窗，非美化登录页                                                 |
| 实时通道   | Dashboard 2.0 的 socket.io 实时推送不受 `httpNodeAuth` 保护（严格场景需反向代理 + 认证插件） |
| 单用户     | `httpNodeAuth` 仅支持一个账户                                                                |

### 5.4 安全措施

| 措施       | 说明                                                                   |
| ---------- | ---------------------------------------------------------------------- |
| 密码哈希   | 使用 bcrypt（cost=10）单向哈希，配置文件不存明文                       |
| 凭据加密   | 设置 `credentialSecret`，对 `flows_cred.json` 加密存储，防丢失不可恢复 |
| Token 过期 | editor 会话默认 7 天，可由 `sessionExpiryTime` 调整                    |
| 端口限制   | 默认仅 1880，建议防火墙限制来源 IP（部署文档说明）                     |

### 5.5 密码修改机制

配置文件（settings.js）中仅存 bcrypt 哈希、无明文，修改密码即**替换哈希**，流程如下：

1. **生成新密码的 bcrypt 哈希**（任选一种）：

   ```bash
   # 方式一：本机（有 Node 环境）
   node -e "console.log(require('bcryptjs').hashSync('新密码', 10))"

   # 方式二：官方推荐（本机或服务器）
   npx node-red admin hash-pw

   # 方式三：现场容器内（免装依赖）
   docker exec -it node-red node-red admin hash-pw
   ```

2. **替换 [settings.js](../deploy/settings/settings.js) 对应位置**（宿主机直接编辑，settings.js 为挂载文件无需进容器）：

   | 账户  | 位置                          |
   | ----- | ----------------------------- |
   | admin | `adminAuth.users[0].password` |
   | user  | `httpNodeAuth.pass`           |

3. **重启生效**：`docker compose restart`，浏览器重新登录验证。

## 6. 数据持久化设计

### 6.1 持久化数据范围

| 数据       | 文件                                       | 说明                                     |
| ---------- | ------------------------------------------ | ---------------------------------------- |
| 流程定义   | `flows.json`                               | 核心业务数据                             |
| 节点凭据   | `flows_cred.json`                          | 加密存储，依赖 credentialSecret          |
| 自定义配置 | `settings.js`                              | 本项目统一由外部注入                     |
| 已安装节点 | `/usr/src/node-red/node_modules`（镜像层） | 第三方节点包，构建期预装固化，随镜像分发 |
| 业务数据库 | `*.db`                                     | sqlite 业务数据文件（见 6.3）            |
| 静态资源   | `lib/` 等                                  | 用户自定义静态文件                       |

### 6.2 Docker 持久化方案

- 数据卷映射：`./data:/data`（bind mount），容器内一切写入均落盘至宿主机。
- 配置注入：`./settings/settings.js:/data/settings.js`，认证配置与镜像解耦，可版本化、可更新。
- 备份方式：直接备份宿主机 `./data` 目录即可完成全量备份与迁移。
- 迁移方式：新服务器上放置 `./data` 与交付文件后 `docker compose up -d` 即可。

### 6.3 sqlite 业务数据持久化

Node-RED 可通过 `node-red-node-sqlite` 节点接入 **sqlite 文件型数据库**存储业务数据（如设备采集数据、历史记录等）。该节点为第三方节点，需手动安装，其持久化机制如下：

| 对象              | 存储位置                                    | 持久化                                  |
| ----------------- | ------------------------------------------- | --------------------------------------- |
| sqlite 节点程序   | `/usr/src/node-red/node_modules/`（镜像层） | ✅ 固化于镜像，随镜像分发               |
| 数据库文件（.db） | 默认落于 `$HOME`（容器内）                  | ✅ 随数据卷持久化（需满足下方路径规则） |

**关键规则：**

- 官方镜像中容器用户 `HOME=/data`，sqlite 节点**默认将数据库文件存放在用户主目录**。因此配置 sqlite 节点时**仅填文件名**（如 `mydb.db`），文件即自动落在 `/data/mydb.db`，随数据卷持久化。
- 亦可显式填写绝对路径 `/data/xxx.db`，效果相同。
- 避坑：
  - 不要使用 `:memory:`（内存库，不持久化）；
  - 不要使用 `~` 开头路径（节点不支持）；
  - 不要将绝对路径指向 `/data` 之外（如 `/tmp/`、容器内其他目录），否则容器重建后数据丢失。

**部署说明（现场离线）：**

- 现场不允许安装节点，sqlite 节点需在**构建期预装入镜像**：在 `Dockerfile` 中取消 `node-red-node-sqlite` 安装行注释并锁定版本，重新构建镜像后离线分发。
- 该节点含原生编译模块（sqlite3），**编译在构建机完成**，现场零编译。
- 数据库文件（`.db`）仍存放于 `/data` 数据卷，随卷持久化。

**数据迁移：**

- 旧环境（如既有项目）的 `.db` 文件直接复制至新服务器 `./data/` 目录，sqlite 节点指向同名文件即可读取旧数据。
- 备份时 `*.db` 文件必须包含在内（业务数据核心），`node_modules/` 体积大可选备份。

## 7. 目录结构设计

```
node-red/
├── docs/
│   └── design.md            # 本设计文档
└── deploy/                  # 部署交付物（整体上传至目标服务器）
    ├── Dockerfile           # 自定义镜像构建（官方基座 + 预装节点）
    ├── settings/
    │   └── settings.js      # 认证与运行时配置
    ├── data/                # 数据持久化目录（flows/凭据/sqlite 库）
    ├── docker-compose.yml   # Docker 编排文件
    └── README.md            # 部署操作说明
```

## 8. 关键配置设计

### 8.1 settings.js 核心片段

```js
module.exports = {
  flowFile: "flows.json",
  credentialSecret: "<随机生成的凭据加密密钥>",
  // 编程页面（/）认证：admin 账户，完全权限
  adminAuth: {
    type: "credentials",
    users: [{ username: "admin", password: "$2a$10$...", permissions: "*" }],
  },
  // 前端页面（/dashboard）认证：user 账户，HTTP Basic
  httpNodeAuth: {
    user: "user",
    pass: "$2a$10$...",
  },
  uiPort: 1880,
};
```

### 8.2 docker-compose.yml

```yaml
services:
  node-red:
    image: node-red-custom:5.0.4
    container_name: node-red
    restart: unless-stopped
    environment:
      - TZ=Asia/Shanghai
    ports:
      - "1880:1880"
    volumes:
      - ./data:/data
      - ./settings/settings.js:/data/settings.js
```

### 8.3 Dockerfile 核心片段

```dockerfile
FROM nodered/node-red:5.0.4
USER node-red
# 预装第三方节点（锁定版本，按需增删）
RUN npm install --no-audit --no-fund @flowfuse/node-red-dashboard@1.30.2
# RUN npm install --no-audit --no-fund node-red-node-sqlite@1.0.3
```

## 9. 部署流程设计

按部署场景分两类（与部署手册 README 章节一致）：
**场景一：在线部署**（服务器能连外网，直接部署 node-red）与 **场景二：镜像打包 + 离线部署**（给无法联网的现场）。

### 9.1 场景一：在线部署（服务器能连外网）

**方式 A：直接部署官方镜像**（最简，无预装节点，适合临时/测试或后续手动装节点）

```bash
docker run -d --name node-red \
  -p 1880:1880 \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/data/settings.js \
  -e TZ=Asia/Shanghai \
  --restart unless-stopped \
  nodered/node-red:5.0.4
```

- 官方镜像只含核心节点，dashboard 等第三方节点需手动安装（见部署手册第五章）。
- 认证配置通过挂载 settings.js 注入，双账户认证依然生效。

**方式 B：部署含预装节点的自定义镜像**（适合这台服务器本身就是长期运行目标）

```bash
cd deploy
docker compose up -d --build
```

- `--build` 按 Dockerfile 现场构建（预装 dashboard），首次需联网拉基础镜像与 npm 包。

### 9.2 场景二：镜像打包与离线部署（给现场）

#### 9.2.1 打包所需文件

| 文件                 | 作用                         | 构建机（打包） | 现场（运行） |
| -------------------- | ---------------------------- | -------------- | ------------ |
| Dockerfile           | 定义镜像内容（预装哪些节点） | ✅ 需要        | ❌ 不需要    |
| docker-compose.yml   | 启动编排（端口/挂载）        | ✅ 验证时用    | ✅ 需要      |
| settings/settings.js | 双账户认证配置               | ✅ 验证时用    | ✅ 需要      |
| data/                | 数据持久化目录               | 自动生成       | ✅ 需要      |

#### 9.2.2 构建机（有外网）操作流程

1. 上传 `deploy/` 目录（含 Dockerfile）至服务器 A，进入 `deploy/` 目录。
2. `docker build -t node-red-custom:5.0.4 .` 构建镜像（预装节点固化进镜像层）。
3. **（推荐）先在本机验证**：`docker compose up -d` → 浏览器确认登录页、Dashboard 节点可用 → `docker compose down` 停止。
4. `docker save node-red-custom:5.0.4 | gzip > node-red-5.0.4.tar.gz` 导出离线包。
5. **产出物**：`node-red-5.0.4.tar.gz` + 整个 `deploy/` 目录，一同带到现场。

#### 9.2.3 现场（离线）操作流程

1. 上传离线包与 `deploy/` 目录至服务器，验证 `docker compose version` 可用，确认 1880 端口未被占用。
2. `docker load -i node-red-5.0.4.tar.gz` 导入镜像（本地导入，全程无需联网）。
3. 在 `deploy/` 目录执行 `docker compose up -d` 拉起服务（使用本地镜像）。
4. `docker compose ps` 确认容器 Up，`docker logs node-red` 确认无报错。
5. 放行 1880 端口（`ufw allow 1880`，云服务器另需安全组放行）。
6. 浏览器访问 `http://服务器IP:1880`，出现登录页即部署成功。

#### 9.2.4 现场需要新增节点

现场禁止安装节点。在**构建机**改 Dockerfile 追加 `RUN npm install <包名>@<版本>` → 重新 `docker build` + `docker save` → 新包带到现场 `docker load` → 备份 `./data` → `docker compose up -d`。

> 场景一/场景二的区别仅在于**镜像来源**：在线由服务器现场构建（或 docker run 官方镜像），离线由构建机 build/save 后 `docker load`。两套流程共用同一份 docker-compose.yml 与 settings.js。

## 10. 运维与备份

| 场景     | 命令/操作                                                                    |
| -------- | ---------------------------------------------------------------------------- |
| 查看状态 | `docker compose ps`                                                          |
| 查看日志 | `docker logs node-red`                                                       |
| 重启     | `docker compose restart`                                                     |
| 停止     | `docker compose down`（数据保留在 ./data）                                   |
| 升级     | 构建机重新构建导出 → 现场 `docker load` → 备份 data → `docker compose up -d` |
| 备份     | 归档 `./data` 目录（含 flows、凭据、sqlite 库；节点已在镜像中，无需备份）    |
| 还原     | 解压至 `./data` 后 `docker compose up -d`                                    |

## 11. 验证方案

1. **可用性**：`curl -I http://localhost:1880` 返回 200。
2. **认证**：
   - 访问 `/` → 登录页，`admin`/`3er4#ER$` 登录 → 完整编辑器，可部署。
   - 访问 `/dashboard` → Basic 弹窗，`user`/`Nts@1234` 登录 → 前端 UI 界面。
   - 错误密码 → 登录被拒。
3. **持久化**：`docker compose down && up -d` 后 flows 仍存在。
4. **预装节点**：登录编辑器，节点面板中可见 `ui-gauge`、`ui-chart` 等 Dashboard 节点（无需安装）。
5. **自启**：宿主重启后容器自动拉起（`restart: unless-stopped`）。

## 12. 风险与注意事项

| 风险                    | 应对                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------ |
| 密码泄露风险            | bcrypt 哈希存储；建议上线后按需修改密码并重新生成哈希                                |
| Dashboard 实时通道      | Basic 认证不保护 socket.io 通道；严格场景需反向代理 + 认证插件                       |
| 公开网络暴露            | 建议置于内网/加反向代理与 HTTPS（后续扩展项）                                        |
| Node-RED 5.0 破坏性变更 | 升级前备份数据，确认第三方节点兼容 5.0                                               |
| credentialSecret 丢失   | 妥善保管；丢失将无法解密 flows_cred.json                                             |
| 数据卷权限              | 挂载目录需对容器用户（node-red，UID 1000）可读写                                     |
| sqlite 路径配置错误     | 数据库文件必须位于 `/data` 内；避免 `:memory:` 与 `~` 路径                           |
| sqlite 原生编译失败     | 编译在构建期完成；构建机需具备编译工具链，失败时构建机补装 `python3 make g++` 后重建 |
| 现场缺少 compose 插件   | 现场一般已具备 Docker；若 `docker compose` 不可用需提前离线补装插件                  |

---
