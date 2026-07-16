#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDKMAN_INIT="${SDKMAN_DIR:-${HOME}/.sdkman}/bin/sdkman-init.sh"
PIDS=()

if [[ ! -f "$ROOT_DIR/.env" ]]; then
  echo "缺少 $ROOT_DIR/.env，请先复制 .env.example 并填写本地配置。" >&2
  exit 1
fi

# 五个主链进程共享根目录配置，避免 Java/Python 内部令牌不一致。
set -a
source "$ROOT_DIR/.env"
set +a

for command in uv pnpm; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "缺少命令：$command" >&2
    exit 1
  fi
done

if [[ ! -f "$SDKMAN_INIT" ]]; then
  echo "未找到 SDKMAN 初始化脚本：$SDKMAN_INIT" >&2
  exit 1
fi

start_process() {
  local label="$1"
  shift
  echo "[$label] 启动中"
  "$@" &
  PIDS+=("$!")
}

cleanup() {
  trap - EXIT INT TERM
  echo
  echo "正在停止 CareerAI 本地进程..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" >/dev/null 2>&1 || true
  done
  wait >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM

start_process "careerai-app" bash -lc \
  "source '$SDKMAN_INIT' && cd '$ROOT_DIR/backend' && sdk env >/dev/null && exec mvn -pl careerai-app spring-boot:run"
start_process "java-agent-bridge" bash -lc \
  "source '$SDKMAN_INIT' && cd '$ROOT_DIR/backend' && sdk env >/dev/null && exec mvn -pl agent-service spring-boot:run"
start_process "python-agent" bash -lc \
  "cd '$ROOT_DIR/agent-service' && exec uv run uvicorn careerai_agent.main:app --port 8000"
start_process "gateway" bash -lc \
  "source '$SDKMAN_INIT' && cd '$ROOT_DIR/backend' && sdk env >/dev/null && exec mvn -pl gateway-service spring-boot:run"
start_process "frontend" bash -lc \
  "cd '$ROOT_DIR/frontend' && exec pnpm dev"

echo
echo "CareerAI 主链正在启动："
echo "  Web       http://localhost:5173"
echo "  Gateway   http://localhost:8090"
echo "  Core      http://localhost:8080"
echo "  Agent     http://localhost:8000"
echo "另开终端执行 ./scripts/smoke-test.sh 检查就绪状态；按 Ctrl+C 停止。"

wait
