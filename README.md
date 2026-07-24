# Spring Boot k6 Observability Lab

Java 21 Spring Boot 서버에 k6 부하를 가하고, Prometheus와 Grafana에서 애플리케이션·JVM·HikariCP·k6 지표를 함께 확인하는 재현 가능한 로컬 실험실입니다.

## 완료 기준

아래 한 명령이 Docker Compose로 Spring Boot, PostgreSQL, Prometheus, Grafana를 기동하고 k6 시나리오를 실행한 뒤, 네 가지 검증을 통과해야 합니다.

```bash
./scripts/run-experiment.sh
```

검증 항목은 다음과 같습니다.

1. Spring Boot Actuator health가 `UP`이다.
2. Prometheus에 `http_server_requests_seconds_count`가 수집된다.
3. k6 원격 쓰기 지표 `k6_http_reqs_total`이 존재한다.
4. 프로비저닝된 Grafana 대시보드 UID `spring-k6-overview`를 API로 조회할 수 있다.

성공 후 Grafana는 <http://localhost:13000/d/spring-k6-overview/spring-boot-k6-load-test-overview>에서 확인합니다. Prometheus는 <http://localhost:19090>에서 확인합니다. 초기 계정은 `admin` / `admin`이며, 로컬 실험 전용입니다.

## 빠른 시작

사전 조건은 Docker Desktop, Docker Compose, `curl`, `jq`입니다. Java나 k6를 호스트에 설치할 필요가 없습니다.

```bash
git clone https://github.com/joonfluence/spring-boot-k6-observability-lab.git
cd spring-boot-k6-observability-lab
./scripts/run-experiment.sh
```

기본값은 75ms의 모의 I/O 작업을 0→10→30 VU로 35초 동안 램프업/다운합니다. 다른 병목 가설은 환경 변수로 바꿉니다.

```bash
# PostgreSQL/HikariCP 대기열을 관찰하는 DB 지연 시나리오
WORKLOAD_MODE=db LATENCY_MS=150 ./scripts/run-experiment.sh

# CPU 작업량을 단계적으로 높이는 시나리오
WORKLOAD_MODE=cpu CPU_ITERATIONS=500000 ./scripts/run-experiment.sh
```

호스트 포트가 충돌하면 `APP_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT`를 지정해 바꿀 수 있습니다. 기본값은 각각 `18080`, `19090`, `13000`입니다.

실험 종료 후 컨테이너는 관측을 위해 계속 실행됩니다. 종료하려면 다음을 실행합니다.

```bash
./scripts/stop.sh
```

## 아키텍처

```text
k6 ──HTTP──> Spring Boot (Java 21, virtual threads)
 │                 │  └─ JDBC/HikariCP ──> PostgreSQL
 │                 └─ /actuator/prometheus
 │                              │
 └─ remote write ──> Prometheus ── datasource ──> Grafana
```

Prometheus는 애플리케이션의 Actuator 엔드포인트를 3초마다 scrape합니다. k6는 `experimental-prometheus-rw` 출력으로 테스트 중 생성한 시계열을 Prometheus remote-write receiver에 보냅니다.

## 대시보드에서 확인할 것

- k6 요청률, 실패율, HTTP p95/p99
- Spring Boot 서버 요청 p95
- JVM CPU 사용률과 라이브/데몬 스레드 수
- HikariCP 활성·대기·유휴·최대 커넥션 수

부하를 올릴 때 하나의 수치만 보고 한계를 선언하지 않습니다. 요청률 증가가 멈추는 시점에서 p95/p99, 오류율, CPU, 스레드, HikariCP pending을 함께 비교해 먼저 포화된 자원을 판별합니다.

## 문서

- [구현 및 실험 계획](docs/IMPLEMENTATION_PLAN.md)
- [구현 작업 기록](docs/IMPLEMENTATION_LOG.md)
- [실행 기록과 측정 결과](docs/EXPERIMENT_LOG.md)
- [기술 블로그용 글감](docs/BLOG_MATERIALS.md)
- [공식 참고자료](docs/REFERENCES.md)

## 디렉터리

```text
src/                         Spring Boot 부하 대상
k6/load-test.js              램프 부하 및 threshold
observability/prometheus/    scrape 설정
observability/grafana/       datasource와 대시보드 provisioning
scripts/run-experiment.sh    한 번에 기동·실행·검증
results/                     k6 실행 summary (git 제외)
docs/                        계획, 결과 기록, 블로그 소재
```
