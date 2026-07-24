#!/usr/bin/env bash
set -euo pipefail

app_port="${APP_PORT:-18080}"
prometheus_port="${PROMETHEUS_PORT:-19090}"
grafana_port="${GRAFANA_PORT:-13000}"

query() {
  curl --fail --silent --get --data-urlencode "query=$1" "http://localhost:${prometheus_port}/api/v1/query"
}

app_health="$(curl --fail --silent "http://localhost:${app_port}/actuator/health")"
grafana_health="$(curl --fail --silent "http://localhost:${grafana_port}/api/health")"
app_metric="$(query 'count(http_server_requests_seconds_count)')"
k6_metric="$(query 'count(k6_http_reqs_total)')"
dashboard="$(curl --fail --silent --user admin:admin "http://localhost:${grafana_port}/api/dashboards/uid/spring-k6-overview")"

printf '%s' "$app_health" | jq -e '.status == "UP"' >/dev/null
printf '%s' "$grafana_health" | jq -e '.database == "ok"' >/dev/null
printf '%s' "$app_metric" | jq -e '.status == "success" and (.data.result[0].value[1] | tonumber) > 0' >/dev/null
printf '%s' "$k6_metric" | jq -e '.status == "success" and (.data.result[0].value[1] | tonumber) > 0' >/dev/null
printf '%s' "$dashboard" | jq -e '.dashboard.uid == "spring-k6-overview"' >/dev/null

printf 'Verification passed: Spring Boot, Prometheus application metrics, k6 metrics, and the provisioned Grafana dashboard are available.\n'
