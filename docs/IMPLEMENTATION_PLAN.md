# 구현 및 실험 계획

## 목적

이 실험은 "이론상 감지할 수 있는 병목"을 "현재 환경에서 몇 VU·몇 RPS부터 관찰되는가"로 바꾸는 데 목적이 있다. 단일 숫자의 최대 처리량을 선언하기보다, 부하 증가 중 가장 먼저 포화되는 자원과 그 근거 지표를 기록한다.

## 실험 가설과 관찰 지표

| 가설 | 자극 | 우선 관찰 지표 | 병목 판단 신호 |
|---|---|---|---|
| I/O 대기 | `WORKLOAD_MODE=io`, `LATENCY_MS` 증가 | k6 p95/p99, Spring 서버 p95, JVM thread | 지연은 증가하지만 CPU가 낮을 때 동시성·대기 모델 확인 |
| CPU 포화 | `WORKLOAD_MODE=cpu`, `CPU_ITERATIONS` 증가 | 요청률, CPU, p95/p99, 오류율 | CPU가 상한 근처인데 요청률이 더 늘지 않음 |
| DB 풀 포화 | `WORKLOAD_MODE=db`, `LATENCY_MS` 증가 | `hikaricp_connections_pending`, active/max, p95 | pending 증가 또는 active가 max에 고정됨 |
| 애플리케이션 한계 | VU 단계 증가 | 오류율, 응답 시간, `jvm_threads_live_threads` | threshold 초과 또는 오류율 상승 |

## 고정 조건

- 런타임: Java 21 호환 Spring Boot 컨테이너
- 부하 생성: Docker의 k6 이미지
- 지표 저장: Prometheus의 scrape와 remote write receiver
- 시각화: 프로비저닝된 Grafana 대시보드
- 기본 시나리오: 0→10 VU(10초)→30 VU(15초)→0 VU(10초), 각 VU는 100ms think time
- 기본 성공 기준: HTTP 실패율 < 2%, k6 HTTP p95 < 750ms

## 설계 결정

1. **프로세스를 모두 Docker Compose로 묶는다.** 호스트별 Java/k6 설치 차이를 줄이고 실행 순서를 스크립트 하나에 고정한다.
2. **애플리케이션 지표와 부하 생성 지표를 같은 Prometheus에 저장한다.** k6 summary만 보면 자원 포화 원인을 알 수 없고, 서버 지표만 보면 실제 클라이언트 체감 지연을 알 수 없기 때문이다.
3. **Grafana 대시보드를 JSON provisioning으로 버전 관리한다.** 화면에서 수동 생성한 대시보드가 로컬에만 남는 문제를 피한다.
4. **PostgreSQL과 HikariCP를 포함한다.** DB 작업 모드에서는 커넥션 풀 대기열을 확인해 애플리케이션 지연과 DB 자원 고갈을 구분한다.
5. **실행 성공을 API로 검증한다.** 브라우저를 열었다는 사실이 아니라 health, Prometheus 시계열, Grafana dashboard UID를 기계적으로 확인한다.

## 실행 단계

1. `./scripts/run-experiment.sh`가 Compose 서비스를 기동한다.
2. Spring Boot, Prometheus, Grafana readiness를 HTTP로 기다린다.
3. k6가 HTTP 요청과 Prometheus remote write를 동시에 수행한다.
4. 스크립트가 애플리케이션·k6 시계열과 Grafana 대시보드 provisioning을 검증한다.
5. `results/summary.json`, Prometheus/Grafana 화면, 컨테이너 로그를 이용해 결과를 기록한다.

## 완료 판정

- [x] `docker compose config`가 유효하다.
- [x] Java 단위 테스트를 RED→GREEN 순서로 통과한다.
- [x] 한 번의 실행 스크립트가 서비스 기동·k6·검증을 모두 완료한다.
- [x] Prometheus에서 Spring Boot와 k6 시계열을 조회한다.
- [x] Grafana에서 provisioned 대시보드가 열리고 데이터가 나타난다.
- [x] 실제 실행 수치와 환경 정보를 `EXPERIMENT_LOG.md`에 기록한다.
- [ ] 원격 GitHub 저장소에 커밋과 push를 완료한다.
