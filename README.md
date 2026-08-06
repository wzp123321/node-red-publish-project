# Node-RED 自定义部署

基于 `nodered/node-red:5.0.4` 构建的自定义镜像部署方案，支持从 3.1.9 容器平滑迁移。

## 目录结构

```
.
├── copy/                  # 老数据备份（迁移场景用）
├── deploy/                # 部署根目录
│   ├── data/              # 运行时 data 目录（容器挂载点）
│   ├── scripts/           # 工具脚本
│   ├── settings/          # settings.js 配置
│   └── Dockerfile
├── docs/                  # 文档
│   ├── deploy.md          # 镜像构建 + 部署
│   ├── design.md          # 设计说明
│   └── migrate.md         # 数据迁移
└── README.md              # 本文件
```

## 快速上手

### 部署

```bash
# 构建自定义镜像并启动（挂载/端口/认证参数说明见 docs/deploy.md）
cd deploy
docker build -t node-red-custom:5.0.4 .
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  -e NODE_RED_SETTINGS=/data/settings.js \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/data/settings.js \
  --restart unless-stopped \
  node-red-custom:5.0.4
# 访问 http://<IP>:1880
```

### 从老版本迁移

```bash
# 1) 同步 credentialSecret（必做）
docker exec <老容器> grep credentialSecret /data/settings.js
# 把值写到 deploy/settings/settings.js

# 2) 备份老数据、converter 转 Dashboard v2、不兼容节点处理
#    完整流程见 docs/migrate.md（历史迁移工具 scan-compat.sh 已移除）

# 3) 启动新服务
cd deploy
docker run -d --name node-red-custom \
  -p 1880:1880 \
  -e TZ=Asia/Shanghai \
  -e PORT=1880 \
  -e NODE_RED_SETTINGS=/data/settings.js \
  -v $(pwd)/data:/data \
  -v $(pwd)/settings/settings.js:/data/settings.js \
  --restart unless-stopped \
  node-red-custom:5.0.4
```

## 文档导航

| 场景                     | 文档                               |
| ------------------------ | ---------------------------------- |
| 镜像怎么构建、容器怎么跑 | [docs/deploy.md](docs/deploy.md)   |
| 设计思路、自定义点       | [docs/design.md](docs/design.md)   |
| 从老版本迁移数据         | [docs/migrate.md](docs/migrate.md) |
