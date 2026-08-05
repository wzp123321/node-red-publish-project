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

## 1. 一键迁移

脚本：[`deploy/scripts/scan-compat.sh`](../deploy/scripts/scan-compat.sh)（独立文件，拷到服务器用）

**用法**：

```bash
# 1) 干跑：只备份+扫描，看 http request urls（不替换不部署）
bash scan-compat.sh --container energy-nodered-hvac-0 --dry-run

# 2) 实际：备份+扫描+替换+部署（一条命令搞定）
bash scan-compat.sh \
  --container energy-nodered-hvac-0 \
  --replace http://192.168.1.100:1880=http://192.168.1.200:1890
```

参数说明：

| 参数           | 默认值                      | 说明                           |
| -------------- | --------------------------- | ------------------------------ |
| `--container`  | （无）                      | 源容器名，启用 docker 备份流程 |
| `--workdir`    | `/deploy/old-node-red-data` | 备份/工作目录                  |
| `--deploy-dir` | `/deploy/data`              | 目标数据目录                   |
| `--replace`    | （无）                      | url 前缀替换 OLD=NEW，可多次   |
| `--dry-run`    | `False`                     | 只跑备份+扫描                  |

## 2. 启动 + 验证

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

## 3. 常见问题

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
