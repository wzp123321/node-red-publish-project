#!/usr/bin/env bash
# ============================================================
# Node-RED 容器启动入口（自动注册 Agent 版本）
# 职责：
#   1. 后台启动注册/心跳 agent（main.js，AGENT_ENABLED=false 时立即退出，无副作用）
#   2. exec 官方 entrypoint.sh，完整保留官方启动行为（settings 加载、safe mode、
#      npm start、信号转发等），agent 与 node-red 进程相互独立
# 说明：
#   - 不覆盖官方 /usr/src/node-red/entrypoint.sh，仅在此脚本末尾 exec 它
#   - 本脚本必须以 bash 执行（官方基础镜像 debian-slim 自带 bash）
#   - docker stop 时 SIGTERM 只会发给 PID 1（node-red），agent 的优雅注销
#     属"尽力而为"；平台侧离线/注销主要依赖心跳超时检测
# ============================================================
set -e

AGENT_DIR=/usr/src/node-red/agent

# 后台启动 agent（不阻塞 node-red 启动；注册失败由 agent 内部退避重试）
if [ -f "${AGENT_DIR}/main.js" ]; then
  echo "[agent] 启动自动注册 agent：node ${AGENT_DIR}/main.js"
  node "${AGENT_DIR}/main.js" &
fi

# 交给官方入口启动 node-red（保留官方全部行为与环境变量处理）
exec /usr/src/node-red/entrypoint.sh "$@"
