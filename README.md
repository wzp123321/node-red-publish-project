# Node-RED 自定义部署

基于 `nodered/node-red:5.0.4` 构建的自定义镜像部署方案，支持从 3.1.9 容器平滑迁移。

## 目录结构

```
.
├── copy/                  # 老数据备份（迁移场景用）
├── deploy/                # 部署根目录
│   ├── data/              # 运行时 data 目录（容器挂载点）
│   ├── scripts/           # 工具脚本
│   │   ├── README.md
│   │   └── scan-compat.sh # 一键迁移脚本
│   ├── settings/          # settings.js 配置
│   ├── docker-compose.yml
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
cd deploy
docker compose up -d
# 访问 http://<IP>:1890
```

### 从老版本迁移

```bash
# 1) 同步 credentialSecret（必做）
docker exec <老容器> grep credentialSecret /data/settings.js
# 把值写到 deploy/settings/settings.js

# 2) 干跑：备份+扫描，看 http request urls
bash deploy/scripts/scan-compat.sh --container <老容器> --dry-run

# 3) 实跑：备份+扫描+替换+部署
bash deploy/scripts/scan-compat.sh --container <老容器> \
  --replace http://老地址=http://新地址

# 4) 启动新服务
cd deploy && docker compose up -d
```

## 文档导航

| 场景 | 文档 |
|------|------|
| 镜像怎么构建、容器怎么跑 | [docs/deploy.md](docs/deploy.md) |
| 设计思路、自定义点 | [docs/design.md](docs/design.md) |
| 从老版本迁移数据 | [docs/migrate.md](docs/migrate.md) |
| 迁移脚本说明 | [deploy/scripts/README.md](deploy/scripts/README.md) |
