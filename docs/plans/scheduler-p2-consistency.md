# Analytics·Competitor·Channel 스케줄러 P2 정합성 분석

조사일: 2026-08-07
범위: `AnalyticsSyncScheduler`, `CompetitorSyncScheduler`, `ChannelScheduler`

코드 수정 없이 정적 분석만 했다. 목적은 Schedule/Billing의 P1 트랜잭션 격리와 별개로, 현재 무트랜잭션 항목별 처리가 어떤 부분 반영을 허용하는지 결정하는 것이다.

## 요약 판정

| 대상 | 현재 DB 쓰기 | 현재 경계 | P2 위험 | 권장 정책 |
|---|---|---|---|---|
| Analytics | 영상·날짜별 `analytics_daily` 단일 upsert | 날짜 단위 자동 커밋 + 실패 catch | 낮음. 한 날짜 실패와 다른 날짜 성공이 섞일 수 있으나 자연스러운 백필 동작 | 현재 격리 유지, upsert/재시도 IT 추가 |
| Competitor | 경쟁자 snapshot update → 일별 analytics upsert | 경쟁자별 자동 커밋, 두 쓰기 사이 경계 없음 | **P1/P0 스키마 차단**. `competitor_analytics_daily` 마이그레이션이 없어 upsert가 relation 오류일 가능성 | 테이블 존재를 먼저 운영 DB에서 확인하고 migration 보강 후, 두 DB 쓰기를 짧은 트랜잭션으로 묶기 |
| Channel | 채널 정보 update; 토큰 만료 시 channel update → notification save | 채널별 자동 커밋 | 중간. 만료 상태는 저장됐지만 알림만 유실될 수 있음 | 채널 상태는 우선 커밋, 알림은 outbox/재시도 경계 |

세 대상 모두 `@Transactional` 바깥 루프에서 DB 예외를 삼키는 Schedule/Credit/ABTest 유형의 트랜잭션 오염은 현재 소스에서 확인되지 않았다. 다만 `tryLock/releaseLock` 구형 API는 세 스케줄러에 남아 있으며 별도 withLock 마이그레이션 대상이다.

## 1. AnalyticsSyncScheduler

### 소스 근거

- 스케줄과 락: `backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/AnalyticsSyncScheduler.kt:33-40`
- 채널별 가상 스레드와 업로드/날짜 루프: `:43-95`
- 외부 API 호출과 단일 upsert: `:104-123`
- `analytics_daily`의 `(video_upload_id, date)` 유니크 키와 체크 제약: `backend/onGo-api/src/main/resources/db/migration/V1__init_schema.sql:173-193`
- jOOQ upsert는 `ON CONFLICT (video_upload_id, date) DO UPDATE`: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/AnalyticsJooqRepository.kt:306-334`

### 판정

`syncVideoAnalytics`는 날짜 하나의 플랫폼 조회 결과를 하나의 upsert로 저장한다. 현재는 메서드에 트랜잭션이 없고 예외를 `:121-123`에서 기록한 뒤 다음 날짜로 진행한다. 따라서 날짜 A가 성공하고 날짜 B가 실패하는 부분 반영은 의도된 백필 특성과 맞는다. B의 실패가 A의 데이터를 롤백해야 할 교차 날짜 원자성은 없다.

`ON CONFLICT`와 유니크 키 덕분에 같은 날짜를 다음 주기에 재실행해도 중복 행 대신 최신 값으로 갱신된다. `findLatestDateByVideoUploadId`(`AnalyticsJooqRepository.kt:419-424`)가 마지막 날짜를 기준으로 다음 백필을 잡으므로, 중간 날짜 실패는 다음 실행에서 재시도될 수 있다.

### 남은 위험

- 채널별 작업은 가상 스레드에서 실행되므로 DB 커넥션/외부 API 동시성이 풀과 플랫폼 rate limit을 넘지 않는지 런타임 미확인이다. `Semaphore(5)`는 외부 analytics 호출 주변에만 있다(`:65-80`).
- `syncVideoAnalytics`가 모든 예외를 삼키므로 영구적인 데이터/제약 오류도 다음 주기 재시도로만 보인다. 실패 날짜와 횟수 메트릭/알림은 별도 관측 작업이다.
- `tryLock`과 `releaseLock`이 서로 다른 풀 커넥션을 사용할 가능성이 있어 락 누수가 남아 있다(`:35`, `:100`). 이는 정합성보다 실행 누락 위험이며 withLock 마이그레이션에서 해결한다.

### 권장 검증

1. 같은 `(video_upload_id, date)`를 두 번 upsert해 행이 하나이고 최신 값인지 PostgreSQL IT로 확인한다.
2. 날짜 A의 upsert 성공, 날짜 B의 DB 오류, 날짜 C 성공 시 A/C가 남고 B가 다음 실행에서 재시도되는지 확인한다.
3. 플랫폼 API 실패·DB 제약 실패·파티션 누락을 구분해 구조화 로그가 남는지 확인한다.
4. 가상 스레드 수와 Hikari 풀 상한을 함께 둔 부하 테스트를 별도로 한다.

## 2. CompetitorSyncScheduler

### 소스 근거

- 전체 루프와 항목별 catch: `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorSyncScheduler.kt:19-57`
- 외부 채널 조회: `:25-31`
- snapshot update: `:33-39`
- 일별 analytics upsert: `:42-49`
- jOOQ 구현은 두 쓰기를 별도 SQL로 발행: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/CompetitorJooqRepository.kt:71-85`, `:125-143`
- analytics 유니크 키는 `(competitor_id, date)`를 전제로 한다(`CompetitorJooqRepository.kt:135-143`의 `ON CONFLICT` 대상).

### 새로 확인된 스키마 차단점

`rg`로 `backend/onGo-api/src/main/resources/db/migration/V*.sql` 전체를 검색했지만 `competitor_analytics_daily`의 `CREATE TABLE`/`ALTER TABLE`은 **0건**이었다. 반면 다음 production 코드는 해당 테이블을 전제로 한다.

- 테이블 상수: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt:51`
- 기간 조회: `CompetitorJooqRepository.kt:99-123`
- upsert: `CompetitorJooqRepository.kt:125-143`
- 스케줄러 호출: `CompetitorSyncScheduler.kt:42-49`

따라서 실제 운영 DB에 별도 수동 테이블이 없다면 매 동기화마다 `relation "competitor_analytics_daily" does not exist`가 발생한다. 현재 스케줄러는 그 예외를 `CompetitorSyncScheduler.kt:52-54`에서 삼키므로, `competitors` snapshot update(`:33-39`)가 먼저 커밋된 뒤 analytics는 영원히 누락되는 **확정 가능한 P1/P0급 경로**다. 운영 DB에 수동 생성된 테이블이 있는지는 아직 미확인이다. 이 문서의 원래 P2 판정을 **마이그레이션/운영 스키마 확인 전까지 P1 차단**으로 상향한다.

마이그레이션 번호와 DDL 추가는 소유자에게 요청해야 한다. 이 감사에서는 스키마 파일이나 코드 수정을 하지 않는다.

### 판정

한 경쟁자에 대해 외부 조회가 성공하면 snapshot과 일별 analytics를 연속으로 쓴다. 현재 트랜잭션이 없으므로 snapshot update가 커밋된 뒤 analytics upsert에서 실패하면 다음 상태가 된다.

```text
competitors.last_synced_at = 최신
competitor_analytics_daily = 해당 날짜 누락
```

이는 구독/크레딧 같은 권한 정합성은 아니지만, 화면의 현재 snapshot과 기간 분석이 서로 다른 시점을 가리킨다. 특히 위 테이블이 실제로 없으면 이 상태가 매번 재현된다. 경쟁자 한 건의 두 DB 쓰기는 외부 API 호출이 끝난 뒤 짧은 트랜잭션으로 묶는 편이 맞다. 외부 호출을 트랜잭션 안에 넣으면 안 된다.

실패 후 다음 실행이 전체 경쟁자를 다시 조회하므로 누락 analytics는 재시도된다. `upsertAnalytics`가 멱등이면 중복은 없지만, 실패 원인이 영구 제약 오류일 때는 snapshot만 계속 갱신될 수 있다.

### 권장 정책

- 외부 `lookupChannel`은 트랜잭션 밖에서 수행한다.
- 결과가 `found`인 경우에만 경쟁자 1건 단위 `REQUIRES_NEW`/TransactionTemplate으로 `update + upsertAnalytics`를 함께 커밋한다.
- 둘 중 하나가 실패하면 둘 다 롤백하고 `competitorId/date`를 포함한 재시도 로그를 남긴다.
- 다음 실행에서 동일 결과를 재적용해도 안전하도록 두 SQL의 조건부 update/upsert를 유지한다.

### 권장 검증

1. snapshot update 성공 후 analytics upsert를 강제 실패시켰을 때 snapshot까지 롤백되는지 확인한다.
2. 경쟁자 A 실패가 B/C의 두 쓰기를 막지 않는지 확인한다.
3. 동일 날짜 재실행이 analytics 한 행만 유지하는지 확인한다.
4. 외부 lookup 실패 시 DB 쓰기가 0회인지 확인한다.

## 3. ChannelScheduler

### 소스 근거

- 채널 정보 갱신: `backend/onGo-application/src/main/kotlin/com/ongo/application/channel/ChannelScheduler.kt:30-50`
- 토큰 만료 조회와 루프: `:55-66`
- 토큰 갱신 성공 시 채널 update: `:67-89`
- 비일시 오류 시 EXPIRED update와 notification save: `:90-109`
- 채널의 사용자·플랫폼 유니크 제약: `backend/onGo-api/src/main/resources/db/migration/V1__init_schema.sql:60-81` (다른 채널의 중복 생성 방지용; 스케줄러 update는 ID 기준)

### 판정

`refreshAllChannels`는 채널 한 건당 외부 조회 후 채널 update 하나만 한다(`:37-44`). 실패한 채널만 catch하고 다음 채널로 가는 현재 격리는 적절하다.

`checkExpiringTokens`의 비일시 오류 분기는 채널을 `EXPIRED`로 바꾼 뒤 알림을 저장한다(`:97-105`). 트랜잭션이 없으므로 channel update가 성공하고 notification save가 실패하면 채널은 올바르게 만료되지만 사용자 알림은 유실된다. 반대로 알림 저장이 먼저가 아니어서 “알림은 있는데 채널은 ACTIVE”인 순서는 현재 코드상 발생하지 않는다.

이 경로에서 알림은 인증 상태의 원천 데이터가 아니다. 따라서 채널 상태를 알림 저장 성공에 종속해 롤백하는 것보다, EXPIRED 상태를 먼저 보존하고 알림을 outbox/재시도 대상으로 남기는 정책이 안전하다. 단, 동일 채널이 `findAllActive`에서 제외되는지와 알림 재시도 기준은 IT로 확인해야 한다.

### 남은 위험

- 일시적 오류는 상태를 유지하고 다음 시간 주기에 재시도한다(`:90-94`). 영구 오류는 EXPIRED로 바꾸므로 오류 분류가 틀리면 정상 채널을 차단할 수 있다.
- `channelRepository.update`가 성공한 뒤 알림 저장 실패는 현재 로그만 남긴다. 구조화 실패 이벤트/알림 outbox가 없다.
- 두 스케줄 메서드 모두 분산 락이 없다. 여러 인스턴스에서 같은 채널의 토큰 갱신이 경합할 수 있다. `withLock`을 도입할지, 채널 ID 행 잠금/조건부 update를 사용할지는 별도 정책이다.

### 권장 정책

- 채널 정보 조회·토큰 refresh 같은 외부 호출은 트랜잭션 밖에서 수행한다.
- 채널 단일 update는 현재처럼 항목별 격리를 유지한다.
- EXPIRED 상태 update는 핵심 상태로 즉시 커밋하고, 알림은 outbox 또는 실패 재시도 경로로 분리한다.
- 토큰 refresh 성공 결과를 저장할 때는 `WHERE id = ? AND token_expires_at <= observedExpiry` 같은 조건부 update 또는 행 잠금으로 오래된 작업이 최신 토큰을 덮어쓰지 않게 한다. 현재 조건부 update 여부는 **미확인**이다.

### 권장 검증

1. 정상 refresh 결과가 채널 토큰/만료 시각에 정확히 반영되는지 확인한다.
2. 비일시 오류에서 channel=EXPIRED가 커밋되고 notification 실패가 재시도 목록에 남는지 확인한다.
3. 일시적 429/5xx에서는 상태가 ACTIVE로 유지되는지 확인한다.
4. 두 인스턴스가 만료 토큰을 동시에 처리할 때 마지막 토큰이 유실되지 않는지 PostgreSQL/모의 플랫폼 IT로 확인한다.

## 공통 락 후속

세 스케줄러 모두 기존 `tryLock`/`releaseLock`을 사용한다.

- Analytics: `AnalyticsSyncScheduler.kt:35`, `:100`
- Competitor: 분산 락 없음
- Channel: 분산 락 없음

Analytics는 Credit/ABTest/Schedule에서 이미 확인된 세션 락 누수의 동일한 위험을 공유한다. Competitor/Channel은 반대로 락 자체가 없어 다중 인스턴스 중복 실행 위험이 별도다. 락을 일괄 추가하기 전에 작업 주기·DB 행 경계·외부 API 중복 허용 여부를 정해야 한다.

## 우선순위와 결론

1. **Competitor**: 운영 DB에 `competitor_analytics_daily`가 존재하는지 즉시 확인하고, 없으면 migration을 먼저 추가한다(P1/P0). 그 뒤 한 항목의 `update + upsert`를 짧은 트랜잭션으로 묶는다.
2. **Channel**: EXPIRED 상태와 알림을 분리하고 알림 재시도/outbox를 정하는 P2. 토큰 경합 방어도 필요하다.
3. **Analytics**: 현재 날짜별 upsert 격리는 합리적이며, 멱등 upsert·실패 재시도·락 마이그레이션 IT가 우선이다.

세 스케줄러에서 `@Transactional` 전체 루프 + DB 예외 삼킴으로 인한 P1 트랜잭션 오염은 확인되지 않았다. 다만 Competitor는 스키마 부재 가능성 때문에 별도 P1/P0 차단이다. 나머지 P2 변경은 외부 호출을 긴 트랜잭션에 넣지 않고, **항목 내부의 필요한 DB 쓰기만 짧게 묶는 방향**으로 진행해야 한다.
