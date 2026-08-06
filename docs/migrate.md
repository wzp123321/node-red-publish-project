# Node-RED 数据迁移（3.1.9 容器 → 5.0.4 自定义镜像）

> 现场：容器 `energy-nodered-hvac-0`（Node-RED 3.1.9），data 存容器内 `/data`
> 目标：把 data 处理好放到 `/deploy/data`，给 5.0.4 自定义镜像用

## 0. 同步 credentialSecret（动手前必做）

`flows_cred.json` 里的凭据是用 `settings.js` 的 `credentialSecret` 加密的。两边不一致 → 新服务启动报 `credential decrypt failed`，凭据全丢。

```bash
# 1) 拿老服务的 credentialSecret
docker exec energy-nodered-hvac-0 grep credentialSecret /data/settings.js
# 输出: credentialSecret: "xxxxxxxxxxxxxxxx"

# 2) 改新服务的 settings.js（跟老服务一致）
#    路径：/deploy/settings/settings.js
#    找到 credentialSecret 行，替换成老服务的值

# 3) 走下面 1-2 步正常迁移
```

> 为什么要先改？settings.js 在容器启动时读取，复制 `flows_cred.json` 之后 secret 不一致就报错了。

## 1. 备份老数据 + 转 Dashboard v2（converter 工具）

> **时序**：本节所有动作都在**老容器的 `/data` 已备份到 `/deploy/old-node-red-data/` 之后**进行。converter 直接读 workdir 里的 `flows.json`，输出 `flows.v2.json` 留在同一目录。

老 flow 里 `ui_text / ui_button / ui_group ...` 是 v1 节点（下划线），v3 镜像不识别，`scan-compat.sh` 也不管这一层转换。**必须先用 converter 工具把 v1 节点批量改成 v2。**

### 1.1 备份老容器数据到 workdir

```bash
docker stop energy-nodered-hvac-0         # 先停容器，避免拷到写入一半的文件
mkdir -p /deploy/old-node-red-data
# 进镜像中查看数据的命令是docker run -it --rm <镜像名> /bin/bash
docker cp energy-nodered-hvac-0:/data/. /deploy/old-node-red-data/
ls /deploy/old-node-red-data/flows.json    # 确认存在再继续
```

> **容器名按现场替换**：`energy-nodered-hvac-0` 是**溧阳**现场的容器名；**张家港**现场是 `nodered`。下面所有命令里的 `energy-nodered-hvac-0` 在张家港都要替换成 `nodered`。

> 这一步现在改成**手动操作**（见 [第 2 节](#2-兼容性处理scan-compatsh) 的"手动备份"小节）。

### 1.2 用 converter 工具把 flows.json 转 v2

工具来源：项目自带 `nodered-dashboard-converter-v1.0.0.zip`（基于 `@flowfuse/node-red-dashboard-2-migration@0.0.4` 离线打包）。

```bash
# 1) 解压到 deploy/scripts/converter/（约定路径，跟 scan-compat.sh 同目录）
unzip nodered-dashboard-converter-v1.0.0.zip -d deploy/scripts/converter/
cd deploy/scripts/converter

# 2) 装依赖（zip 内若已带 node_modules 可跳；现场离线就用 npm install --offline）
npm install --omit=dev

# 3) 转换：读 workdir 里的 flows.json，输出 v2 版到同目录
node migrate.js /deploy/old-node-red-data/flows.json \
  > /deploy/old-node-red-data/flows.v2.json
```

**工具做了什么 / 没做什么：**

| 节点                                                                                                                                     | 处理                                                                                                                                                                                                                              |
| ---------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ui_text` / `ui_button` / `ui_slider` / `ui_switch` / `ui_dropdown` / `ui_numeric` / `ui_text_input` / `ui_form` / `ui_tab` / `ui_group` | 重命名为 v2（kebab-case，如 `ui-text`）                                                                                                                                                                                           |
| `ui-base` / `ui-theme`                                                                                                                   | **自动新增** 默认配置（旧的 v1 `ui_base` 会被标 `d: true` 但保留）                                                                                                                                                                |
| `ui_chart` / `ui_template` / `ui_spacer` / `ui_gauge` / `ui_audio` / `ui_toast` / `ui_control` / `ui_colour_picker`                      | **不支持**，原样保留并 `d: true`                                                                                                                                                                                                  |
| `ui_date_picker`                                                                                                                         | **不支持**，且 v2/v3 都没有独立 `ui-date-picker` 节点——date 功能被合并到 `ui-text-input` 的 `mode` 配置。**必须在编辑器里手工改成 `ui-text-input` 并设置 `mode=date`**，详见 [4.4](#44-c-2-类人工改-uidate_picker-为-uitextinput) |

> 产物 `flows.v2.json` 还不能直接交给 v3 镜像：残留的 v1 节点（`ui_base / ui_spacer / ui_chart / ui_template`）会出现在画布上，必须接着走下面两步——`scan-compat.sh` 做 url 替换 + 残留节点按 [第 4 节](#4-不支持的节点处理abc-分类) 处理。

## 2. 兼容性处理（scan-compat.sh）

脚本：[`deploy/scripts/scan-compat.sh`](../deploy/scripts/scan-compat.sh)（独立文件，拷到服务器用）

**作用**：在 converter 跑完的 `flows.v2.json` 基础上做两件事——

1. **ui_chart 字段改写**：`ui_chart` → `ui-chart`，按 v3 字段结构重排（详见 [4.1](#41-a-类脚本自动改写)）
2. **http request url 前缀替换**：扫描所有 `http request` 节点，按 `--replace` 规则替换 url

**不会做**：docker 备份、部署到 `/deploy/data/`、converter 转换——这几步全手动。

**用法**：

```bash
# 1) 干跑：扫描 + 统计，不写文件
bash scan-compat.sh --workdir /deploy/old-node-red-data --dry-run

# 2) 实跑：ui_chart 改字段 + http url 替换（覆盖 workdir 里的 flows.v2.json）
bash scan-compat.sh \
  --workdir /deploy/old-node-red-data \
  --replace http://192.168.1.100:1880=http://192.168.1.200:1890
```

**手动备份**（脚本不负责，参考命令）：

```bash
docker stop energy-nodered-hvac-0
mkdir -p /deploy/old-node-red-data
docker cp energy-nodered-hvac-0:/data/. /deploy/old-node-red-data/
```

**手动部署**（脚本处理完，再人工复制）：

```bash
cp /deploy/old-node-red-data/flows.v2.json /deploy/data/flows.json
```

启动命令见 [第 3 节](#3-启动--验证)。

参数说明：

| 参数        | 默认值                      | 说明                                 |
| ----------- | --------------------------- | ------------------------------------ |
| `--workdir` | `/deploy/old-node-red-data` | workdir 路径                         |
| `--input`   | `flows.v2.json`             | 待处理文件名（converter 跑完的产物） |
| `--replace` | （无）                      | url 前缀替换 `OLD=NEW`，可多次       |
| `--dry-run` | `False`                     | 只扫描 + 统计，不写文件              |

> 脚本会顺带**扫描并 WARN** 残留的 v1 节点（`ui_base / ui_spacer / ui_template / ui_date_picker`）。这些脚本不处理，详见 [第 4 节](#4-不支持的节点处理abc-分类)。

## 3. 启动 + 验证

```bash
# 启动
cd /deploy && docker compose up -d

# 看启动日志
docker logs -f node-red-custom 2>&1 | grep "Server now running"
# 出现 Server now running at http://0.0.0.0:1890 即成功

# 浏览器访问 http://<服务器IP>:1890
# admin / 3er4#ER$ 登录看画布
# /dashboard → user / Nts@1234
```

## 4. 不支持的节点处理（ABC 分类）

converter 工具跑完后仍会残留若干 v1 节点。这些节点全部被工具打了 `d: true`（禁用），v3 启动不会崩溃，但**会留在画布上变成"unknown type"垃圾**。`scan-compat.sh` 只处理 `ui_chart`，其余只能人工。处理分三档：

| 节点类型         | 档位         | 谁来做                                    |
| ---------------- | ------------ | ----------------------------------------- |
| `ui_base`        | —（不处理）  | 脚本 WARN 提示，人工确认删/留             |
| `ui_spacer`      | —（不处理）  | 脚本 WARN 提示，人工确认改/留             |
| `ui_chart`       | **A + B 类** | 脚本改字段 + 人工贴数据补丁               |
| `ui_template`    | **C 类**     | 必须人工重写                              |
| `ui_date_picker` | **C-2 类**   | 必须人工改成 `ui-text-input`（mode=date） |

### 4.1 A 类：脚本自动改写（仅 ui_chart）

`scan-compat.sh` 在 v2 转换结果上做 `ui_chart` → `ui-chart` 字段改写：

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

> `ui_base` / `ui_spacer` 脚本**只统计不处理**（避免改坏 v2 节点结构）。`d: true` 状态下 v3 启动不报错，画布会有 unknown type——接受就直接用；想清理就在编辑器里手动删/改名。

### 4.2 B 类：半自动（生成数据契约补丁）

`ui_chart` 改 type + 字段还不够——**数据契约变了**，光改节点配置 v3 图表收不到数据。

```js
// v1 旧契约：msg.payload 直接是数字
msg.payload = 23.5;

// v3 新契约：msg.payload 是 { series: [{x, y}] }
msg.payload = { series: [{ x: 1754381234567, y: 23.5 }] };
```

`scan-compat.sh` 日志会列出 14 个 chart 节点的 `id / label / chartType`（即上一步 [4.1](#41-a-类脚本自动改写) 里的 `===CHART_REWRITE===` 输出）。人工按这个清单，到 flow 编辑器里给每个 chart 上游插入一个 function 节点做数据契约转换：

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

### 4.3 C 类：必须人工重写

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

`ui_template` 残留节点在 v3 启动时会被 `scan-compat.sh` 列入 v1 残留 WARN。人工重写后可直接在编辑器里删除原节点。

### 4.4 C-2 类：人工改 `ui_date_picker` 为 `ui-text-input`

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

**快速检查方法**：跑完 scan-compat.sh 后日志里有 `===V1_RESIDUAL===` 段，里面会列出所有 v1 残留节点类型——本批次如果 `ui_date_picker` 不在列表里，说明现场没用这个节点，可以跳过 4.4 节。

### 4.5 处理流程总览

```
老容器 energy-nodered-hvac-0 (Node-RED 3.1.9)
   │  /data
   ▼
[手动: docker stop + docker cp]
   │  ──► /deploy/old-node-red-data/flows.json (v1 旧)
   ▼
[手动: converter 工具]          ──► /deploy/old-node-red-data/flows.v2.json
   │                                (大部分已转，残留 v1 节点全部 d:true)
   ▼
[scan-compat.sh]                ──► ui_chart 改字段
   │                                + http url 替换
   │                                + WARN 各类 v1 残留
   │  ──► 覆盖 flows.v2.json
   ▼
[人工]   B 类：每个 chart 上游插入 adapter function 节点
   │      C 类：每个 ui_template 业务重写
   │      C-2 类：每个 ui_date_picker 改成 ui-text-input（mode=date）
   ▼
[手动: cp + docker compose up -d]
   ▼
flows.json (v3 兼容)  ──► /deploy/data/ 运行
```

> 时序要点：**手动备份 → converter → scan-compat（自动改 A + WARN）→ 人工补 B/C → 手动部署**。后一步严格依赖前一步的产物。

## 5. 常见问题

### Q1: sqlite 节点报 SQLITE_READONLY

文件权限问题：

```bash
sudo chmod a+rw /deploy/data/*.db
# 还报错就连目录一起改
sudo chmod a+rwX /deploy/data/
```

### Q2: 启动报 credential decrypt failed

`flows_cred.json` 解密失败。原因是新服务 `settings.js` 的 `credentialSecret` 跟老服务不一致。

**修复**：

1. 拿老服务的 secret：`docker exec energy-nodered-hvac-0 grep credentialSecret /data/settings.js`
2. 改 `/deploy/settings/settings.js` 的 `credentialSecret` 字段
3. 重启：`docker compose restart node-red-custom`

**预防**：迁移前先做 [第 0 步](#0-同步-credentialsecret动手前必做)。
