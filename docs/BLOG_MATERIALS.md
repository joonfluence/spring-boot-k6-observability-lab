---
status: 초안재료완료
verified: false
---

# 기술 블로그용 글감: 이론으로 알던 병목을 실제 그래프로 검증하기

이 문서는 최종 블로그 글이 아니라, 검증된 실험 기록을 사실 기반 글로 재구성하기 위한 원재료다. 최종 글의 문체·독자 관점은 작성 전에 별도로 정한다.

## 제목 후보

- "동시 요청은 늘었는데 RPS는 왜 안 늘지?" Spring Boot와 k6로 병목을 찾은 실험
- Java 21 Virtual Thread·HikariCP·k6를 한 화면에서 관찰하는 로컬 부하 테스트 랩
- 최대 처리량을 숫자 하나로 말하지 않는 이유: Prometheus와 Grafana로 읽는 병목 신호

## 한 문장 메시지

병목 지표의 이름을 아는 것과 실제 임계값을 아는 것은 다르며, 부하 생성·애플리케이션·자원 지표를 같은 시간축에 놓아야 "무엇이 먼저 막혔는지"를 설명할 수 있다.

## 독자가 겪는 문제 상황

- k6 summary에서 p95가 나빠졌지만 애플리케이션, DB, 부하 생성기 중 어디가 원인인지 알 수 없다.
- Virtual Thread를 켰으니 동시성 한계가 사라졌다고 오해한다.
- HikariCP의 max pool size는 설정했지만 `pending`이 언제 증가하는지 보지 않는다.
- Grafana 대시보드를 화면에서 만들고 설정 파일은 남기지 않아 재현이 안 된다.

## 글의 권장 흐름

1. **문제 제기**: "몇 RPS까지 되나요?"라는 질문이 왜 불완전한가.
2. **실험 계약**: 고정한 환경, 부하 단계, threshold, 관찰 지표를 먼저 명시한다.
3. **관측 파이프라인**: k6 → Prometheus remote write, Spring Actuator → Prometheus scrape, Grafana provisioning 흐름을 설명한다.
4. **세 가지 병목 가설**: I/O 대기, CPU 포화, HikariCP 풀 포화를 각각 어떻게 자극했는지 보여준다.
5. **그래프 읽기**: 요청률·p95/p99·오류율·CPU·스레드·Hikari pending을 동일 시간축에서 비교한다.
6. **결론과 한계**: localhost smoke test의 수치를 운영 capacity 수치로 일반화하지 않고, 다음 실험(유지 구간 확대·반복·DB 쿼리 현실화)을 제시한다.

## 수집해야 할 증거

| 증거 | 출처 | 글에서의 용도 |
|---|---|---|
| k6 총 요청, p95/p99, threshold | `results/summary.json` | 부하가 실제로 수행됐다는 근거 |
| 요청률/실패율 시계열 | Grafana k6 패널 | 부하 증가와 품질 저하 시점 연결 |
| Spring 요청 p95 | Grafana Micrometer 패널 | 서버 관측과 클라이언트 체감 비교 |
| CPU/스레드 | Grafana JVM 패널 | CPU·스케줄링 가설 검증 |
| Hikari active/pending/max | Grafana HikariCP 패널 | DB 커넥션 풀이 병목인지 판별 |
| Compose·대시보드 설정 | 저장소 파일 | 다른 사람이 재현할 수 있는 코드 근거 |

## 이번 실행에서 확보한 사실

- 75ms 모의 I/O, 0→10→30→0 VU, 35초 조건에서 2,683개 요청이 모두 HTTP 200이었다.
- k6 summary의 p95는 90.65ms, p99는 113.66ms, 실패율은 0%였다.
- Grafana datasource proxy도 동일한 p95 값 `0.0906528958`초를 반환했다.
- 이 조건에서는 첫 병목이 관찰되지 않았다. "아직 한계가 아니다"라는 결론을 데이터와 함께 남기는 것이 핵심이다.

이 수치는 `EXPERIMENT_LOG.md`의 한 번의 smoke execution에서 온 것이므로, 블로그 본문에는 환경·조건·반복 횟수를 반드시 함께 적는다.

## 실험 후 채울 문장 템플릿

- "[환경]에서 [VU 단계]까지는 p95가 [값]이었지만, [다음 단계]부터 [지표]가 [변화]했다."
- "이때 CPU는 [값]이고 HikariCP pending은 [값]이어서, 이번 조건의 첫 병목은 [판정]으로 해석했다."
- "이 결론은 [Docker Desktop 자원/짧은 유지 구간/localhost]이라는 한계를 가진다. 운영 환경에서는 [추가 검증]이 필요하다."

## 사실 확인이 필요한 주장

- k6 Prometheus remote write 모듈은 문서상 experimental 상태이므로, 사용한 k6 이미지 버전을 글에 함께 명시한다.
- Spring Boot의 Prometheus endpoint는 명시적으로 노출해야 하며, 이 프로젝트는 `/actuator/prometheus`를 사용한다.
- Java 21 Virtual Thread의 효과는 작업 성격(I/O 대기, pinning, 외부 리소스 한계)에 따라 달라진다. 벤치마크 결과만으로 일반적 우위를 주장하지 않는다.
