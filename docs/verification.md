# 구현과 검증 근거

## 현재 구현

- [병상 전체 페이지 수집](../src/main/java/com/curelingo/curelingo/publicdata/mysql/sync/BedSnapshotCollector.java): totalCount 고정, 페이지별 예상 건수, 비어 있거나 중복된 hpid 거부. 0건과 10,000건 초과 응답은 보수적으로 거부.
- [병상 반영](../src/main/java/com/curelingo/curelingo/publicdata/mysql/sync/BedSyncService.java): 외부 수집·파싱은 트랜잭션 밖, 현재값과 성공 기록은 한 트랜잭션. 외래 키 오류는 전체 롤백. 원천 갱신 시각이 이전보다 과거이거나 이전 시각이 있는데 새 응답에 없으면 거부. 잘못된 숫자·날짜 거부. 이전과 같은 원천 시각은 허용하고 fetched_at 갱신. 응답에서 빠진 기관은 삭제하거나 0으로 만들지 않음.
- [일일 요청 예약](../src/main/java/com/curelingo/curelingo/publicdata/mysql/egen/EgenRequestBudget.java): 해당 EgenClient를 통한 요청마다 DB의 조건부 UPDATE로 예약. 재시도도 포함. Asia/Seoul 날짜 기준. 기본 1,000회는 설정한 계정 조건이며 제공자의 공통 한도/SLA가 아님. 다른 서버·레거시 프록시·같은 인증키를 쓰는 외부 클라이언트가 이 예산을 우회하면 계정 전체 한도를 보장하지 못함.
- [수집 스케줄](../src/main/java/com/curelingo/curelingo/egen/batch/AvailableBedsBatchJob.java): 기본 비활성화, fixed delay 3분. 한 페이지의 정상 주기 수집은 하루 최대 480회, 초기 적재·추가 페이지·재시도에는 별도 여유 필요. 동일 JVM의 전체 적재와 병상 수집은 같은 client lock으로 직렬화. DB당 수집 프로세스 한 개만 배포. 분산 락/다중 수집 서버는 구현하지 않음.
- [주변 병원 SQL](../src/main/java/com/curelingo/curelingo/publicdata/mysql/db/HospitalSearchRepository.java): R-tree 공간 후보 → hospital_department의 (hpid, department_code) 기본키 조회 → 좁은 materialized CTE → 정확 반경 → 상세·병상 JOIN → distance/hpid 정렬 및 LIMIT 50. NO_MERGE와 NO_DERIVED_CONDITION_PUSHDOWN 사용.
- [응급기관 SQL](../src/main/java/com/curelingo/curelingo/publicdata/mysql/db/EmergencyHospitalRepository.java): (emergency_room_open, active, hpid) 인덱스와 hospital을 시작점으로 사용, 위치는 기본키 탐색, 병상 LEFT JOIN, LIMIT 100. PK 반복 횟수는 한 SQL 내부 iterator의 loops이며 개별 SQL 요청 수가 아님.
- [기존 DB 마이그레이션](../src/main/resources/db/20261011-bed-refresh.sql): 새 앱 실행 전에 기존 DB에 한 번 적용. 새 DB는 전체 schema 사용. 이미 인덱스를 추가한 DB에서는 ALTER를 중복 실행하지 않음.

## 최신성/저장 규칙

원본 프로젝트의 source_updated_at DATETIME은 제공자의 한국 로컬 시각을 보존한다. sourceUpdatedAt 응답은 이를 Asia/Seoul로 해석해 Instant로 변환한다. fetched_at은 수집 완료 시각이며 UTC 연결 설정을 사용한다. 최근 수집/오래된 수집은 fetched_at과 설정 기준 6분으로 판정한다. 병상 0개와 NULL은 다르다. 최근 수집이라도 원천 정보가 오래됐을 수 있어 sourceUpdatedAt과 sourceAgeSeconds를 별도로 제공한다. 이는 실제 의료기관 수용 가능성을 보장하지 않는다.

현재값 조회가 목적이므로 hpid별 병상 현재값을 UPSERT한다. 수집 실행 이력은 sync_run에 저장한다. 병상 값의 전체 시계열 이력은 저장하지 않는다. 과거 상태 감사/추세 분석 요구가 생기면 변경값과 원천 시각 이력 저장을 추가할 수 있다.

## 2026-10-11 원본 프로젝트 통합 검증

[실행 스크립트](../scripts/verification/verify-bed-sync.py) · [모의 API](../scripts/verification/bed-provider-mock.py) · [17개 결과](evidence/bed-20261011/bed-sync-results.json)

실제 MySQL 8.4와 원본 Spring Boot 3.4.5 JAR에서 정상 4/실패 13개 통과. 실패 전후 병상 값·원천 시각·수집 시각을 대조. 두 번째 페이지 실패, totalCount 불일치, 중복/누락 hpid, 과거 시각, 잘못된 숫자/시각, 외래 키 오류, 응답 타임아웃, API22/23, 일일 예산 소진을 포함한다. 일부 페이지 장애는 모의 API로 주입하며 실제 공공 API 호출은 0회다. 무작위 장애/강제 프로세스 종료/다중 수집 서버/모든 입력의 무결성을 검증한 것은 아니다.

[응급 API 검증](../scripts/verification/verify-emergency-contract.py) · [결과](evidence/bed-20261011/api-contract-result.json) · [합성 응답](evidence/bed-20261011/api-contract-response.json)

실제 /api/emergency/nearby에서 병상 0개/최근 수집, 7분 전 수집/STALE, 병상 행 없음/NOT_AVAILABLE 3개 기관을 확인했다. 병상 정보 없는 기관의 전화번호·좌표와 목록 유지 확인. 추가 병상 API를 호출하지 않고 목록 응답에 정보를 포함한다. 기존 /beds 경로는 호환용으로 유지한다. /api/clinic/department도 합성 기관3개에서 거리순·진료과·병상0개 응답을 확인했다. Swagger 인터페이스와 Controller의 중복된 메서드 검증 선언이 500을 만들던 문제를 수정했다. 원천 hvidate 18:00이 DATETIME18:00, 응답09:00Z로 변환됨을 확인했다. [JAR·검증 기록](evidence/bed-20261011/manifest.json).

```bash
./gradlew bootJar
# 검증 전용 DB. 아래 비밀번호는 합성 환경의 공개 fixture 값이다.
docker run -d --name curelingo-original-verification -p 127.0.0.1:13307:3306 -e MYSQL_ROOT_PASSWORD=local-verification -e MYSQL_DATABASE=curelingo mysql:8.4
# mysqladmin ping으로 DB 준비 확인 후 실행. 출력 디렉터리는 매번 새 경로 사용.
python3 scripts/verification/verify-bed-sync.py /tmp/curelingo-bed-run
python3 scripts/verification/verify-emergency-contract.py /tmp/curelingo-api-run
docker stop curelingo-original-verification
```

## 과거 검색 성능 측정

측정 데이터와 SQL을 보존했으며, 이번 원본 프로젝트 통합 코드의 성능을 다시 측정한 값으로 쓰지 않는다. 측정 앱은 Spring Boot 3.4.9, 현재 원본은 3.4.5이고 DTO/스키마 컬럼 매핑을 원본에 맞췄다. 병원 데이터 79,019건, 진료과 관계 163,542건, 병상 416건 snapshot. 서울시청 좌표, 3km, D001, limit 50. 앱/DB/loadgen 각각 e2-medium 2 vCPU/4GB, MySQL8.4.11, Hikari max10, minIdle1. 목표 28 RPS, constant-arrival-rate, 측정 각60초.

[3회별 결과](evidence/nearby/measurements.csv) · [k6 검사 코드](evidence/nearby/nearby-load.js) · [변경 전 SQL](evidence/nearby/before-nearby.sql) · [변경 후 SQL](evidence/nearby/after-nearby.sql) · [전 계획](evidence/nearby/before-plan.txt) · [후 계획](evidence/nearby/after-plan.txt) · [동일 결과 검사](evidence/nearby/result-parity.json)

k6 원시 summary는 evidence/nearby/before-1..3.k6-summary.json 및 after-1..3.k6-summary.json에 있다. 본문의 HTTP p95는 매 실행 전체 요청의 http_req_duration p95를 구한 뒤 3개 실행값의 중앙값이며 모든 실행 요청을 합친 p95가 아니다. 각 실행 HTTP 상태200/JSON 배열 최대50개/hpid 중복 없음/3km 이내 거리 오름차순의 검사 통과율100%. 전체 내용의 완전성을 뜻하지 않는다. dropped_iterations 집계는 null이므로 0이라고 주장하지 않는다.

| 반복 | 기준 p95(ms) | 변경 p95(ms) |
|---|---:|---:|
| 1 | 372.9 | 131.7 |
| 2 | 198.5 | 128.6 |
| 3 | 358.3 | 120.1 |

기준은 9월28일 24/28 RPS 단계 교차, 변경은 9월30일 매회 2 RPS 60초 워밍업 후20초 대기. 날짜·순서 차이와 기준값 변동이 있어 완전한 교차 A/B/통계적 유의성/운영 피크 대응을 주장하지 않는다. 다른 좌표·진료과·cold cache는 미검증.

SQL 평균 경과시간은 Performance Schema digest 누적 wall elapsed/실행수이며 SQL CPU 시간이 아니다. 검사 행은 digest의 SUM_ROWS_EXAMINED/실행수이며 EXPLAIN 각 iterator 행 수와 다르다. DB VM CPU는 Prometheus 5초 scrape/30초 rate 표본의 stage 중앙값. MySQL 컨테이너 CPU와 호스트 CPU는 다르다. Grafana app p95는 서버 histogram 윈도우 값이고 k6 p95는 HTTP 왕복 지연이다. 그래프 순간값과 전체 실행 p95를 동일시하지 않는다.

첫 수정안은 상세·병상 JOIN을 CTE 안에 포함해 검사 행이 줄어도 HTTP p95 4.8–5.0초/실패가 발생했다. CTE 폭과 JOIN 위치 각각의 원인 기여는 분리하지 않았다. 최종안에서 중간 열을 식별자·위치·거리로 줄이고 상세 JOIN을 뒤로 이동했다. CTE 자체가 빨라지는 기술이라는 주장은 하지 않는다.

## 응급 검색: 추가 SQL 계측

[공간 우선 SQL](evidence/emergency/query.sql) · [응급기관 우선 SQL](evidence/emergency/alternative.sql) · [반복 실행계획](evidence/emergency/repeated-plans.txt) · [3회 수치](evidence/emergency/summary.json) · [동일 결과](evidence/emergency/parity.json)

2026-10-04 GCP MySQL8.4.11, 서울시청10km, limit100. 전국 병원79,019건 중 응급실 운영519건(약0.66%). 기존은 병원 PK 탐색 loops14,756회, 변경은 위치 PK 탐색 loops519회. 두 SQL은 병상 LEFT JOIN을 유지하고46건의 모든 컬럼/정렬 순서를 대조. 각1회 워밍업 후3회 번갈아 실행한 EXPLAIN ANALYZE top iterator 경과시간은 기준256/252/257ms, 변경9.18/8.91/9.18ms. 계측 오버헤드를 포함하며 API 지연/동시 부하 개선율이 아니다. 다른 지역·경계·분포의 동등성은 미검증.
