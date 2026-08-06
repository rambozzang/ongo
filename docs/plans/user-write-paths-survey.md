# 사용자 범위 쓰기 경로 전수 조사

작성: 2026-08-07 · 계정 삭제 동결 게이트 적용 대상 확정용
선행 문서: `account-deletion-policy-table.md`

## 0. 왜 적용보다 조사가 먼저인가

가드를 먼저 붙이면 **붙이지 않은 경로를 "보호됐다"고 오판**하게 된다(codex).
적용 대상 목록을 먼저 확정하고, 그 목록을 기준으로 적용과 **누락 탐지 테스트**를 함께 만든다.

현재 `UserWriteGuard` 계약만 있고 **어디에도 적용하지 않았다.**

## 1. 규모

| 범주 | 기본 프로필 | `@Profile("wip")` 차단 |
|---|---:|---:|
| `user_id` 를 다루며 쓰기하는 UseCase·Service | **66** | 36 |
| `user_id` 를 쓰는 jOOQ 저장소 (insert/update/delete) | **52** / 전체 73 | — |
| `@Scheduled` 보유 파일 | **16** | 0 |
| `@EventListener` | 2 | — |
| `@TransactionalEventListener` | 2 | — |
| `@Async` | 4 | — |
| workspace/team 경유 쓰기 후보 | 40 | — |

HTTP 필터 하나로 66개 UseCase 를 덮을 수는 있지만, **`@Scheduled` 16개는 필터를 지나지
않는다.** 이게 필터만으로 부족한 이유다.

## 2. 범주별 판정

### 2.1 HTTP 진입 — 공통 gate 로 덮인다

컨트롤러를 통해 들어오는 쓰기는 JWT 인증 직후 공통 경계 한 곳에서 막을 수 있다.
`@CurrentUser` 로 사용자 식별이 이미 이뤄지므로 그 지점이 자연스럽다.

**예외 허용은 최소로 한다.**

| 경로 | 허용 이유 |
|---|---|
| `DELETE /api/v1/auth/account` | 삭제 요청 자체 |
| `POST /api/v1/auth/refresh` | 세션 유지. **쓰기를 재개시키지 않는다** |
| 로그아웃 | 세션 종료 |
| 삭제 상태 조회 | 진행 상황 확인 |

`refresh` 가 허용된다고 해서 게이트가 풀리는 것이 아니다. 토큰을 갱신해도 다음 쓰기 요청은
같은 게이트에서 다시 막힌다. 이걸 테스트로 명시한다.

### 2.2 스케줄러 — 필터가 닿지 않는다

기본 프로필에서 도는 16개다.

```
ABTestEvaluator            AnalyticsSyncScheduler     ApprovalSlaScheduler
BillingScheduler           ChannelScheduler           CommentSyncScheduler
CompetitorSyncScheduler    CreditScheduler            PartitionMaintenanceScheduler
PerformanceTriggerEvaluator RefreshTokenCleanupScheduler RevenueAlertScheduler
ScheduleExecutor           TrendSyncScheduler         WeeklyDigestScheduler
WebhookRetryScheduler
```

전부 게이트를 봐야 하는 것은 아니다. 세 갈래로 나뉜다.

| 갈래 | 예 | 처리 |
|---|---|---|
| 사용자 데이터를 쓴다 | `AnalyticsSyncScheduler`, `CompetitorSyncScheduler`, `ScheduleExecutor`, `WeeklyDigestScheduler` | **항목별로 게이트 검사.** 동결된 사용자는 건너뛴다 |
| 사용자 무관 유지보수 | `PartitionMaintenanceScheduler`, `RefreshTokenCleanupScheduler` | 시스템 경로로 등록해 우회 |
| **금융·정산** | `WebhookRetryScheduler`, `BillingScheduler`, `CreditScheduler`, 결제 웹훅 서비스 | **동결 중에도 처리한다.** 시스템 경로로 등록 |

### 금융 경로는 동결로 막지 않는다 (codex 결정)

처음에 "판단 필요"로 미뤄뒀는데 방향이 정해졌다. **멈추는 쪽이 더 위험하다.**

- `WebhookRetryScheduler` 와 결제 웹훅: 멈추면 결제 상태·환불·크레딧 원장이 깨진다.
  외부 PG 가 보내는 사실을 기록하는 것이라 동결 여부와 무관하다
- `BillingScheduler`: 외부 게이트웨이 호출이 없는 DB 정합화라 계속 처리한다.
  단 **새 청구를 만들지 않는지** 확인이 필요하다
- `CreditScheduler` 무료 크레딧 리셋: 일괄로 건너뛰면 동결이 길어졌다 풀렸을 때
  **권리가 누락된다.** 제품 정책 확정 전 기본값으로 계속 처리한다

대신 삭제 트랜잭션이 금융 쪽 `REVIEW_BLOCK` 을 만나면 **삭제를 막는다.**
정합성을 지키는 방향은 "쓰기를 멈추는 것"이 아니라 "삭제를 미루는 것"이다.

우회는 [`SystemWritePathRegistry`] 에 근거와 함께 등록된 경로만 할 수 있다.
등록되지 않은 경로가 `SYSTEM_RECONCILIATION` 을 쓰면 예외로 막고, 사용할 때마다 로그를 남긴다.
기본값은 `USER_AUTHORED` 라 실수로 우회되지 않는다.

### 2.3 workspace/team 경유 — 지금 적용한다

40개 파일이 `workspaceId` 를 다룬다.

처음에 "정책 표의 `workspaces.owner_id` 가 `REVIEW_BLOCK` 이니 게이트 적용도 보류하자"고
제안했는데 **틀렸다**(codex 지적). 두 질문을 섞은 것이다.

| 질문 | 상태 |
|---|---|
| 삭제할 때 이 공유 행을 어떻게 할 것인가 | `REVIEW_BLOCK` 유지. 미정 |
| **동결된 사용자가 계속 써도 되는가** | **아니다.** 지금 막는다 |

전자가 미정이라고 후자를 허용할 근거가 되지 않는다. 기본은 fail-closed 다.

**적용 방식**: HTTP·내부 구분 없이 workspace 쓰기도 **호출자 `userId`** 의 게이트를 통과시킨다.
동결된 멤버는 공유 워크스페이스에도 쓰지 못하고, 다른 활성 멤버는 자기 게이트로 평소처럼 일한다.
소유자 탈퇴와 공유 행 삭제 정책은 계속 `REVIEW_BLOCK` 이다.

### 2.4 이벤트·비동기

`@EventListener` 2, `@TransactionalEventListener` 2, `@Async` 4.
HTTP 요청에서 발행된 이벤트는 이미 게이트를 지난 뒤일 수 있으나, **발행 시점과 처리 시점
사이에 동결이 걸릴 수 있다.** 처리 시점에 다시 검사할지 결정해야 한다.

## 3. 누락 탐지를 어떻게 강제할 것인가

목록을 문서로만 두면 다음 기능이 추가될 때 조용히 어긋난다. 스키마 드리프트에서 겪은 것과
같은 실패 방식이다.

제안: **정적 검사 테스트**를 둔다.
1. `user_id` 를 다루며 쓰기하는 UseCase·Service 를 리플렉션·소스 스캔으로 수집
2. 각각이 게이트를 통과하는지(가드 호출 또는 명시적 예외 등록) 확인
3. 어느 쪽도 아니면 실패

예외 목록은 근거와 함께 등록하게 하고, `UserFkPolicyRegistry` 처럼 근거 문자열을 필수로 한다.

## 4. 실제 삭제를 열기 전 필요한 IT (codex 지정)

| # | 내용 |
|---|---|
| (a) | 게이트 ON 계정의 HTTP 쓰기 차단 |
| (b) | 스케줄러·내부 쓰기 차단 |
| (c) | 게이트 설정과 진행 중 job 의 원자성 — **완료** (`AccountDeletionRequestIT`) |
| (d) | `FOR UPDATE` 후 preflight 재검사 동시성 |

(d) 가 중요하다. preflight 와 실제 삭제 사이에 자식 행이 들어오면 그 창이 비어 있다.
삭제 트랜잭션에서 사용자 행을 잠그고 preflight 를 다시 돌려야 닫힌다.

## 5. 조사 방법의 한계

- UseCase 수집은 파일명(`*UseCase.kt`, `*Service.kt`)과 메서드명 패턴(`save/update/delete/...`)
  기반이다. 다른 이름을 쓰는 쓰기 경로는 놓칠 수 있다
- `@Scheduled` 목록에 `UserWriteGuard.kt` 가 잡혔는데 이건 **오탐**이다. KDoc 에
  `@Scheduled` 라는 단어가 들어갔을 뿐이다. 위 16개에서 제외했다
- workspace 경유 40개는 "`workspaceId` 를 언급한다"는 느슨한 기준이다. 실제 쓰기 주체
  판정은 개별 확인이 필요하다

정적 검사 테스트(§3)를 만들 때는 이 한계를 줄이는 방향으로 수집 기준을 다시 정한다.
