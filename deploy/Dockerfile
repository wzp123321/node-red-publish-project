# ==============================================
# Node-RED 自定义镜像：官方基座 + 预装第三方节点 + 自动注册 Agent
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
#   启动命令见 docs/deploy.md：只需挂载数据卷 /data + 指定端口；
#   settings.js 已烧入镜像，启动时由 CMD 的 --settings 直接加载，无需挂载
#
# 自动注册 Agent（deploy/agent/main.js，零依赖，仅用 Node 内置 http/https）：
#   容器启动即拉起 agent；未配置 AGENT_API_BASE 时 agent 立即退出，与老镜像行为一致。
#   需要注册时在 docker run 加环境变量 AGENT_API_BASE（可选 AGENT_HEARTBEAT_INTERVAL），
#   详见 docs/deploy.md「自动注册机制」章节。

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

# ---------- 自动注册 Agent ----------
# 零依赖（仅用 Node 内置 http/https），不新增任何 npm 包；保持 node-red 用户权限
COPY --chown=node-red:node-red agent/ /usr/src/node-red/agent/
COPY --chown=node-red:node-red agent-entrypoint.sh /usr/src/node-red/agent-entrypoint.sh

# ---------- 出厂 settings.js（烧入镜像，启动默认加载）----------
# 刻意放在 /data 之外：/data 是运行时 userDir，docker run 时通常被
# `-v $(pwd)/data:/data` 挂载遮蔽，镜像内 /data 的内容会整体不可见。
#
# 启动时由下方 CMD 传 --settings 绝对路径加载，完整执行链路：
#   agent-entrypoint.sh → 官方 entrypoint.sh →
#   node node_modules/node-red/red.js --userDir /data flows.json --settings /usr/src/node-red/settings.js
#
# red.js 命中 `--settings` 分支后即固定使用该文件：不再去 /data 找 settings.js，
# 也不会在缺失时往 /data 复制一份默认配置——历史上前者导致认证不生效、
# 后者（宿主机目录属主非 1000 时）直接 EACCES 导致容器反复重启，此处一并规避。
# 数据卷 /data 只承担 flows/凭据等运行时数据的持久化。
COPY --chown=node-red:node-red settings/settings.js /usr/src/node-red/settings.js

# 覆盖入口：先后台启动 agent（注册+心跳），再 exec 官方 entrypoint.sh 启动 node-red
# 官方 entrypoint.sh（/usr/src/node-red/entrypoint.sh）未被覆盖，官方行为完整保留
ENTRYPOINT ["bash", "/usr/src/node-red/agent-entrypoint.sh"]

# 传给官方 entrypoint，由其透传给 red.js：始终加载镜像内出厂 settings.js。
# 现场需临时换配置（不改镜像）时，在 docker run 末尾覆盖即可，如：
#   docker run ... node-red-custom:5.0.4 --settings /tmp/settings.js
CMD ["--settings", "/usr/src/node-red/settings.js"]
