# Node-RED 数据迁移（3.1.9 容器 → 5.0.4 自定义镜像）

> 现场：容器 `energy-nodered-hvac-0`（Node-RED 3.1.9），data 存容器内 `/data`
> 目标：把 data 处理好放到 `/deploy/data`，给 5.0.4 自定义镜像用

> **容器名按现场替换**：`energy-nodered-hvac-0` 是**溧阳**现场的容器名；**张家港**现场是 `nodered`。下面所有命令里的 `energy-nodered-hvac-0` 都要替换成对应的容器名称即可。

## 1. 同步 credentialSecret（动手前必做）

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
```

> 兜底：凭据不重要时，直接删掉新服务 `/deploy/data/flows_cred.json` 重新开始，但 MQTT / HTTP 等需要密钥的节点都要重新配置。

## 2. 备份数据

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

## 3. 启动转换工具，转换 json

### 3.1 启动转换工具

工具来源：`nodered-dashboard-converter-v1.0.1.zip`（基于 `@flowfuse/node-red-dashboard-2-migration@0.0.4` 离线打包）。

```bash
# 1、解压nodered-dashboard-converter-v1.0.1.zip

# 2、双击: windows系统下：run.bat； linux系统下：./run.sh

# 3、在解压后的目录打开cmd窗口，执行node service.js，会启动一个本地服务，默认端口是3000

# 4、在浏览器打开http://localhost:3000，复制需要转换的json文件，转换之后会生成新的json文件
```

### 3.2 节点转换支持清单

| 转换结果           | 节点                                                                                                                                     | 说明 / 后续处理                                                                                                                |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| 自动转换           | `ui_text` / `ui_button` / `ui_slider` / `ui_switch` / `ui_dropdown` / `ui_numeric` / `ui_text_input` / `ui_form` / `ui_tab` / `ui_group` | 重命名为 v2（kebab-case，如 `ui-text`）                                                                                        |
| 工具自动特殊处理   | `ui-base` / `ui-theme`                                                                                                                   | 自动新增默认配置（旧的 v1 `ui_base` 会被标 `d: true` 但保留）                                                                  |
| 不支持，需人工     | `ui_chart`                                                                                                                               | 类型改 `ui-chart` + 字段重排（见 [3.3.1](#331-ui_chart改字段a-类)）+ 上游补数据契约（见 [3.3.2](#332-ui_chart补数据契约b-类)） |
| 不支持，需人工     | `ui_template`                                                                                                                            | v3 走 Vue3 不接 AngularJS，必须业务重写（见 [3.3.3](#333-ui_template必须重写c-类)）                                            |
| 不支持，需人工     | `ui_date_picker`                                                                                                                         | v2/v3 无独立节点，改 `ui-text-input` 并设 `mode=date`（见 [3.3.4](#334-ui_date_picker换成-ui-text-inputc-2-类)）               |
| 不支持，需人工确认 | `ui_spacer` / `ui_gauge` / `ui_audio` / `ui_toast` / `ui_control` / `ui_colour_picker` / 旧 `ui_base`                                    | 工具标 `d: true` 原样保留，画布显示 unknown type，人工确认删/留                                                                |

### 3.3 人工处理（全部转人工）

#### 3.3.1 ui_chart：改字段（A 类）

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

#### 3.3.2 ui_chart：补数据契约（B 类）

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

#### 3.3.3 ui_template：必须重写（C 类）

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

#### 3.3.4 ui_date_picker：换成 ui-text-input（C-2 类）

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

#### 3.3.5 ui_spacer / ui_gauge 等：确认删/留

`d: true` 状态下 v3 启动不报错，画布会有 unknown type——接受就直接用；想清理就在编辑器里手动删/改名。

### 3.4 转换完成后：启动 + 验证

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

## 4. 常见问题

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
3. 重启：`docker restart node-red-custom`

**预防**：迁移前先做 [第 1 步](#1-同步-credentialsecret动手前必做)。
