#!/usr/bin/env node
/**
 * Node-RED 实例自动注册 Agent（零依赖，仅用 Node 内置 http/https）
 * ---------------------------------------------------------------------------
 * 职责：
 *   1. 启动时调用平台注册接口
 *   2. 注册成功后定时上报心跳，平台据此维护 在线/离线 状态
 *
 * 环境变量：
 *   AGENT_API_BASE              平台 API 基础地址，如 http://192.168.1.10:8080（经 nginx 代理时含代理前缀，如 http://192.168.1.10:8899/zp-api）
 *   AGENT_HEARTBEAT_INTERVAL    心跳间隔（秒），默认 30
 *
 * 说明：脚本每次启动都触发注册（无可选开关）；缺失 AGENT_API_BASE 时 warn 并退出
 * ---------------------------------------------------------------------------
 */
"use strict";

const http = require("http");
const https = require("https");
const os = require("os");

/* ---------------- 配置 ---------------- */
const API_BASE =
  (process.env.AGENT_API_BASE || "").replace(/\/+$/, "") ||
  "http://nginxIn.tshlms.com:10130/energy/tenant-api";
const HEARTBEAT_INTERVAL = Math.max(
  1,
  Number(process.env.AGENT_HEARTBEAT_INTERVAL) || 60,
);
const REQUEST_TIMEOUT = 8000; // 单次 HTTP 超时
// Node-RED 监听端口：与 node-red 自身规则一致，读 PORT 环境变量（docker run -e PORT 注入，deploy.md 约定必传），缺省官方默认 1880
const NODE_RED_PORT = Number(process.env.PORT) || 1880;

const API = {
  register: `${API_BASE}/tenant/nodeRed/register`,
  heartbeat: `${API_BASE}/tenant/nodeRed/heartbeat`,
};

/* ---------------- 本地时间格式化（yyyy-MM-dd HH:mm:ss，与平台 @JsonFormat 对齐） ---------------- */
function fmtLocalTime(d) {
  const p = (n) => String(n).padStart(2, "0");
  return (
    `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
    `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  );
}

/* ---------------- 日志（本地时间戳，与容器 TZ=Asia/Shanghai 一致） ---------------- */
function log(...args) {
  console.log(`[${fmtLocalTime(new Date())}] [agent]`, ...args);
}
function warn(...args) {
  console.warn(`[${fmtLocalTime(new Date())}] [agent]`, ...args);
}

/* ---------------- 启动校验 ---------------- */
if (!API_BASE) {
  warn("缺少 AGENT_API_BASE，自动注册无法启动，请配置环境变量后重启容器");
  process.exit(0);
}

/* ---------------- 实例 ID：固定 nodered-${PORT} ---------------- */
function getInstanceId() {
  return `Node-RED-${NODE_RED_PORT}`;
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
    log("调用接口", method, API[pathKey], body ? JSON.stringify(body) : "");
    const res = await request(method, API[pathKey], body);
    console.log(res);
    let json = null;
    try {
      json = JSON.parse(res.body);
    } catch (e) {
      /* 非 JSON 响应 */
    }
    const ok = json.success;
    return { ok, status: res.status, json };
  } catch (e) {
    return { ok: false, status: 0, error: e.message };
  }
}

/* ---------------- 注册 / 心跳 ---------------- */
// 首次心跳成功打一条确认日志，后续成功心跳静默（防刷屏）；失败/重注册后仍会提示
let heartbeatConfirmed = false;

async function doRegister() {
  const body = {
    instanceId: getInstanceId(),
    ip: getLocalIP(),
    port: NODE_RED_PORT,
  };
  const r = await call("POST", "register", body);
  if (r.ok) {
    log(
      "注册成功",
      JSON.stringify({
        instanceId: body.instanceId,
        ip: body.ip,
        port: body.port,
      }),
    );
    return { ...r, ok: true };
  }
  if (r.json && r.json.errmsg === "相同IP和端口的实例已存在") {
    log("相同IP和端口的实例已存在");
    return { ...r, ok: true };
  }
  warn(
    "注册失败",
    JSON.stringify({ status: r.status, error: r.error, resp: r.json }),
  );
  return { ...r, ok: false };
}

async function doHeartbeat() {
  const instanceId = getInstanceId();
  const t0 = Date.now();
  const r = await call("POST", "heartbeat", { instanceId });
  const cost = Date.now() - t0;
  if (r.ok) {
    if (!heartbeatConfirmed) {
      heartbeatConfirmed = true;
      log(`心跳通道确认正常（耗时 ${cost}ms），后续成功心跳不再打印`);
    }
    return;
  }
  warn(
    "心跳失败",
    JSON.stringify({
      status: r.status,
      error: r.error,
      resp: r.json,
      cost: `${cost}ms`,
    }),
  );
}

/* ---------------- 主流程 ---------------- */
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function run() {
  // 启动注册：成功 或 提示"相同IP和端口的实例已存在" 才进入心跳；其他报错不执行心跳
  const regResult = await doRegister();
  if (!regResult.ok) {
    warn("注册失败，不进入心跳轮询，agent 退出");
    return;
  }
  // 心跳轮询
  while (true) {
    await doHeartbeat();
    await sleep(HEARTBEAT_INTERVAL * 1000);
  }
}

log(
  `自动注册启动：instanceId=${getInstanceId()} 心跳间隔=${HEARTBEAT_INTERVAL}s API=${API_BASE}`,
);
run();
