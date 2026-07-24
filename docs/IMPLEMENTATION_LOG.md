# 구현 작업 기록

## 1. 완료 기준을 실행 가능한 계약으로 변환

원래 목표를 "설정 파일이 있다"가 아니라 다음 한 명령이 검증까지 끝내는 것으로 정의했다.

```bash
./scripts/run-experiment.sh
```

이 스크립트는 Spring Boot·PostgreSQL·Prometheus·Grafana를 시작하고, readiness를 기다린 다음 k6를 실행한다. 마지막에는 health, Prometheus 애플리케이션 시계열, Prometheus k6 시계열, Grafana dashboard UID를 API로 검사한다.

## 2. TDD 구현 기록

Spring Boot 부하 대상은 Java 21 가상 스레드 위에서 `cpu`, `io`, `db` 작업을 수행하도록 만들었다. 다음 RED 실패를 먼저 확인한 뒤 최소 구현을 추가했다.

| RED 단계 | GREEN 구현 | 검증 |
|---|---|---|
| `WorkloadService`와 결과 모델이 없어 컴파일 실패 | 결정적 CPU 작업 서비스와 결과 record | 서비스 단위 테스트 통과 |
| 컨트롤러가 없어 HTTP 계약 컴파일 실패 | `GET /api/work` 컨트롤러 | MockMvc 테스트 통과 |
| 계약 변경 전 `/api/work`가 404 | `mode`, `latencyMs`, `cpuIterations` 파라미터 계약 | lowercase `mode` JSON 응답 확인 |
| DB mode 및 `JdbcTemplate` 생성자가 없음 | 범위가 제한된 `SELECT 1 FROM pg_sleep(?)` | DB 모드 단위 테스트 통과 |
| workload meter가 없음 | Micrometer Counter/Timer 등록 | meter 존재 테스트 통과 |
| `latencyMs=5001`을 허용 | Bean Validation 상한 5,000ms | 요청 검증 테스트 통과 |

최종 Java 21 실행으로 JUnit 7개가 실패 없이 통과했다.

```bash
JAVA_HOME=/Users/yijun/.sdkman/candidates/java/current ./mvnw test
```

## 3. 관측 파이프라인 구현

```text
k6 --HTTP--> Spring Boot --JDBC/HikariCP--> PostgreSQL
 |                    |
 |                    +-- /actuator/prometheus --scrape--> Prometheus
 +-- experimental-prometheus-rw -------------------------> Prometheus
                                                        |
                                                     Grafana
```

- Spring Boot는 Actuator Prometheus endpoint와 HTTP histogram, HikariCP/JVM/workload metric을 노출한다.
- Prometheus는 애플리케이션을 3초 간격으로 scrape하고 remote-write receiver를 연다.
- k6는 `experimental-prometheus-rw`로 결과 시계열을 전송한다.
- Grafana datasource와 9개 패널 대시보드는 provisioning 파일로 관리한다.

## 4. 통합 중 발견한 문제와 수정

| 관찰 | 원인 | 수정 |
|---|---|---|
| Docker daemon에 연결할 수 없음 | Docker Desktop이 실행되지 않음 | Docker Desktop 시작 후 daemon readiness 확인 |
| 9090/3000 port bind 실패 | 다른 로컬 개발 컨테이너가 사용 중 | 기본 host port를 18080/19090/13000으로 변경하고 환경 변수 override 제공 |
| Grafana dashboard API가 404 | dashboard 디렉터리를 nested mount해 provisioning 설정 파일이 가려짐 | provider 설정은 `/etc/grafana/provisioning`, JSON은 `/var/lib/grafana/dashboards`로 분리 |
| k6 p95 패널에 데이터 없음 | 실제 원격 쓰기 metric이 `k6_http_req_duration_p95`였음 | Prometheus에서 series name을 확인해 dashboard query를 실측 이름으로 수정 |

각 수정 뒤에는 `docker compose down` 후 `./scripts/run-experiment.sh`를 다시 실행했다. 최종 실행은 네 가지 자동 검증을 모두 통과했다.

## 5. 재현성과 운영 경계

- `results/summary.json`은 매 실행 산출물이라 Git에서 제외하고 핵심 수치는 `EXPERIMENT_LOG.md`에 고정 기록한다.
- 기본 Grafana 계정 `admin/admin`은 로컬 실험용이다. 외부 공개 환경에서는 secret과 인증을 별도로 구성해야 한다.
- 이 프로젝트의 수치는 Docker Desktop/localhost 결과이며 운영 capacity planning의 대체물이 아니다.
