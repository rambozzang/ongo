# 상시 스케줄러·이벤트 리스너 트랜잭션 감사

조사일: 2026-08-07
범위: `backend` production 소스의 `@Scheduled` 20개 메서드와 `@EventListener` 2개가 있는 18개 파일

이 문서는 정적 감사 결과와 후속 테스트 우선순위만 기록한다. 코드 수정은 하지 않았다.

## 판정 기준

- **P1-오염 위험**: 하나의 Spring 트랜잭션 안에서 여러 항목을 순회하고, 항목별 `catch`가 DB 예외를 삼키며 계속 진행한다. jOOQ가 Spring 트랜잭션에 참여하면 첫 DB 예외 뒤 트랜잭션이 abort되어 뒤 작업과 앞서 성공한 작업이 함께 롤백될 수 있다.
- **P1-금전/상태 경계**: 외부 경로가 호출하는 항목별 작업에 트랜잭션이 없거나, 한 배치의 여러 DB 쓰기가 원자적으로 묶이지 않아 결제·구독·웹훅 상태가 부분 반영될 수 있다.
- **P2-부분 쓰기**: 의도적으로 항목별 격리를 사용하지만 여러 DB 쓰기 사이에 트랜잭션 경계가 없어 한 항목 내부 정합성이 깨질 수 있다.
- **안전/관찰**: 단일 DB 작업·읽기·이벤트 발행 또는 이미 `REQUIRES_NEW`로 경계가 확인되는 경우. 런타임 동작은 IT가 없으면 미확인으로 남긴다.

`@Profile`은 18개 파일 어디에도 없었다. 이들은 WIP 컨트롤러와 무관하게 기본 프로필에서 이미 실행되는 상시 빈이다.

## 우선순위 요약

| 우선순위 | 대상 | 핵심 근거 | 판정 |
|---|---|---|---|
| P1 | `ScheduleExecutor` | `@Transactional` + 예약별 `catch` + `scheduleRepository.update` (`ScheduleExecutor.kt:46-105`); 위임 메서드도 `StreamPublishUseCase.kt:428-`에서 `@Transactional` | DB 예외를 삼키면 다음 예약까지 오염될 수 있는 **직접 재현 대상**. 또한 구형 `tryLock/releaseLock` 사용(`:49`, `:108`) |
| P1 | `WebhookRetryScheduler` | 이벤트 루프에서 `reprocessWebhookEvent` 후 상태 갱신, 실패 시 FAILED/DEAD_LETTER 갱신 (`WebhookRetryScheduler.kt:41-74`); 재처리 메서드는 `PaddleWebhookService.kt:115-127`에 `@Transactional` 없음 | 결제·구독 반영이 일부 성공한 뒤 실패 상태만 기록될 수 있음. 이벤트별 원자성/멱등성 IT 필요 |
| P1 | `BillingScheduler` | 메서드 전체 `@Transactional` (`BillingScheduler.kt:31-33`) 안에서 구독·사용자·크레딧·알림을 여러 루프에서 갱신 (`:42-163`) | 항목별 catch는 없어 DB 예외를 삼키는 결함은 **미확인**. 다만 한 항목 실패 시 전체 배치 롤백 정책과 대형 트랜잭션을 명시해야 하며 금전 경로라 우선 감사 |
| P1 | `CreditScheduler` | 기존 결함은 `TransactionTemplate(REQUIRES_NEW)`로 수정됨 (`CreditScheduler.kt:62-145`) | 9e559ba/후속 IT로 항목 격리 반영. 재회귀 감시만 필요 |
| P1 | `ABTestEvaluator` | 기존 결함은 `TransactionTemplate(REQUIRES_NEW)`로 수정됨 (`ABTestEvaluator.kt:33-70`, `:78-100`) | 372ee1f/`ABTestEvaluatorTest.kt:83-138` 반영. 4개 테스트와 실제 DB 의미 IT를 유지 |
| P2 | `CommentSyncScheduler` + 위임 UseCase | 스케줄러는 사용자별 catch (`CommentSyncScheduler.kt:46-55`); 사용자 작업은 `CommentSyncUseCase.kt:30-83`의 `@Transactional`, 영상 작업은 외부 빈 `VideoCommentSyncService.kt:39-176`의 `@Transactional` | 현재는 사용자·영상 호출이 각각 프록시 경계를 타는 구조로 보인다. 실제 DB 예외를 유발해 1번 실패 후 2번 진행을 IT로 고정할 것. 구형 락 API도 사용(`:26`, `:60`) |
| P2 | `AnalyticsSyncScheduler` | 채널/업로드/날짜 중첩 루프와 비동기 작업, `analyticsRepository.upsert` (`AnalyticsSyncScheduler.kt:43-95`, `:104-123`) | 외부 API·날짜별 실패는 현재 항목별 격리(무트랜잭션)로 보인다. 한 날짜의 upsert만 커밋되는 부분 쓰기는 의도인지 확인. 구형 락 API(`:35`, `:100`) |
| P2 | `CompetitorSyncScheduler` | 경쟁자별 `update` 후 `upsertAnalytics`, 같은 항목 catch (`CompetitorSyncScheduler.kt:19-55`) | 트랜잭션이 없어 snapshot은 저장되고 daily analytics는 실패하는 불일치 가능. 어댑터 IT로 원자성 정책 결정 |
| P2 | `ChannelScheduler` | 채널별 외부 호출 후 update, 토큰 실패 시 상태 update+notification save (`ChannelScheduler.kt:30-109`) | 트랜잭션이 없어 상태는 EXPIRED인데 알림이 실패하는 부분 쓰기 가능. 토큰 갱신·만료 처리의 의도와 재시도 정책 확인 |
| P2 | `WeeklyDigestScheduler` | 구독자 루프와 사용자별 catch (`WeeklyDigestScheduler.kt:33-54`); UseCase는 AI 후 단일 save (`WeeklyDigestUseCase.kt:27-80`) | 스케줄러 트랜잭션 오염은 없음. 사용자별 단일 저장 실패 격리는 의도에 맞아 보이나 AI 호출·저장 관측 테스트 필요 |
| P2 | `RevenueAlertScheduler` | 설정별 `runCatching` 안에서 알림 save (`RevenueAlertScheduler.kt:25-60`, `:67-98`) | 단일 알림 쓰기라 트랜잭션 오염은 없음. 중복 알림/재실행 멱등성은 미확인 |
| P2 | `PerformanceTriggerEvaluator` | rule별 catch와 이벤트 발행 (`PerformanceTriggerEvaluator.kt:24-70`), DB write 없음 | DB 트랜잭션 결함은 아님. 이벤트 중복 발행/리스너 유무를 별도 확인 |
| P2 | `ApprovalSlaScheduler` | 승인 단계 루프에서 이벤트만 발행 (`ApprovalSlaScheduler.kt:20-48`) | DB write 없음. 이벤트 consumer와 중복 발행 정책 미확인 |
| P2 | `TrendSyncScheduler` | 단일 `saveBatch`를 전체 try/catch (`TrendSyncScheduler.kt:15-26`) | 항목별 루프 없음. 배치 전체 성공/실패 경계는 명확하나 재실행 멱등성 미확인 |
| P2 | `RefreshTokenCleanupScheduler` | 단일 `deleteExpired` 호출 (`RefreshTokenCleanupScheduler.kt:14-22`) | 루프·부분 쓰기 없음 |
| P2 | `PartitionMaintenanceScheduler` | 단일 DDL 함수 호출 (`PartitionMaintenanceScheduler.kt:14-22`) | 루프·부분 쓰기 없음. 실패 알림/다음 실행 의존 |
| 안전 후보 | `LowCreditAlertEventListener` | `@EventListener` + `REQUIRES_NEW` + notification 단일 save (`LowCreditAlertEventListener.kt:21-30`) | 현재 패턴은 적절해 보임. 실제 이벤트 실패 시 publisher 영향은 미확인 |
| 안전 후보 | `UploadCompletedEventListener` | `@EventListener` + `REQUIRES_NEW` + notification 단일 save (`UploadCompletedEventListener.kt:21-47`) | 현재 패턴은 적절해 보임. WebSocket 실패 시 DB notification 커밋 정책은 IT 필요 |

## 확정된 사실과 미확인 사항

### 소스에서 확정

1. `ScheduleExecutor`는 CreditScheduler/ABTestEvaluator 수정 전과 같은 구조인 `@Transactional` 바깥 루프 + 항목별 `catch` + DB update다. 이 파일은 가장 먼저 PostgreSQL IT를 붙여야 한다.
2. `BillingScheduler`는 한 번의 `@Transactional` 안에서 여러 구독·사용자·크레딧·알림 쓰기를 수행한다. catch가 없어 “삼킨 예외”는 확인되지 않지만, 하나의 실패가 전체 롤백되는 정책이 코드에 문서화되어 있지 않다.
3. `WebhookRetryScheduler`의 재처리 경로는 `PaddleWebhookService.handleWebhook`의 `@Transactional`(`PaddleWebhookService.kt:39`)과 다르다. `reprocessWebhookEvent`에는 트랜잭션 애노테이션이 없으므로 재시도 경로의 원자성이 보장되지 않는다.
4. `AnalyticsSyncScheduler`, `CompetitorSyncScheduler`, `ChannelScheduler`는 트랜잭션 없이 항목별 DB 쓰기를 수행한다. 이는 트랜잭션 오염은 아니지만 항목 내부 부분 반영 가능성이다.
5. `CommentSyncScheduler`는 자체 트랜잭션이 없지만 실제 사용자·영상 작업은 각각 다른 Spring 빈의 `@Transactional` 메서드를 호출한다. 자기호출이 아니므로 프록시 경계가 있다는 것이 소스상 확인된다.

### 아직 미확인

- PostgreSQL에서 `ScheduleExecutor` 한 건의 DB 예외를 삼킨 뒤 다음 예약이 실제로 어떻게 되는지
- `BillingScheduler`에서 한 구독 실패 시 전체 롤백이 제품 의도인지, 사용자별 격리가 필요한지
- `WebhookRetryScheduler` 재처리 중 결제/구독 변경 일부 성공 후 예외가 날 때 재시도가 중복 반영을 만드는지
- 무트랜잭션 스케줄러의 항목 내부 두 쓰기(update+upsert/update+notification)가 원자적이어야 하는지
- 5개 스케줄러의 구형 advisory lock(`tryLock`/`releaseLock`) 누수가 아직 남아 있는지. 공유 `withLock`은 CreditScheduler에만 전환된 상태다.

## 권장 실행 순서

1. **ScheduleExecutor**: `PaymentLockIT` 계열의 PostgreSQL 오염 재현 + 항목별 `REQUIRES_NEW` 또는 전체 재던짐 정책 확정. 예약 게시 금전/상태 영향이 있어 최우선.
2. **WebhookRetryScheduler**: 재처리 이벤트 하나를 독립 트랜잭션으로 묶고, 처리 실패 시 상태 갱신과 업무 변경의 롤백 경계를 Testcontainers로 검증.
3. **BillingScheduler**: 전체 원자성 대 항목별 격리 중 제품 정책을 정한 뒤 테스트. 구독/크레딧/알림을 한 transaction으로 묶을지 분리할지 결정.
4. **공유 락 마이그레이션**: Schedule, Comment, WebhookRetry, Billing, Analytics의 `tryLock/releaseLock`을 `DistributedLockPort.withLock`으로 옮기는 별도 작업.
5. **P2 consistency IT**: Analytics/Competitor/Channel의 항목 내부 다중 쓰기와 Comment의 위임 트랜잭션 경계를 실제 DB로 고정.
6. 단일 쓰기/읽기/이벤트 빈은 위 P1이 닫힌 뒤 관찰성·중복 이벤트 테스트를 추가한다.

## 감사 결론

현재 코드에서 **CreditScheduler와 ABTestEvaluator의 직접 결함은 이미 수정된 상태**다. 새로 확인된 직접 P1은 `ScheduleExecutor`이며, 금전 경로의 경계 위험은 `WebhookRetryScheduler`와 `BillingScheduler`다. 나머지는 같은 결함으로 단정하지 않고, 트랜잭션 없음에 따른 부분 반영·락 누수·멱등성 문제를 별도 IT로 확인해야 한다.
