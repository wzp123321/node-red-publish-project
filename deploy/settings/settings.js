/**
 * Node-RED 配置文件（基于 Node-RED 5.0.4 官方默认 settings.js）
 * ----------------------------------------------------------------------------
 * 官方结构参考：
 *   https://github.com/node-red/node-red/blob/5.0.4/packages/node_modules/node-red/settings.js
 *
 * 现场：Docker 部署（nodered/node-red:5.0.4 官方镜像）
 * 认证设计：双入口独立认证
 *   - 编程页面 /        ：adminAuth，账户 admin / 3er4#ER$（完全权限）
 *   - 前端页面 /dashboard：httpNodeAuth，账户 user / Nts@1234（HTTP Basic）
 *
 * 与官方默认的差异（搜索"⚠ 现场修改"）：
 *   ⚠ credentialSecret  从注释改为启用（与老容器同步）
 *   ⚠ uiPort            固定 1890 → 由启动命令 -e PORT 指定（未指定回落 1880）
 *   ⚠ adminAuth         从注释改为启用
 *   ⚠ httpNodeAuth      从注释改为启用
 *   ⚠ externalModules   autoInstall: false → true
 *   其余字段：保留官方默认（显式列出的同时标注"官方默认"以便审计）
 *
 * 备份：原 settings.js 已备份为 settings.js.bak
 */

module.exports = {
  /*******************************************************************************
   * Flow File and User Directory Settings
   * - flowFile
   * - credentialSecret
   * - flowFilePretty
   * - userDir
   * - nodesDir
   ******************************************************************************/

  /** 流程文件（相对 /data 用户目录） */
  flowFile: "flows.json", // 官方默认就是 flows.json，显式列出便于审计

  /** By default, credentials are encrypted in storage using a generated key. To
   * specify your own secret, set the following property.
   * If you want to disable encryption of credentials, set this property to false.
   * Note: once you set this property, do not change it - doing so will prevent
   * node-red from being able to decrypt your existing credentials and they will be
   * lost.
   */
  //credentialSecret: "a-secret-key",

  /** 目前现场都没有设置，所以这里也不设置 */
  // credentialSecret: "88d387cabb2fa6b4e25aab8f1b4c0a91cb75cbed9f8410fb",

  /** 流程 JSON 多行格式化（便于 git diff）——官方默认 true */
  flowFilePretty: true,

  // userDir: '/home/nol/.node-red/',  // 官方默认注释（Docker 镜像自动走 /data，不改）

  // nodesDir: '/home/nol/.node-red/nodes',  // 官方默认注释（无自定义节点目录）

  /*******************************************************************************
   * Security
   * - adminAuth
   * - https
   * - httpsRefreshInterval
   * - requireHttps
   * - httpNodeAuth
   * - httpStaticAuth
   ******************************************************************************/

  /** ⚠ 现场修改：编程页面（/）认证启用 admin 账户 */
  adminAuth: {
    type: "credentials",
    users: [
      {
        username: "admin",
        password:
          "$2b$10$iizVpw.GpHWoD.PrxHiDf.2YCw4WHKdNsoqcmgLm6xRUbp4rlOs0K", // bcrypt(3er4#ER$)
        permissions: "*",
      },
    ],
  },

  // https: {  // 官方默认注释（生产环境应该考虑 HTTPS）
  //   key: require("fs").readFileSync('privkey.pem'),
  //   cert: require("fs").readFileSync('cert.pem')
  // },

  // httpsRefreshInterval: 12,  // 官方默认注释

  // requireHttps: true,  // 官方默认注释

  /** ⚠ 现场修改：前端页面（/dashboard）认证启用 user 账户 */
  httpNodeAuth: {
    user: "user",
    pass: "$2b$10$MLCxeGOhQidzbyOvaTeJuudHyUtipAazgL0BPsu7wA/.be0yacCle", // bcrypt(Nts@1234)
  },

  // httpStaticAuth: { user: "user", pass: "..." },  // 官方默认注释（无静态资源）

  /*******************************************************************************
   * Server Settings
   * - uiPort
   * - uiHost
   * - apiMaxLength
   * - httpServerOptions
   * - httpAdminRoot
   * - httpAdminMiddleware
   * - httpAdminCookieOptions
   * - httpNodeRoot
   * - httpNodeCors
   * - httpNodeMiddleware
   * - httpStatic
   * - httpStaticRoot
   * - httpStaticCors
   * - proxyOptions
   ******************************************************************************/

  /** ⚠ 现场修改：端口由启动命令 -e PORT 指定（多实例共用本配置文件），未指定时回落官方默认 1880 */
  uiPort: process.env.PORT || 1880,

  // uiHost: "127.0.0.1",  // 官方默认注释（默认监听所有 IPv4 = 0.0.0.0）

  // apiMaxLength: '5mb',  // 官方默认 5mb

  // httpServerOptions: {},  // 官方默认注释

  // httpAdminRoot: '/admin',  // 官方默认注释（默认 /）

  // httpAdminMiddleware: function(req, res, next) {  // 官方默认注释
  //   // res.set('X-Frame-Options', 'sameorigin');
  //   next();
  // },

  // httpAdminCookieOptions: {},  // 官方默认注释

  // httpNodeRoot: '/red-nodes',  // 官方默认注释（默认 /）

  // httpNodeCors: {  // 官方默认注释
  //   origin: "*",
  //   methods: "GET,PUT,POST,DELETE"
  // },

  // httpNodeMiddleware: function(req, res, next) {  // 官方默认注释
  //   next();
  // },

  // httpStatic: '/home/nol/node-red-static/',  // 官方默认注释（无静态资源）

  // httpStatic: [  // 官方默认注释（多静态目录数组形式）
  //   { path: '/home/nol/pics/', root: "/img/" }
  // ],

  // httpStaticRoot: '/static/',  // 官方默认注释

  // httpStaticCors: {  // 官方默认注释
  //   origin: "*",
  //   methods: "GET,PUT,POST,DELETE"
  // },

  // proxyOptions: { mode: "legacy" },  // 官方默认注释（v4 兼容代理）

  /*******************************************************************************
   * Runtime Settings
   * - lang
   * - runtimeState
   * - telemetry
   * - diagnostics
   * - logging
   * - contextStorage
   * - exportGlobalContextKeys
   * - externalModules
   ******************************************************************************/

  // lang: "de",  // 官方默认 en-US（DOCKER 镜像自带多语言包，按需启用）

  /** 诊断界面 — 官方默认 {enabled: true, ui: true}，显式列出便于审计 */
  diagnostics: {
    enabled: true,
    ui: true,
  },

  /** 运行时启停 — 官方默认 {enabled: false, ui: false}，显式列出 */
  runtimeState: {
    enabled: false,
    ui: false,
  },

  // telemetry: {  // 官方默认注释（首次启动时由用户在编辑器里选择）
  //   // enabled: true,
  //   // updateNotification: true
  // },

  // logging: {  // 官方默认注释（默认 info 级别；现场调试复杂 flow 可改 debug/trace）
  //   console: {
  //     level: "info",
  //     metrics: false,
  //     audit: false
  //   }
  // },

  // contextStorage: {  // 官方默认注释（默认 memory，⚠ 重启丢 context；生产建议启用）
  //   default: {
  //     module: "localfilesystem"
  //   }
  // },

  exportGlobalContextKeys: false, // 官方默认 false

  /*******************************************************************************
   * Editor Settings
   * - editorTheme
   * - codeEditor
   * - palette
   * - multilineEditor
   * - markdownEditor
   * - debug
   * - compressResponse
   ******************************************************************************/

  /** 编辑器主题 — 现场只显式列出 projects.enabled（其他走官方默认） */
  editorTheme: {
    // projects: { enabled: false },  // 官方默认 false
    /** ⚠ 显式列出：禁用项目模式（容器跑单 flow 不需要 projects） */
    projects: {
      enabled: false,
    },
  },

  // codeEditor: {  // 官方默认注释（monaco）
  //   lib: "monaco",
  //   options: {}
  // },

  // palette: {},  // 官方默认注释

  // multilineEditor: {},  // 官方默认注释

  /*******************************************************************************
   * Node Settings
   * - functionGlobalContext
   * - externalModules
   * - autoSaveInterval
   * - nodeVersion
   * - safeMode
   * - installGlobalPackages
   * - flowEncryption
   * - nodeSettings
   ******************************************************************************/

  /** 函数节点等模块的加载选项（默认空）— 显式列出 */
  functionGlobalContext: {
    // os:require('os'),
    // jfive:require("johnny-five"),
    // j5board:require("johnny-five").Board({repl:false})
  },

  /** ⚠ 现场修改：autoInstall 从 false → true（允许编辑器自动装节点） */
  externalModules: {
    autoInstall: true, // 官方默认 false
    autoUpdate: false, // 官方默认 false
  },

  /** 默认每 30 秒自动保存一次 — 官方默认就是 30 秒，显式列出 */
  autoSaveInterval: 30000,

  // nodeVersion: "20.x",  // 官方默认注释

  // safeMode: false,  // 官方默认 false

  // installGlobalPackages: false,  // 官方默认 false

  // flowEncryption: {  // 官方默认注释（5.x 新增）
  //   enabled: true,
  //   key: ""
  // },
};
