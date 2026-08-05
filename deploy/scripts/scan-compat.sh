#!/bin/bash
# ============================================================================
# Node-RED 容器数据一键迁移（3.1.9 → 5.0.4）
# ----------------------------------------------------------------------------
# 作用：
#   1. 备份：docker stop + docker cp 容器内 /data 到 workdir
#   2. 扫描：列 db 文件、节点类型、http request urls、flows_cred.json
#   3. 替换：http request url 前缀替换（可多条规则）
#   4. 部署：复制处理后的文件到 deploy-dir
#
# 用法：
#   scan-compat.sh --container <name> [--workdir <dir>] [--deploy-dir <dir>]
#                   [--replace OLD=NEW]... [--dry-run]
#
# 必填：无（无 --container 时只扫描本地目录）
# JSON 处理用内嵌 python3，不依赖 jq
# ============================================================================

set -e

# ---------- 默认值 ----------
WORKDIR="/deploy/old-node-red-data"
DEPLOY_DIR="/deploy/data"
REPLACE_RULES=()
DRY_RUN=false
CONTAINER="energy-nodered-hvac-0"

# ---------- 日志 ----------
ts() { date +%H:%M:%S; }
log() { local lv=$1; shift; echo -e "[$(ts)] [$lv] $*"; }

# ---------- 参数 ----------
while [[ $# -gt 0 ]]; do
  case $1 in
    --container)  CONTAINER="$2"; shift 2 ;;
    --workdir)    WORKDIR="$2"; shift 2 ;;
    --deploy-dir) DEPLOY_DIR="$2"; shift 2 ;;
    --replace)    REPLACE_RULES+=("$2"); shift 2 ;;
    --dry-run)    DRY_RUN=true; shift ;;
    -h|--help)    sed -n '2,15p' "$0"; exit 0 ;;
    *) log ERROR "未知参数: $1"; exit 1 ;;
  esac
done

# ---------- 步骤 1: 备份 ----------
if [ -n "$CONTAINER" ]; then
  log INFO "步骤 1/4: 备份容器 $CONTAINER → $WORKDIR"
  docker stop "$CONTAINER" 2>/dev/null || log WARN "容器可能已停, 继续"
  mkdir -p "$WORKDIR"
  docker cp "$CONTAINER:/data/." "$WORKDIR/"
  log OK "备份完成: $WORKDIR"
else
  log INFO "步骤 1/4: 跳过备份（未传 --container）, 直接读 $WORKDIR"
fi

FP="$WORKDIR/flows.json"
[ -f "$FP" ] || { log ERROR "未找到 $FP"; exit 1; }

# ---------- 步骤 2+3: 扫描 + 替换（python3 处理 JSON） ----------
log INFO "步骤 2/4: 扫描 $FP"
RULES_STR=$(IFS=';'; echo "${REPLACE_RULES[*]}")

RESULT=$(python3 - "$FP" "$WORKDIR" "$RULES_STR" <<'PY'
import json, sys, os, collections
fp, workdir, rules_str = sys.argv[1], sys.argv[2], sys.argv[3]
flows = json.load(open(fp, encoding='utf-8'))
nodes = [n for n in flows if isinstance(n, dict)]

DB = (".db", ".db-wal", ".db-shm")
dbs = sorted(f for f in os.listdir(workdir) if f.endswith(DB))
has_cred = os.path.isfile(os.path.join(workdir, "flows_cred.json"))

types = collections.Counter(n.get("type", "?") for n in nodes)
http_reqs = [n for n in nodes if n.get("type") == "http request"]

print(f"===NODES===|{len(nodes)}|{len(types)}")
for t, n in types.most_common():
    print(f"===TYPE===|{n}|{t}")
for n in http_reqs:
    print(f"===HTTP===|{n.get('method','GET')}|{n.get('url','')}|{n.get('name','')}")
print(f"===DBS===|{','.join(dbs)}")
print(f"===CRED===|{has_cred}")

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
    out = fp + ".new"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(flows, f, indent=2, ensure_ascii=False)
    print(f"===WRITTEN===|{out}|{os.path.getsize(out)}")
PY
)

# ---------- 解析输出 ----------
SIZE=$(stat -c %s "$FP" 2>/dev/null || stat -f %z "$FP")
DB_FILES=""
HAS_CRED="False"

while IFS='|' read -r tag rest; do
  case "$tag" in
    "===NODES===")
      log OK "读取 flows.json: 大小=${SIZE}B 节点=$(echo "$rest"|cut -d'|' -f1) 类型=$(echo "$rest"|cut -d'|' -f2)种"
      ;;
    "===TYPE===")
      log INFO "  $(echo "$rest"|cut -d'|' -f1)  $(echo "$rest"|cut -d'|' -f2)"
      ;;
    "===HTTP===")
      log INFO "  [$(echo "$rest"|cut -d'|' -f1)] $(echo "$rest"|cut -d'|' -f2)  # $(echo "$rest"|cut -d'|' -f3)"
      ;;
    "===DBS===")
      DB_FILES="$rest"
      log INFO "db 文件: $DB_FILES"
      ;;
    "===CRED===")
      HAS_CRED="$rest"
      [ "$HAS_CRED" = "True" ] && log WARN "flows_cred.json 存在, 必须随迁"
      ;;
    "===REPLACED===")
      log OK "  替换: $(echo "$rest"|cut -d'|' -f1) → $(echo "$rest"|cut -d'|' -f2)"
      ;;
    "===HITS===")
      log INFO "步骤 3/4: 替换 $(echo "$rest"|cut -d'|' -f1)/$(echo "$rest"|cut -d'|' -f2) 命中"
      ;;
    "===WRITTEN===")
      WP=$(echo "$rest"|cut -d'|' -f1)
      mv "$WP" "$FP"
      log OK "覆盖: $FP"
      ;;
  esac
done <<< "$RESULT"

[ "$DRY_RUN" = "true" ] && { log INFO "dry-run 模式, 退出"; exit 0; }

# ---------- 步骤 4: 部署 ----------
log INFO "步骤 4/4: 部署到 $DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"
FILES=("flows.json")
[ "$HAS_CRED" = "True" ] && FILES+=("flows_cred.json")
IFS=',' read -ra DB_ARR <<< "$DB_FILES"
FILES+=("${DB_ARR[@]}")

for f in "${FILES[@]}"; do
  [ -f "$WORKDIR/$f" ] || { log WARN "  跳过（源文件不存在）: $f"; continue; }
  cp -p "$WORKDIR/$f" "$DEPLOY_DIR/$f"
  SZ=$(stat -c %s "$DEPLOY_DIR/$f" 2>/dev/null || stat -f %z "$DEPLOY_DIR/$f")
  log OK "  复制: $f (${SZ}B)"
done
log OK "部署完成: $DEPLOY_DIR"
log INFO "下一步: cd /deploy && docker compose up -d"
log INFO "看日志: docker logs -f node-red-custom | grep 'Server now running'"
