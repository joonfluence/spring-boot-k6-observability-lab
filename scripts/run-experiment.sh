#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

compose=(docker compose)
app_port="${APP_PORT:-18080}"
prometheus_port="${PROMETHEUS_PORT:-19090}"
grafana_port="${GRAFANA_PORT:-13000}"

wait_for_url() {
  local name="$1"
  local url="$2"
  local max_attempts="$3"

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if curl --fail --silent --show-error "$url" >/dev/null; then
      printf '%s is ready: %s\n' "$name" "$url"
      return 0
    fi
    sleep 2
  done

  printf 'Timed out waiting for %s: %s\n' "$name" "$url" >&2
  "${compose[@]}" logs --tail=100
  return 1
}

mkdir -p results
"${compose[@]}" up --build --detach app postgres prometheus grafana
wait_for_url 'Spring Boot' "http://localhost:${app_port}/actuator/health" 60
wait_for_url 'Prometheus' "http://localhost:${prometheus_port}/-/ready" 30
wait_for_url 'Grafana' "http://localhost:${grafana_port}/api/health" 60

"${compose[@]}" run --rm \
  -e WORKLOAD_MODE="${WORKLOAD_MODE:-io}" \
  -e LATENCY_MS="${LATENCY_MS:-75}" \
  -e CPU_ITERATIONS="${CPU_ITERATIONS:-0}" \
  k6 run --out experimental-prometheus-rw /scripts/load-test.js

for ((attempt = 1; attempt <= 30; attempt++)); do
  k6_series="$(curl --fail --silent --get --data-urlencode 'query=count(k6_http_reqs_total)' "http://localhost:${prometheus_port}/api/v1/query" || true)"
  if printf '%s' "$k6_series" | jq -e '.status == "success" and (.data.result[0].value[1] | tonumber) > 0' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

./scripts/check-metrics.sh
printf '\nExperiment complete.\nGrafana: http://localhost:%s/d/spring-k6-overview/spring-boot-k6-load-test-overview (admin/admin)\nPrometheus: http://localhost:%s\nResult summary: %s/results/summary.json\n' "$grafana_port" "$prometheus_port" "$root_dir"
