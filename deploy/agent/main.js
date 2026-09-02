#!/usr/bin/env node
/**
 * Node-RED 实例自动注册 Agent（零依赖，仅用 Node 内置 http/https）
 * ---------------------------------------------------------------------------
 * 职责：
 *   1. 启动时调用平台注册接口（携带预授权凭证 AGENT_TOKEN），失败指数退避重试
 *   2. 注册成功后定时上报心跳，平台据此维护 在线/离线 状态
 *   3. 收到退出信号（SIGTERM/SIGINT）时尽力调用注销接口
 *   4. 心跳返回"实例不存在"（如被平台手动注销 / 离线自动注销）时自动重新注册
 *
 * 环境变量（未启用时脚本直接退出，不影响 node-red 启动，与老部署完全兼容）：
 *   AGENT_ENABLED               是否启用，默认 false
 *   AGENT_API_BASE              平台 API 基础地址，如 http://192.168.1.10:8080/api/v1
 *   AGENT_TOKEN                 预授权凭证（平台预生成，一实例一 Token，防止恶意注册）
 *   AGENT_INSTANCE_ID           实例 ID（可选；缺省自动生成并持久化到 /data/.agent-instance-id，
 *                               容器重启后 ID 不变，重新注册即恢复在线）
 *   AGENT_HEARTBEAT_INTERVAL    心跳间隔（秒），默认 30
 * ---------------------------------------------------------------------------
 */
"use strict";

const http = require("http");
const https = require("https");
const fs = require("fs");
const os = require("os");

/* ---------------- 配置 ---------------- */
const ENABLED = (process.env.AGENT_ENABLED || "false").toLowerCase() === "true";
const API_BASE = (process.env.AGENT_API_BASE || "").replace(/\/+$/, "");
const TOKEN = process.env.AGENT_TOKEN || "";
const HEARTBEAT_INTERVAL = Math.max(
  5,
  Number(process.env.AGENT_HEARTBEAT_INTERVAL) || 30,
);
const REQUEST_TIMEOUT = 8000; // 单次 HTTP 超时
const INSTANCE_ID_FILE = "/data/.agent-instance-id";
const MAX_RETRY_DELAY = 60 * 1000; // 注册重试退避上限

const API = {
  register: `${API_BASE}/agent/register`,
  heartbeat: `${API_BASE}/agent/heartbeat`,
  deregister: `${API_BASE}/agent/deregister`,
};

/* ---------------- 日志 ---------------- */
function log(...args) {
  console.log(new Date().toISOString(), "[agent]", ...args);
}
function warn(...args) {
  console.warn(new Date().toISOString(), "[agent]", ...args);
}

/* ---------------- 启动校验 ---------------- */
if (!ENABLED) {
  console.log("[agent] AGENT_ENABLED != true，自动注册未启用（保持原行为）");
  process.exit(0);
}
if (!API_BASE) {
  warn("缺少 AGENT_API_BASE，自动注册无法启动，请配置环境变量后重启容器");
  process.exit(0);
}
if (!TOKEN) {
  warn(
    "缺少 AGENT_TOKEN（预授权凭证），自动注册无法启动，请配置环境变量后重启容器",
  );
  process.exit(0);
}

/* ---------------- 实例 ID（优先环境变量，否则持久化自动生成） ---------------- */
function getInstanceId() {
  if (process.env.AGENT_INSTANCE_ID)
    return process.env.AGENT_INSTANCE_ID.trim();
  try {
    if (fs.existsSync(INSTANCE_ID_FILE)) {
      const v = fs.readFileSync(INSTANCE_ID_FILE, "utf8").trim();
      if (v) return v;
    }
    const id = `nr-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    fs.writeFileSync(INSTANCE_ID_FILE, id, { encoding: "utf8", mode: 0o600 });
    return id;
  } catch (e) {
    warn(`实例 ID 持久化失败（${e.message}），本次使用随机 ID`);
    return `nr-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  }
}

/* ---------------- 本机 IP ---------------- */
function getLocalIP() {
  const ifaces = os.networkInterfaces();
  for (const name of Object.keys(ifaces)) {
    for (const it of ifaces[name] || []) {
      if (it.family === "IPv4" && !it.internal) return it.address;
    }
  }
  return "";
}

/* ---------------- HTTP 封装（仅内置模块） ---------------- */
function request(method, url, body) {
  return new Promise((resolve, reject) => {
    let u;
    try {
      u = new URL(url);
    } catch (e) {
      return reject(new Error(`无效的 URL: ${url}`));
    }
    const lib = u.protocol === "https:" ? https : http;
    const payload = body === undefined ? null : JSON.stringify(body);
    const req = lib.request(
      {
        method,
        hostname: u.hostname,
        port: u.port || (u.protocol === "https:" ? 443 : 80),
        path: u.pathname + u.search,
        timeout: REQUEST_TIMEOUT,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${TOKEN}`,
          "User-Agent": "node-red-agent/1.0",
          ...(payload !== null
            ? { "Content-Length": Buffer.byteLength(payload) }
            : {}),
        },
      },
      (res) => {
        let data = "";
        res.setEncoding("utf8");
        res.on("data", (c) => (data += c));
        res.on("end", () => resolve({ status: res.statusCode, body: data }));
      },
    );
    req.on("timeout", () => req.destroy(new Error("请求超时")));
    req.on("error", reject);
    if (payload !== null) req.write(payload);
    req.end();
  });
}

async function call(method, pathKey, body) {
  try {
    const res = await request(method, API[pathKey], body);
    let json = null;
    try {
      json = JSON.parse(res.body);
    } catch (e) {
      /* 非 JSON 响应 */
    }
    const ok = res.status >= 200 && res.status < 300 && json && json.code === 0;
    return { ok, status: res.status, json };
  } catch (e) {
    return { ok: false, status: 0, error: e.message };
  }
}

/* ---------------- 注册 / 心跳 / 注销 ---------------- */
async function doRegister() {
  const body = {
    instanceId: getInstanceId(),
    name: os.hostname(),
    ip: getLocalIP(),
    platform: process.platform,
    arch: process.arch,
    nodeVersion: process.version,
    nodeRedVersion: process.env.NODE_RED_VERSION || "unknown",
    startTime: new Date().toISOString(),
  };
  const r = await call("POST", "register", body);
  if (r.ok) {
    log(
      "注册成功",
      JSON.stringify({
        instanceId: body.instanceId,
        name: body.name,
        ip: body.ip,
      }),
    );
  } else {
    warn(
      "注册失败",
      JSON.stringify({ status: r.status, error: r.error, resp: r.json }),
    );
  }
  return r;
}

async function doHeartbeat() {
  const instanceId = getInstanceId();
  const r = await call("POST", "heartbeat", { instanceId });
  if (r.ok) return { needReRegister: false };
  // 401/403：凭证失效；404/4001：实例已被注销 —— 都需重新注册
  const needReRegister =
    r.status === 401 || r.status === 404 || (r.json && r.json.code === 4001);
  if (needReRegister) {
    warn(
      "心跳返回实例不存在/凭证失效，准备重新注册",
      JSON.stringify({ status: r.status, resp: r.json }),
    );
  } else {
    warn(
      "心跳失败",
      JSON.stringify({ status: r.status, error: r.error, resp: r.json }),
    );
  }
  return { needReRegister };
}

async function doDeregister() {
  const instanceId = getInstanceId();
  const r = await call("POST", "deregister", { instanceId });
  if (r.ok) {
    log("注销成功", instanceId);
  } else {
    warn(
      "注销失败",
      JSON.stringify({ status: r.status, error: r.error, resp: r.json }),
    );
  }
}

/* ---------------- 主流程 ---------------- */
let stopping = false;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function run() {
  let registered = false;
  let retry = 0;
  while (!stopping) {
    if (!registered) {
      const r = await doRegister();
      if (r.ok) {
        registered = true;
        retry = 0;
        continue;
      }
      // 指数退避：5s → 10s → 20s → 40s → 60s（封顶）
      const delay = Math.min(MAX_RETRY_DELAY, 5000 * Math.pow(2, retry));
      retry = Math.min(retry + 1, 6);
      await sleep(delay);
      continue;
    }
    const r = await doHeartbeat();
    if (r.needReRegister) {
      registered = false;
      continue;
    }
    await sleep(HEARTBEAT_INTERVAL * 1000);
  }
}

function shutdown() {
  if (stopping) return;
  stopping = true;
  log("收到退出信号，尝试注销实例...");
  // 尽力而为：docker stop 只保证给 PID 1 发 SIGTERM，agent 可能直接被回收
  doDeregister().finally(() => {
    log("agent 退出");
    process.exit(0);
  });
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

log(
  `自动注册启动：instanceId=${getInstanceId()} 心跳间隔=${HEARTBEAT_INTERVAL}s API=${API_BASE}`,
);
run();
