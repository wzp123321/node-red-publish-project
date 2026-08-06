# Node-RED 部署与迁移手册

> 本手册由《部署手册》与《数据迁移手册》合并而成。**第一部分：部署**（在线/离线部署、双入口认证、数据持久化、运维）；**第二部分：迁移**（3.1.9 容器 → 5.0.4 自定义镜像）。

---

## 第一部分 部署

### 一、在线部署（服务器能连外网）

官方镜像只含核心节点，不含 dashboard 等第三方节点。

> **先准备配置文件（在 `mkdir -p data` 之前）**：本方式需要 `settings.js` 与 `Dockerfile` 两个文件，按下面的目录结构放好后再执行启动命令。

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

| 配置项             | 作用                                                            |
| ------------------ | --------------------------------------------------------------- |
| `credentialSecret` | 凭据加密密钥，必须与 `flows_cred.json` 成对备份，丢失后无法解密 |
| `adminAuth`        | 编程页面 `/` 登录认证：`admin` / `3er4#ER$`（完全权限）         |
| `httpNodeAuth`     | 前端页面 `/dashboard` 浏览器 Basic 认证：`user` / `Nts@1234`    |
| `uiPort`           | 容器内监听端口，需与 `-p` 端口映射保持一致                      |
| `externalModules`  | `autoInstall: true` 允许编辑器在线安装节点（仅在线场景需要）    |

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

---

### 二、镜像打包与离线部署（给现场）

#### 2.1 打包需要哪些文件

| 文件                 | 作用                         | 构建机（打包） | 现场（运行） |
| -------------------- | ---------------------------- | -------------- | ------------ |
| Dockerfile           | 定义镜像内容（预装哪些节点） | ✅ 需要        | ❌ 不需要    |
| settings/settings.js | 双账户认证配置               | ✅ 验证时用    | ✅ 需要      |
| data/                | 数据持久化目录               | 自动生成       | ✅ 需要      |

#### 2.2 构建机（有外网）执行哪些命令

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

#### 2.3 现场（离线）执行哪些命令

```bash
# 1. 导入镜像（本地导入，全程不联网）
docker load < node-red-custom-5.0.4.tar.gz

# 2. 进入 deploy/ 目录，准备数据目录与配置（settings.js 必须存在，否则容器启动失败）
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

#### 2.3.1 仅改外部访问端口

容器内仍是 1880，只改对外映射（示例改为 1890，`-p` 与 `-e PORT` 需保持一致）：

```bash
docker rm -f node-red-custom
docker run -d --name node-red-custom -p 1890:1890  -e TZ=Asia/Shanghai  -e PORT=1890   -e NODE_RED_SETTINGS=/data/settings.js   -v $(pwd)/data:/data   -v $(pwd)/settings/settings.js:/data/settings.js  --restart unless-stopped   node-red-custom:5.0.4
```

> Windows PowerShell 把 `$(pwd)` 换成 `${PWD}`；`bash` 脚本同理可用。

#### 2.4 现场需要新增节点怎么办

现场禁止安装节点。需在**构建机**上改 Dockerfile 加一行，重新打包：

```bash
# 构建机：在 Dockerfile 追加（锁定版本，构建前用 npm view <包名> version 查最新版）
#   RUN npm install --no-audit --no-fund <包名>@<版本>
docker build -t node-red-custom:5.0.4 .
docker save node-red-custom:5.0.4 | gzip > node-red-custom-5.0.4.tar.gz
```

将新包带到现场：`docker load -i` → 备份 `./data` → 按 2.3 重新启动。

---

### 三、双入口认证（部署后登录用）

| 入口     | 地址                       | 账户    | 密码       | 认证方式          | 权限                        |
| -------- | -------------------------- | ------- | ---------- | ----------------- | --------------------------- |
| 编程页面 | `http://IP:1880/`          | `admin` | `3er4#ER$` | Node-RED 登录页   | 完全权限（可编辑/部署流程） |
| 前端页面 | `http://IP:1880/dashboard` | `user`  | `Nts@1234` | 浏览器 Basic 弹窗 | 查看 UI 界面                |

---

### 四、数据持久化

- 数据卷映射：`./data:/data`，容器内数据（flows、凭据）均落盘到宿主机 `./data`。
- 预装节点在镜像层，容器重建不影响；数据卷内容（`./data`）需单独备份。
- **备份**：先 `docker stop node-red-custom` 停容器（保证文件写入完整）→ 打包 `./data` 目录 → 再 `docker start node-red-custom`。
- **迁移**：新服务器放置 `./data` 与交付文件后按 2.3 启动。
- **删除容器不丢数据**：`docker rm -f node-red-custom` 只删容器，`./data` 保留。

#### 4.1 sqlite 数据库文件路径（务必配置绝对路径）

sqlite 节点不做相对路径解析，db 文件位置取决于你在节点配置 `Database` 里填的路径：

| 填写的路径              | db 文件实际位置                                          | 持久化                  |
| ----------------------- | -------------------------------------------------------- | ----------------------- |
| `mydb.db`（相对）       | 容器内 `/usr/src/node-red/mydb.db`（工作目录，非数据卷） | ❌ 容器重建即丢失       |
| `./data/mydb.db`        | 容器内 `/usr/src/node-red/data/`                         | ❌ 同上                 |
| `/data/mydb.db`（绝对） | 容器内 `/data/` → 宿主机 `./data/`                       | ✅ 随 `./data` 备份迁移 |

**务必填绝对路径**，例如 `/data/mydb.db`，db 文件才会落在宿主机 `./data` 下并与 flows、凭据一起持久化。现场离线场景如需带已有 db 文件，直接放入 `./data/` 后启动即可。

> **通用原则**：凡是要落盘的业务数据（sqlite、file 节点、context 等），路径统一写 `/data/` 开头的绝对路径；任何相对路径或指向镜像目录（如 `/usr/src/node-red`）的写入，容器重建即丢失。

#### 4.2 备份与迁移注意事项

- **先停后备份**：容器运行中直接拷贝 `./data` 可能拷到写入一半的文件（flows.json、sqlite），导致备份损坏。务必按第四章"备份"步骤先 `docker stop node-red-custom`。
- **sqlite WAL 附属文件**：若 db 启用了 WAL 模式（`journal_mode=WAL`），还会生成 `xxx.db-wal`、`xxx.db-shm`，只拷 `.db` 主文件会丢未合并数据。停容器后 WAL 自动合并；确需在线备份可用 sqlite3 的 `.backup` 命令。
- **凭据成对备份**：`flows_cred.json` 依赖 `settings.js` 中的 `credentialSecret` 解密，两者必须**成对**备份/迁移。只拷 `./data` 不拷 `./settings/settings.js`，现场将无法解密凭据。

---

### 五、安装节点（仅在线部署需要）

现场离线场景禁止执行，节点已预装在镜像中。

- 界面安装：登录编程页面 → 右上角菜单 → Manage palette → Install → 搜索安装
- 命令安装：

```bash
docker exec -u node-red -w /data node-red npm install @flowfuse/node-red-dashboard
docker restart node-red
```

> 命令安装的节点写入 `/data/node_modules`（已持久化），重启/重建容器不丢。

---

### 六、常用运维命令

| 操作     | 命令                                     |
| -------- | ---------------------------------------- |
| 查看状态 | `docker ps`                              |
| 查看日志 | `docker logs -f node-red`                |
| 重启     | `docker restart node-red`                |
| 停止     | `docker stop node-red`                   |
| 升级镜像 | 备份 `./data` → 重新构建/拉取 → 重新启动 |

---

### 七、注意事项

- 服务器防火墙需放行 1880 端口（`sudo ufw allow 1880`），云服务器另需安全组放行。
- Dashboard 认证为浏览器 Basic 弹窗（非登录页）；其 socket.io 实时推送不受认证保护，公网/严格场景建议加 nginx 反向代理。
- 请妥善保管 `settings.js` 中的 `credentialSecret`，丢失后 `flows_cred.json` 无法解密。
- 端口如需变更，修改 `-p` 映射并保持 `-e PORT` 一致后重启容器（如 `-p 8080:1880`，示例见 2.3.1）。
- **Node-RED 5.0 镜像必须显式设置** **`-e NODE_RED_SETTINGS=/data/settings.js`**，否则挂载的 settings.js 不会被加载，adminAuth / httpNodeAuth 全部失效（表现为 `/` 和 `/dashboard` 都不弹登录页）。docker run 部署必须在命令中手动加上。

---

## 第二部分 迁移

> 现场：容器 `energy-nodered-hvac-0`（Node-RED 3.1.9），data 存容器内 `/data`
> 目标：把 data 处理好放到 `/deploy/data`，给 5.0.4 自定义镜像用

> **容器名按现场替换**：`energy-nodered-hvac-0` 是**溧阳**现场的容器名；**张家港**现场是 `nodered`。下面所有命令里的 `energy-nodered-hvac-0` 都要替换成对应的容器名称即可。

### 1. 同步 credentialSecret（动手前必做）

> **动手前先确认老服务有没有 credentialSecret**：`grep` 有输出 → 走"有 secret"分支；无输出 → 走"无 secret"分支。两种情形对比如下：

| 老服务 settings.js        | 密钥实际在哪                                      | 新服务正确做法                                                                                   | 新服务错误做法                                  |
| ------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| **有** `credentialSecret` | 明文写在 settings.js                              | 新 settings.js 写**同一个值**（见下方命令）                                                      | 写错 / 漏写 → 解密失败                          |
| **没有** credentialSecret | 自动生成随机密钥，存 `/data/.config.runtime.json` | 新 settings.js **不要写** credentialSecret，把 `.config.runtime.json` 拷到新服务 `/deploy/data/` | 新 settings.js 写了 credentialSecret → 解密失败 |

**有 credentialSecret（常见）——同步到新服务**：

`flows_cred.json` 里的凭据是用 `settings.js` 的 `credentialSecret` 加密的。两边不一致 → 新服务启动报 `credential decrypt failed`，凭据全丢。

```bash
# 1) 拿老服务的 credentialSecret
docker exec energy-nodered-hvac-0 grep credentialSecret /data/settings.js
# 输出: credentialSecret: "xxxxxxxxxxxxxxxx"

# 2) 改新服务的 settings.js（跟老服务一致）
#    路径：/deploy/settings/settings.js
#    找到 credentialSecret 行，替换成老服务的值
```

> 为什么要先改？settings.js 在容器启动时读取，复制 `flows_cred.json` 之后 secret 不一致就报错了。

**无 credentialSecret 时的迁移命令**：

```bash
# 1) 确认老服务确实没有 credentialSecret
docker exec energy-nodered-hvac-0 grep credentialSecret /data/settings.js   # 无输出

# 2) 把自动密钥文件拷给新服务（settings.js 保持不写 credentialSecret）
docker cp energy-nodered-hvac-0:/data/.config.runtime.json /deploy/data/.config.runtime.json

# 3) 给权限：新容器内 node-red 用户（UID 1000）需可读可写该文件
#    否则启动时报 EACCES（备份 .config.runtime.json → .backup 失败）
sudo chown 1000:1000 /deploy/data/
```

> `.config.runtime.json` 是 Node-RED 启动时读取/更新的运行时配置（更新前会先复制 `.backup`），文件 owner 若非 UID 1000 会导致启动报 `EACCES: permission denied`。若 `/deploy/data/` 目录本身也是 root 所有，一并执行 `sudo chown -R 1000:1000 /deploy/data/`。

> 兜底：凭据不重要时，直接删掉新服务 `/deploy/data/flows_cred.json` 重新开始，但 MQTT / HTTP 等需要密钥的节点都要重新配置。

### 2. 备份数据

先停老容器，避免拷到写入一半的文件；整目录拷出 `/data`（flows.json / flows_cred.json / settings.js 等，**如有 sqlite 等 db 文件一并包含**）：

```bash
docker stop energy-nodered-hvac-0         # 先停容器，避免拷到写入一半的文件
mkdir -p /deploy/old-node-red-data
# 进镜像中查看数据的命令是docker run -it --rm <镜像名> /bin/bash
docker cp energy-nodered-hvac-0:/data/. /deploy/old-node-red-data/
ls /deploy/old-node-red-data/flows.json    # 确认存在再继续
```

> `docker cp /data/.` 是整目录拷贝，sqlite 等业务 db 文件（如 `*.db`）会自动带上；若只单独备份 flows，记得把 db 文件一起拷。
> 备份为**手动操作**（converter 与已移除的 scan-compat.sh 均不负责这一步）。

### 3. 启动转换工具，转换 json

#### 3.1 启动转换工具

工具来源：`nodered-dashboard-converter-v1.0.1.zip`（基于 `@flowfuse/node-red-dashboard-2-migration@0.0.4` 离线打包）。

```bash
# 1、解压nodered-dashboard-converter-v1.0.1.zip

# 2、双击: windows系统下：run.bat； linux系统下：./run.sh

# 3、在解压后的目录打开cmd窗口，执行node service.js，会启动一个本地服务，默认端口是3000

# 4、在浏览器打开http://localhost:3000，复制需要转换的json文件，转换之后会生成新的json文件
```

#### 3.2 节点转换支持清单

| 转换结果           | 节点                                                                                                                                     | 说明 / 后续处理                                                                                                                |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| 自动转换           | `ui_text` / `ui_button` / `ui_slider` / `ui_switch` / `ui_dropdown` / `ui_numeric` / `ui_text_input` / `ui_form` / `ui_tab` / `ui_group` | 重命名为 v2（kebab-case，如 `ui-text`）                                                                                        |
| 工具自动特殊处理   | `ui-base` / `ui-theme`                                                                                                                   | 自动新增默认配置（旧的 v1 `ui_base` 会被标 `d: true` 但保留）                                                                  |
| 不支持，需人工     | `ui_chart`                                                                                                                               | 类型改 `ui-chart` + 字段重排（见 [3.3.1](#331-ui_chart改字段a-类)）+ 上游补数据契约（见 [3.3.2](#332-ui_chart补数据契约b-类)） |
| 不支持，需人工     | `ui_template`                                                                                                                            | v3 走 Vue3 不接 AngularJS，必须业务重写（见 [3.3.3](#333-ui_template必须重写c-类)）                                            |
| 不支持，需人工     | `ui_date_picker`                                                                                                                         | v2/v3 无独立节点，改 `ui-text-input` 并设 `mode=date`（见 [3.3.4](#334-ui_date_picker换成-ui-text-inputc-2-类)）               |
| 不支持，需人工确认 | `ui_spacer` / `ui_gauge` / `ui_audio` / `ui_toast` / `ui_control` / `ui_colour_picker` / 旧 `ui_base`                                    | 工具标 `d: true` 原样保留，画布显示 unknown type，人工确认删/留                                                                |

#### 3.3 人工处理（全部转人工）

##### 3.3.1 ui_chart：改字段（A 类）

原脚本自动做的 `ui_chart` → `ui-chart` 字段改写已随脚本移除，人工对照下表在 flow 编辑器里逐个修改：

| v1 字段                                                                                 | v3 字段                              | 备注                                      |
| --------------------------------------------------------------------------------------- | ------------------------------------ | ----------------------------------------- |
| `type` = `ui_chart`                                                                     | `type` = `ui-chart`                  | 节点类型改名                              |
| `chartType`                                                                             | `property.type`                      | line / bar / pie …                        |
| `colors`（数组）                                                                        | `property.colors` + `series[].color` | 拆到每条 series，series 名占位 `Series N` |
| `removeOlder` + `removeOlderUnit`                                                       | `pointLimit`                         | 粗略换算成秒数                            |
| `label`                                                                                 | `label`                              | 保留                                      |
| `ymin` / `ymax`                                                                         | `ymin` / `ymax`                      | 保留                                      |
| `legend` / `interpolate` / `dot`                                                        | 同名                                 | 保留                                      |
| `xformat` / `useOneColor`                                                               | 同名                                 | 保留                                      |
| `x` / `y` / `wires` / `z` / `group` / `name` / `order` / `width` / `height` / `outputs` | 同名                                 | 画布位置和连接关系不动                    |

##### 3.3.2 ui_chart：补数据契约（B 类）

`ui_chart` 改 type + 字段还不够——**数据契约变了**，光改节点配置 v3 图表收不到数据。

```js
// v1 旧契约：msg.payload 直接是数字
msg.payload = 23.5;

// v3 新契约：msg.payload 是 { series: [{x, y}] }
msg.payload = { series: [{ x: 1754381234567, y: 23.5 }] };
```

人工排查画布上的 `ui-chart` 节点（也可在 flows.v2.json 里 `grep '"type": "ui-chart"'` 统计数量与 id），到 flow 编辑器里给每个 chart 上游插入一个 function 节点做数据契约转换：

```js
// 模板（每个 chart 一份，topic 按实际 wire 路径调整）
if (msg.topic === "<chart 上游 topic>") {
  msg.payload = {
    series: [
      {
        x: msg.timestamp || Date.now(),
        y: Number(msg.payload),
      },
    ],
  };
  return msg;
}
```

> 提示：可以全部 chart 共用一个 topic（比如 `"chart-data"`），然后一个 function 节点广播出去，下游多个 chart 都用 `if (msg.topic === "chart-data")` 接住——减少节点数量。

##### 3.3.3 ui_template：必须重写（C 类）

`ui_template` 节点都是同一段 AngularJS 模板：

```html
<div ng-repeat="(key, value) in msg.payload">
  <md-switch
    ng-model="msg.payload[key]"
    ng-change="sendSwitchState(msg.payload)"
  >
    {{ key }}
  </md-switch>
</div>
```

v3 的 `ui-template` 走 Vue 3，不接 AngularJS 语法；**没有 1:1 替代节点**，必须业务重写：

- 方案 1：上游 function 把对象拆成数组 + 固定数量的 `ui-switch` 节点
- 方案 2：用 v3 的 `ui-template` + `v-for` 改写（需要业务方接受新交互）

残留节点（`d: true`）启动时不报错，但会以 unknown type 留在画布上。人工重写后可直接在编辑器里删除原节点。

##### 3.3.4 ui_date_picker：换成 ui-text-input（C-2 类）

**为什么不能自动改**：v1 的 `ui_date_picker` 在 v2/v3 里**没有对应独立节点**。v2 设计上把 date/time/datetime 这类输入控件合并进了 `ui-text-input`，通过 `mode` 字段配置（`mode: 'date' | 'time' | 'datetime-local' | 'week' | 'month'`）。v3 沿用了这个设计。

**converter 为什么不处理**：`@flowfuse/node-red-dashboard-2-migration` 的 `transformers/map.json` 里没有 `ui_date_picker` 这一项，工具跑完后这个节点会被标 `d:true` 原样保留。

**人工处理步骤**（v3 编辑器里操作）：

1. 选中 v1 `ui_date_picker` 节点
2. 删掉它
3. 在同 group 拖一个 `ui-text-input` 节点
4. 按下表字段映射配置：

| v1 `ui_date_picker` 字段     | v3 `ui-text-input` 字段      | 备注                                                                                        |
| ---------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------- |
| `name`                       | `name`                       | 保留                                                                                        |
| `label`（默认 `'date'`）     | `label`                      | v3 默认是 `'Text Input'`，**必须手动改成 `'date'`**（业务方约定）                           |
| `group`                      | `group`                      | 保留                                                                                        |
| `order` / `width` / `height` | `order` / `width` / `height` | 保留                                                                                        |
| `passthru`                   | `passthru`                   | 保留                                                                                        |
| `topic` / `topicType`        | `topic` / `topicType`        | 保留                                                                                        |
| `className`                  | `className`                  | 保留                                                                                        |
| —                            | `mode`                       | **新增**，设为 `'date'`（按业务需要也可 `'datetime-local'`）                                |
| （输入 msg）                 | `payload`                    | v1 是 epoch 毫秒；v3 `ui-text-input` 输出的也是字符串，**接入时可能要 function 节点转类型** |

**快速检查方法**：converter 转换后人工在画布上查找 unknown type 节点，或 `grep ui_date_picker flows.v2.json` 确认是否用过该节点——没用到就跳过本节。

##### 3.3.5 ui_spacer / ui_gauge 等：确认删/留

`d: true` 状态下 v3 启动不报错，画布会有 unknown type——接受就直接用；想清理就在编辑器里手动删/改名。

#### 3.4 转换完成后：启动 + 验证

> 时序要点：**手动备份 → converter 转换 → 人工处理（字段 / 契约 / 重写 / 换节点）→ 手动部署**。后一步严格依赖前一步的产物。

```bash
# 手动部署（转换 + 人工处理完后再复制）
cp /deploy/old-node-red-data/flows.v2.json /deploy/data/flows.json

# 启动（docker run 命令行显式指定挂载/端口/认证配置）
cd /deploy
docker run -d --name node-red-custom \
  -p 1890:1890 \
  -e TZ=Asia/Shanghai \
  -e PORT=1890 \
  -e NODE_RED_SETTINGS=/data/settings.js \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/data/settings.js \
  --restart unless-stopped \
  node-red-custom:5.0.4

# 看启动日志
docker logs -f node-red-custom 2>&1 | grep "Server now running"
# 出现 Server now running at http://0.0.0.0:1890 即成功

# 浏览器访问 http://<服务器IP>:1890
# admin / 3er4#ER$ 登录看画布
# /dashboard → user / Nts@1234
```

### 4. 常见问题

#### Q1: sqlite 节点报 SQLITE_READONLY

文件权限问题：

```bash
sudo chmod a+rw /deploy/data/*.db
# 还报错就连目录一起改
sudo chmod a+rwX /deploy/data/
```

#### Q2: 启动报 credential decrypt failed

`flows_cred.json` 解密失败。原因是新服务 `settings.js` 的 `credentialSecret` 跟老服务不一致。

**修复**：

1. 拿老服务的 secret：`docker exec energy-nodered-hvac-0 grep credentialSecret /data/settings.js`
2. 改 `/deploy/settings/settings.js` 的 `credentialSecret` 字段
3. 重启：`docker restart node-red-custom`

**预防**：迁移前先做 [第 1 步](#1-同步-credentialsecret动手前必做)。
