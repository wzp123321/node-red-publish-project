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
#
# 减小体积的做法：
#   1. 所有节点合并为一次 npm install，npm 对共同依赖统一去重（hoisting），
#      避免分多次安装产生重复依赖，node_modules 更小
#   2. 安装与清理缓存在同一 RUN 层内完成：npm / 原生模块缓存不残留进镜像层；
#      若拆成多个 RUN，缓存虽在运行时不可见，物理上仍占镜像体积
#   3. 代价：合并后按节点拆分的构建缓存失效；频繁增删节点导致反复全量安装时，
#      可权衡拆回每行一个 RUN（体积略增、构建缓存可复用）
#
# --no-package-lock：不生成/不更新 package-lock.json，保持镜像层干净

RUN npm install --no-audit --no-fund --no-package-lock \
    @flowfuse/node-red-dashboard@1.30.2 \
    node-red-node-sqlite@2.0.1 \
    node-red-contrib-modbus@5.60.1 \
    node-red-contrib-s7@3.1.3 \
    && rm -rf ~/.npm ~/.cache/node-gyp ~/.cache/prebuild-install

# 其他节点按需求追加，直接加在上面的 npm install 参数列表中（锁定版本）：
# 例如：node-red-contrib-xxx@x.y.z
