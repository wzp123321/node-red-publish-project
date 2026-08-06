# ==============================================
# Node-RED 自定义镜像：官方基座 + 预装第三方节点
# ==============================================
# 适用场景：现场完全离线，不允许/无法安装节点
# 节点在构建期烧入镜像，现场零安装、开箱即用
#
# 构建（需外网，在构建机/CI 执行）：
#   docker build -t node-red-custom:5.0.4 .
#
# 导出离线包（传输到现场）：
#   docker save node-red-custom:5.0.4 | gzip > node-red-5.0.4.tar.gz
#
# 现场导入并启动：
#   docker load -i node-red-5.0.4.tar.gz
#   启动命令见 docs/deploy.md：docker run 命令行显式指定端口/挂载/认证配置

FROM nodered/node-red:5.0.4

# 沿用官方镜像用户 node-red（UID 1000），保持文件权限一致
USER node-red

# ---------- 预装第三方节点清单（锁定版本，按需增删）----------
# 规则：构建前用 `npm view <包名> version` 查询最新稳定版并锁定，保证构建可复现
# 每行一个 RUN，便于 Docker 构建缓存复用

# Dashboard 2.0 前端节点（前端页面 /dashboard 必需）
RUN npm install --no-audit --no-fund @flowfuse/node-red-dashboard@1.30.2

# sqlite 数据库节点（业务数据存储，按需启用；含原生编译，构建耗时较长）
RUN npm install --no-audit --no-fund node-red-node-sqlite@2.0.1

# Modbus 通信节点（工业设备 Modbus TCP/RTU 通信）
RUN npm install --no-audit --no-fund node-red-contrib-modbus@5.60.1

# Siemens S7 通信节点（S7 PLC 通信）
RUN npm install --no-audit --no-fund node-red-contrib-s7@3.1.3

# 其他节点按需求追加，同样锁定版本：
# RUN npm install --no-audit --no-fund <node-package>@<version>

# 节点装入 /usr/src/node-red/node_modules（镜像层固化），
# 与数据卷 /data（flows/凭据）互不干扰；沿用官方镜像默认启动命令
