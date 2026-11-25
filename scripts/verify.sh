#!/usr/bin/env bash
# 等待服务就绪后执行全部端到端回放。用法：scripts/verify.sh [base_url]
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://127.0.0.1:8080}}"
export BASE_URL

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "等待 ${BASE_URL}/api/health ..."
for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 "${BASE_URL}/api/health" >/dev/null 2>&1; then
    echo "服务已就绪。"
    break
  fi
  sleep 2
done

PYTHON="${PYTHON:-python3}"

echo "======================================================================"
echo "执行基线全链路回放 scripts/replay.py"
echo "======================================================================"
"${PYTHON}" "${SCRIPT_DIR}/replay.py"

echo "======================================================================"
echo "执行拓扑修订双路径回放 scripts/replay_topology.py"
echo "======================================================================"
"${PYTHON}" "${SCRIPT_DIR}/replay_topology.py"
