#!/usr/bin/env bash

set -euo pipefail

check() {
  local label="$1"
  local url="$2"
  if curl --fail --silent --show-error --max-time 5 "$url" >/dev/null; then
    printf "%-20s OK  %s\n" "$label" "$url"
    return
  fi
  printf "%-20s FAIL %s\n" "$label" "$url" >&2
  return 1
}

echo "CareerAI 主链 smoke test"
check "careerai-app" "http://localhost:8080/actuator/health"
check "java-agent-bridge" "http://localhost:8082/actuator/health"
check "python-agent" "http://localhost:8000/health"
check "gateway-to-core" "http://localhost:8090/actuator/health"
check "frontend" "http://localhost:5173"
echo "主链服务全部就绪。"
