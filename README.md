# Node-RED 自定义部署

基于 `nodered/node-red:5.0.4` 构建的自定义镜像部署方案：预装 dashboard / sqlite / modbus / s7 等第三方节点，内置双入口认证与自动注册 agent，支持离线部署与从 3.1.9 容器迁移。

## 目录结构

```
.
├── deploy/                # 镜像构建目录（构建上下文）
│   ├── Dockerfile         # 镜像定义：预装节点 + 烧入 settings.js/agent
│   ├── settings/
│   │   └── settings.js    # 双入口认证配置（构建期烧入镜像）
│   ├── agent/
│   │   └── main.js        # 自动注册 agent（零依赖）
│   └── agent-entrypoint.sh# 容器入口：后台拉起 agent，再启动 node-red
├── docs/
│   ├── deploy.md                    # 镜像构建 + 部署 + 自动注册
│   ├── migrate.md                   # 数据迁移
│   └── deploy-and-migrate.md        # 历史合并文档（内容以 deploy.md / migrate.md 为准）
├── node-server/           # 中心管理平台（Spring Boot，接收注册/心跳）
├── old-data/              # 迁移用：老容器数据备份
├── new-data/              # 迁移用：转换后的新数据
├── tools/                 # 迁移工具（dashboard converter）
├── upgrade/               # 迁移注意点清单
└── README.md
```

## 快速上手

### 部署

```bash
cd deploy
docker build -t node-red-custom:5.0.4 .
mkdir -p data && sudo chown -R 1000:1000 ./data/
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  --add-host nginxIn.tshlms.com:192.168.50.177 \
  --add-host nginxin.tshlms.com:192.168.50.177 \
  -v $(pwd)/data:/data \
  --restart unless-stopped \
  node-red-custom:5.0.4
# 访问 http://<IP>:1880
```

- `--add-host` 域名映射是**实例注册必须**的，两条大小写都要加，IP 换成现场实际内网地址（详见 [docs/deploy.md](docs/deploy.md)）。
- 换平台地址加 `-e AGENT_API_BASE=http://平台地址:端口`；换心跳间隔加 `-e AGENT_HEARTBEAT_INTERVAL=60`。
- 多实例部署：各实例用独立的容器名、端口、数据目录，目录都要读写权限。

离线部署（现场导入镜像包）与启动命令逐字段说明见 [docs/deploy.md](docs/deploy.md)。

### 从老版本迁移

```bash
# 1) 处理凭据密钥（沿用老容器的 credentialSecret / 密钥文件，见 docs/migrate.md）
# 2) 备份老数据 → converter 转 Dashboard v2 → 处理不兼容节点
#    完整流程见 docs/migrate.md

# 3) 启动新服务
cd deploy
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  --add-host nginxIn.tshlms.com:192.168.50.177 \
  --add-host nginxin.tshlms.com:192.168.50.177 \
  -v $(pwd)/data:/data \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

## 文档导航

| 场景                           | 文档                                           |
| ------------------------------ | ---------------------------------------------- |
| 打包文件、构建镜像、部署、注册 | [docs/deploy.md](docs/deploy.md)               |
| 从老版本迁移数据               | [docs/migrate.md](docs/migrate.md)             |
| 中心管理平台（接收注册/心跳）  | [node-server/README.md](node-server/README.md) |
