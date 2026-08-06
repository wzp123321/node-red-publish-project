/**
 * Node-RED 配置文件
 *
 * 部署形态：Docker（官方镜像 nodered/node-red:5.0.4）
 * 认证设计：双入口独立认证
 *   - 编程页面 /        ：adminAuth，账户 admin / 3er4#ER$（完全权限）
 *   - 前端页面 /dashboard：httpNodeAuth，账户 user / Nts@1234（HTTP Basic）
 */
module.exports = {
  // 流程文件（相对 /data 用户目录）
  flowFile: "flows.json",

  // 凭据加密密钥（丢失将无法解密 flows_cred.json，请妥善保管）
  credentialSecret: "88d387cabb2fa6b4e25aab8f1b4c0a91cb75cbed9f8410fb",

  // 监听端口
  uiPort: 1890,

  // ---------- 编程页面（/）认证：admin 账户 ----------
  adminAuth: {
    type: "credentials",
    users: [
      {
        username: "admin",
        password:
          "$2b$10$iizVpw.GpHWoD.PrxHiDf.2YCw4WHKdNsoqcmgLm6xRUbp4rlOs0K",
        permissions: "*",
      },
    ],
  },

  // ---------- 前端页面（/dashboard）认证：user 账户 ----------
  httpNodeAuth: {
    user: "user",
    pass: "$2b$10$MLCxeGOhQidzbyOvaTeJuudHyUtipAazgL0BPsu7wA/.be0yacCle",
  },

  // 诊断界面可关闭（默认即可）
  diagnostics: {
    enabled: true,
    ui: true,
  },

  // 运行时状态
  runtimeState: {
    enabled: false,
    ui: false,
  },

  // 编辑器主题（保留默认）
  editorTheme: {
    projects: {
      enabled: false,
    },
  },

  // 函数节点等模块的加载选项（默认）
  functionGlobalContext: {
    // os:require('os'),
    // jfive:require("johnny-five"),
    // j5board:require("johnny-five").Board({repl:false})
  },

  // 允许 node-red 面板管理的节点模块
  externalModules: {
    autoInstall: true,
    autoUpdate: false,
  },

  // 默认每 30 秒保存一次（自动保存）
  autoSaveInterval: 30000,
};
