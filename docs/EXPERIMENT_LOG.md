# 실행 기록과 측정 결과

## 재현 명령

```bash
./scripts/run-experiment.sh
```

다른 가설은 아래처럼 한 번에 하나의 변수군만 바꾸어 실행한다.

```bash
WORKLOAD_MODE=db LATENCY_MS=150 ./scripts/run-experiment.sh
WORKLOAD_MODE=cpu CPU_ITERATIONS=500000 ./scripts/run-experiment.sh
```

## 결과를 해석하는 순서

1. k6 오류율과 p95/p99가 threshold 내에 있었는지 확인한다.
2. 요청률이 VU 증가와 함께 비례해 증가했는지 확인한다.
3. Spring Boot p95와 k6 p95의 차이를 보고 서버 내부 지연과 클라이언트 관측 지연을 구분한다.
4. CPU, JVM 스레드, HikariCP pending/active/max를 함께 보고 최초 포화 자원을 결정한다.
5. 같은 조건을 3회 반복해 중앙값과 변동폭을 기록한 뒤에만 임계치를 주장한다.

## 실제 검증 기록

실행 명령: `./scripts/run-experiment.sh`  
조건: `WORKLOAD_MODE=io`, `LATENCY_MS=75`, 0→10→30→0 VU, 35초.  
이미지: Spring Boot 3.5.0 / Java 21, PostgreSQL 16, Prometheus 3.5.0, Grafana 12.1.0, k6 0.57.0.

| 항목 | 값 |
|---|---|
| 실행 일시 (KST) | 2026-07-25 01:15경 |
| Spring Boot health | `{"status":"UP"}` 확인 |
| Prometheus Spring 시계열 | `count(http_server_requests_seconds_count) = 3` 확인 |
| Prometheus k6 시계열 | `count(k6_http_reqs_total) = 1` 확인 |
| Grafana datasource | `Prometheus` (`http://prometheus:9090`) provisioned 확인 |
| Grafana dashboard | UID `spring-k6-overview`, 9개 패널, provisioned 확인 |
| Grafana를 통한 p95 query | `max(k6_http_req_duration_p95) = 0.0906528958 s` |
| k6 총 요청 수 | 2,683 |
| k6 요청률 | 76.53 req/s |
| k6 실패율 | 0% (threshold 통과) |
| k6 HTTP 평균 / p95 / p99 | 82.59ms / 90.65ms / 113.66ms (threshold 통과) |
| k6 HTTP 최대값 | 2,299.63ms |
| checks | 5,366 passed, 0 failed |
| Spring 서버 p95 | 88.97ms (`http_server_requests_seconds_bucket`, 2분 rate window) |
| HikariCP active | 0 (기본 I/O 시나리오는 DB를 호출하지 않음) |
| 관찰된 최초 병목 | 미관찰. 30 VU의 짧은 I/O smoke test에서 오류율과 p95 threshold가 모두 정상 범위였다. |

### 지표 조회 증거

Prometheus 직접 조회와 Grafana의 Prometheus datasource proxy가 같은 k6 p95 값 `0.0906528958`초를 반환했다. 따라서 대시보드가 단순히 존재하는 데 그치지 않고, Grafana 경유로 실제 Prometheus 시계열을 읽는 상태를 확인했다.

```bash
curl --user admin:admin --get \
  --data-urlencode 'query=max(k6_http_req_duration_p95)' \
  http://localhost:13000/api/datasources/proxy/uid/prometheus/api/v1/query
```

### 이번 결과의 해석

이 수치는 **30 VU·75ms 모의 I/O 조건의 smoke experiment 결과**다. 최대 처리량이나 Virtual Thread의 일반적 우위를 의미하지 않는다. 특히 최대값이 p95보다 크게 튀었으므로, 다음 단계에서는 2~5분 유지 구간과 3회 반복을 추가하고, `db` 모드에서 HikariCP pending을 관찰해야 한다.

## 결과 해석 주의점

- 이 저장소의 기본 램프는 재현성 확인용 짧은 smoke experiment다. "최대 트래픽" 결론에는 더 긴 유지 구간과 반복 실행이 필요하다.
- Docker Desktop의 CPU·메모리 할당량, 백그라운드 프로세스, 이미지 캐시 상태가 결과에 영향을 준다.
- localhost 결과는 운영 네트워크·TLS·실제 DB 데이터 분포를 포함하지 않는다. 이 결과를 운영 capacity planning 수치로 직접 일반화하지 않는다.
