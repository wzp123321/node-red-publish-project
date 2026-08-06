const express = require("express");
const path = require("path");
const MigrateDashboard = require("@flowfuse/node-red-dashboard-2-migration");

const app = express();
const port = process.env.PORT || 3000;

// 官方库支持转换的 v1 节点类型（对应 transformers/map.json 的键）
const SUPPORTED_UI_NODES = new Set([
  "ui_tab",
  "ui_group",
  "ui_button",
  "ui_dropdown",
  "ui_form",
  "ui_slider",
  "ui_switch",
  "ui_text",
  "ui_text_input",
  "ui_numeric",
]);

// 参考 deploy/scripts/scan-compat.sh 的 ui_chart → ui-chart 字段改写逻辑
function migrateUiChart(node) {
  const chartType = node.chartType || "line";
  const colors = Array.isArray(node.colors) ? node.colors : [];
  const rmOld = parseInt(node.removeOlder || 0, 10) || 0;
  const rmUnit = parseInt(node.removeOlderUnit || 1, 10) || 1;
  const pointLimit = rmOld && rmUnit ? rmOld * rmUnit : 0;

  // series 占位：每个颜色 → 一个 series
  const series = colors.map((color, i) => ({
    label: `Series ${i + 1}`,
    color,
  }));

  node.type = "ui-chart";
  node.property = {
    type: chartType,
    x: "timestamp",
    y: "value",
    colors,
  };
  if (series.length) {
    node.series = series;
  }
  if (pointLimit) {
    node.pointLimit = pointLimit;
  }
  // 兼容 alias（编辑器里有遗留引用）
  node.chartType = chartType;
  // 与官方工具保持一致：转换后的 ui-chart 也标记为禁用（d:true），
  // 需在 Node-RED 中人工确认启用，避免未经验证的数据格式直接生效
  node.d = true;

  return node;
}

// 替换 http request 节点 url 前缀（startswith 匹配，与 scan-compat.sh 一致）
function replaceHttpUrls(nodes, oldPrefix, newPrefix) {
  let hit = 0;
  if (!oldPrefix) {
    return hit;
  }
  nodes.forEach((node) => {
    if (
      node.type === "http request" &&
      typeof node.url === "string" &&
      node.url.startsWith(oldPrefix)
    ) {
      node.url = newPrefix + node.url.slice(oldPrefix.length);
      hit++;
    }
  });
  return hit;
}

// 分类统计无法处理的节点（migrate 后 type 仍以 ui_ 开头的 v1 残留）
function collectUnhandled(nodes) {
  const unhandled = {};
  nodes.forEach((node) => {
    if (
      typeof node.type === "string" &&
      node.type.startsWith("ui_") &&
      !SUPPORTED_UI_NODES.has(node.type)
    ) {
      if (!unhandled[node.type]) {
        unhandled[node.type] = [];
      }
      unhandled[node.type].push({
        id: node.id,
        name: node.name || node.label || "",
      });
    }
  });
  return unhandled;
}

// Middleware
app.use(express.json({ limit: "10mb" }));
app.use(express.static("public"));

// Conversion endpoint
app.post("/api/convert", (req, res) => {
  try {
    // 兼容两种请求格式：
    // 1. 原格式：数组 或 { nodes: [...] }
    // 2. 扩展格式：{ flow: <原数据>, replaceHttpUrl: { enabled, oldPrefix, newPrefix } }
    let flowData = req.body;
    let replaceHttpUrl = null;

    if (
      req.body &&
      typeof req.body === "object" &&
      !Array.isArray(req.body) &&
      req.body.flow
    ) {
      replaceHttpUrl = req.body.replaceHttpUrl || null;
      flowData = req.body.flow;
    }

    if (!flowData) {
      return res.status(400).json({
        error: "No JSON data provided",
      });
    }

    // Handle different input formats:
    // 1. Array of nodes (direct flow array)
    // 2. Object with 'nodes' property (Node-RED export format)
    let flowArray;
    if (Array.isArray(flowData)) {
      flowArray = flowData;
    } else if (flowData.nodes && Array.isArray(flowData.nodes)) {
      flowArray = flowData.nodes;
    } else {
      return res.status(400).json({
        error:
          'Invalid format. Expected an array of nodes or an object with a "nodes" property.',
      });
    }

    // Migrate Dashboard v1 to v2
    const dashboardV2Json = MigrateDashboard.migrate(flowArray);

    // ---- 扩展后处理 ----
    let httpReplaced = 0;
    let httpTotal = 0;
    let chartsMigrated = 0;

    // ui_chart → ui-chart 字段改写（官方库不支持，参考 scan-compat.sh）
    dashboardV2Json.forEach((node) => {
      if (node.type === "ui_chart") {
        migrateUiChart(node);
        chartsMigrated++;
      }
    });

    // http request url 前缀替换
    if (replaceHttpUrl && replaceHttpUrl.enabled) {
      const oldPrefix = replaceHttpUrl.oldPrefix || "";
      const newPrefix = replaceHttpUrl.newPrefix || "";
      httpTotal = dashboardV2Json.filter(
        (node) => node.type === "http request",
      ).length;
      httpReplaced = replaceHttpUrls(dashboardV2Json, oldPrefix, newPrefix);
    }

    // 分类统计无法处理的节点
    const unhandled = collectUnhandled(dashboardV2Json);

    res.json({
      success: true,
      data: dashboardV2Json,
      meta: {
        httpReplaced,
        httpTotal,
        chartsMigrated,
        unhandled,
      },
    });
  } catch (error) {
    console.error("Conversion error:", error);
    res.status(500).json({
      error: "Error converting the JSON",
      message: error.message,
    });
  }
});

// Health check endpoint
app.get("/api/health", (req, res) => {
  res.json({ status: "ok" });
});

// Start server
app.listen(port, () => {
  console.log(`Server running at http://localhost:${port}`);
  console.log(`Open your browser and navigate to http://localhost:${port}`);
});
