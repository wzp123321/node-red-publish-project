#!/bin/bash
# ============================================================================
# Node-RED flow 兼容性处理（Dashboard v2 → v3，http url 替换）
# ----------------------------------------------------------------------------
# 作用：
#   1. 扫描：列 http request urls + ui_chart 节点 + v1 残留节点
#   2. ui_chart 字段改写：v1 ui_chart → v3 ui-chart（converter 改不了的部分）
#   3. http request url 前缀替换（可多条规则）
#   4. 报告：统计接口调用 + 字段改写情况
#
# 用法：
#   scan-compat.sh [--workdir <dir>] [--input <file>] [--replace OLD=NEW]... [--dry-run]
#
# 必填：无
# 输入：workdir/flows.v2.json（converter 跑完的产物）
# 备份与部署：手动操作
#   - 备份：docker stop + docker cp <容器>:/data/. <workdir>/
#   - 部署：cp <workdir>/flows.json /deploy/data/
# JSON 处理用内嵌 python3，不依赖 jq
# ============================================================================

set -e

# ---------- 默认值 ----------
WORKDIR="/deploy/old-node-red-data"
INPUT="flows.v2.json"          # converter 跑完的产物
REPLACE_RULES=()
DRY_RUN=false

# ---------- 日志 ----------
ts() { date +%H:%M:%S; }
log() { local lv=$1; shift; echo -e "[$(ts)] [$lv] $*"; }

# ---------- 参数 ----------
while [[ $# -gt 0 ]]; do
  case $1 in
    --workdir)    WORKDIR="$2"; shift 2 ;;
    --input)      INPUT="$2"; shift 2 ;;
    --replace)    REPLACE_RULES+=("$2"); shift 2 ;;
    --dry-run)    DRY_RUN=true; shift ;;
    -h|--help)    sed -n '2,15p' "$0"; exit 0 ;;
    *) log ERROR "未知参数: $1"; exit 1 ;;
  esac
done

FP="$WORKDIR/$INPUT"
[ -f "$FP" ] || { log ERROR "未找到 $FP（确认 converter 已跑过）"; exit 1; }

# ---------- 步骤 1+2+3: 扫描 + ui_chart 改字段 + http url 替换 ----------
log INFO "读取 $FP"
RULES_STR=$(IFS=';'; echo "${REPLACE_RULES[*]}")

RESULT=$(python3 - "$FP" "$RULES_STR" "$DRY_RUN" <<'PY'
import json, sys, os, collections
fp, rules_str, dry_run = sys.argv[1], sys.argv[2], sys.argv[3].lower() == "true"
flows = json.load(open(fp, encoding="utf-8"))
nodes = [n for n in flows if isinstance(n, dict)]

# ===== 1. 类型统计 =====
types = collections.Counter(n.get("type", "?") for n in nodes)
print(f"===NODES===|{len(nodes)}|{len(types)}")
for t, n in types.most_common():
    print(f"===TYPE===|{n}|{t}")

# ===== 2. http request urls（统计 + 替换） =====
http_reqs = [n for n in nodes if n.get("type") == "http request"]
for n in http_reqs:
    print(f"===HTTP===|{n.get('method','GET')}|{n.get('url','')}|{n.get('name','')}")

if rules_str:
    rules = [tuple(r.split("=", 1)) for r in rules_str.split(";") if r]
    hit = 0
    for n in http_reqs:
        url = n.get("url", "")
        for old, new in rules:
            if url.startswith(old):
                new_url = new + url[len(old):]
                print(f"===REPLACED===|{url}|{new_url}")
                n["url"] = new_url
                hit += 1
                break
    print(f"===HITS===|{hit}|{len(http_reqs)}")

# ===== 3. ui_chart → ui-chart 字段改写 =====
ui_charts = [n for n in nodes if n.get("type") == "ui_chart"]
print(f"===CHARTS===|{len(ui_charts)}")

for n in ui_charts:
    chart_type = n.get("chartType", "line")
    colors     = n.get("colors", []) or []
    rm_old     = int(n.get("removeOlder") or 0)
    rm_unit    = int(n.get("removeOlderUnit") or 1)
    point_limit = rm_old * rm_unit if rm_old and rm_unit else 0

    # series 占位：每个颜色 → 一个 series
    series = [{"label": f"Series {i+1}", "color": c} for i, c in enumerate(colors)]

    n["type"] = "ui-chart"
    n["property"] = {
        "type":   chart_type,
        "x":      "timestamp",
        "y":      "value",
        "colors": colors,
    }
    if series:
        n["series"] = series
    if point_limit:
        n["pointLimit"] = point_limit
    # 兼容 alias（编辑器里有遗留引用）
    n["chartType"] = chart_type

    print(f"===CHART_REWRITE===|{n.get('id','')}|{n.get('label','')}|{chart_type}|{len(series)} series")

# ===== 4. v1 残留节点（脚本不处理，仅统计警告） =====
v1_residual = collections.Counter()
for n in nodes:
    t = n.get("type", "")
    if t in ("ui_base", "ui_spacer", "ui_template", "ui_date_picker"):
        v1_residual[t] += 1
print(f"===V1_RESIDUAL===|{dict(v1_residual)}")

# ===== 写回 =====
if not dry_run:
    out = fp + ".new"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(flows, f, indent=2, ensure_ascii=False)
    print(f"===WRITTEN===|{out}|{os.path.getsize(out)}")
PY
)

# ---------- 解析输出 ----------
SIZE=$(stat -c %s "$FP" 2>/dev/null || stat -f %z "$FP")

while IFS='|' read -r tag rest; do
  case "$tag" in
    "===NODES===")
      log OK "flows.json: 大小=${SIZE}B 节点=$(echo "$rest"|cut -d'|' -f1) 类型=$(echo "$rest"|cut -d'|' -f2)种"
      ;;
    "===TYPE===")
      log INFO "  $(echo "$rest"|cut -d'|' -f1)  $(echo "$rest"|cut -d'|' -f2)"
      ;;
    "===HTTP===")
      log INFO "  [$(echo "$rest"|cut -d'|' -f1)] $(echo "$rest"|cut -d'|' -f2)  # $(echo "$rest"|cut -d'|' -f3)"
      ;;
    "===REPLACED===")
      log OK "  替换: $(echo "$rest"|cut -d'|' -f1) → $(echo "$rest"|cut -d'|' -f2)"
      ;;
    "===HITS===")
      log OK "http url 替换: $(echo "$rest"|cut -d'|' -f1)/$(echo "$rest"|cut -d'|' -f2) 命中"
      ;;
    "===CHARTS===")
      [ "$rest" != "0" ] && log INFO "ui_chart 待改字段: $rest 个"
      ;;
    "===CHART_REWRITE===")
      log OK "  ui_chart → ui-chart: $(echo "$rest"|cut -d'|' -f2)  type=$(echo "$rest"|cut -d'|' -f3), $(echo "$rest"|cut -d'|' -f4)"
      ;;
    "===V1_RESIDUAL===")
      if [ "$rest" != "{}" ] && [ -n "$rest" ]; then
        log WARN "v1 残留节点（脚本不处理，请人工确认）: $rest"
      else
        log OK "无 v1 残留节点"
      fi
      ;;
    "===WRITTEN===")
      WP=$(echo "$rest"|cut -d'|' -f1)
      mv "$WP" "$FP"
      log OK "覆盖: $FP"
      ;;
  esac
done <<< "$RESULT"

[ "$DRY_RUN" = "true" ] && { log INFO "dry-run 模式, 退出（未写文件）"; exit 0; }

# ---------- 步骤 4: 报告（提示手动部署） ----------
log OK "处理完成: $FP"
log INFO "下一步（手动操作）："
log INFO "  1) cp $FP /deploy/data/flows.json"
log INFO "  2) cd /deploy && docker compose up -d"
log INFO "  3) docker logs -f node-red-custom | grep 'Server now running'"
