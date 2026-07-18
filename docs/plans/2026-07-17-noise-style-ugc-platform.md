# ongo UGC 캠페인 플랫폼 개발 구현 계획서

> 작성일: 2026-07-17  
> 기준 저장소: `/Users/bumkyuchun/work/app/ongo`  
> 목표: ongo의 기존 멀티 SNS 게시·분석 기능을 기반으로 브랜드와 크리에이터가 캠페인 단위로 거래하는 성과형 UGC 플랫폼을 구축한다.  
> 참고 제품: Noise의 캠페인, 플레이북, 크리에이터 참여, 성과형 보상 구조. 디자인·문구·브랜드 자산은 복제하지 않는다.

## 1. 문서 목적

이 문서는 아이디어 수준의 기능 목록이 아니라 현재 ongo 소스를 기준으로 다음을 결정하기 위한 실행 계획이다.

- 어떤 기존 모듈을 그대로 재사용할지
- 어떤 모듈을 캠페인 도메인에 맞게 확장하거나 대체할지
- 새로 필요한 DB, 도메인, API, 이벤트, 배치 작업과 화면은 무엇인지
- 어떤 순서로 개발해야 중간 단계에서도 배포 가능한지
- 단계별 완료 조건과 판매 전 검증 조건은 무엇인지

관리자 로그인 개편은 이번 로드맵에서 제외한다. 다만 운영자용 캠페인·정산·신고 화면은 기존 인증 체계를 전제로 설계한다.

## 2. 목표 제품 정의

### 2.1 핵심 사용자

1. 브랜드 운영자
   - 캠페인과 예산을 만들고 크리에이터에게 콘텐츠 제작을 요청한다.
   - 플레이북으로 필수 문구, 해시태그, 금지 사항과 예시를 전달한다.
   - 제출 콘텐츠를 승인하고 조회수·지출·전환을 확인한다.

2. 크리에이터
   - 참여 가능한 캠페인을 탐색하고 지원 또는 즉시 참여한다.
   - 플레이북과 브랜드 에셋을 받아 콘텐츠를 제작한다.
   - ongo에서 SNS에 게시하거나 외부 게시물 URL을 제출한다.
   - 검증된 성과에 따라 수익을 정산받는다.

3. 운영 담당자
   - 부정 트래픽, 저작권, 브랜드 안전, 분쟁과 정산 보류를 처리한다.
   - 캠페인 및 게시물 상태 변경 이력을 감사한다.

### 2.2 핵심 제품 루프

```text
브랜드 워크스페이스 생성
  → 캠페인·예산 설정
  → 플레이북·에셋 작성
  → 크리에이터 모집/참여
  → 콘텐츠 제출·승인
  → 멀티 SNS 게시
  → 지표 수집·유효 조회수 검증
  → 비용 확정·크리에이터 정산
  → 성과 분석·캠페인 최적화
```

### 2.3 MVP 성공 기준

- 브랜드가 10분 이내에 캠페인을 공개할 수 있다.
- 크리에이터가 하나의 플레이북을 선택해 콘텐츠를 제출하고 SNS에 게시할 수 있다.
- 게시물별 조회수와 예상 보상이 매일 갱신된다.
- 캠페인 예산을 초과한 정산이 발생하지 않는다.
- 모든 금액 변경과 상태 변경을 감사 로그로 추적할 수 있다.
- 실패한 SNS 동기화와 웹훅을 운영자가 재처리할 수 있다.

## 3. 현재 소스 기반 진단

### 3.1 재사용 가능한 기반

| 역량 | 현재 소스 | 활용 방안 |
|---|---|---|
| 워크스페이스 | `domain/workspace`, `WorkspaceUseCase`, `WorkspaceController` | 캠페인의 소유 조직으로 사용 |
| 팀과 권한 | `domain/team`, `TeamUseCase`, `PermissionService` | 브랜드 팀 역할과 캠페인 권한 확장 |
| 멀티 SNS 게시 | `StreamPublishUseCase`, `PublishVideoUseCase`, 플랫폼 writer/client | 승인된 UGC 직접 게시 및 외부 URL 추적 |
| 플랫폼 분석 | `AnalyticsUseCase`, `AnalyticsSyncScheduler`, `AnalyticsRepository` | 캠페인 게시물 지표 원천으로 재사용 |
| 승인 | `domain/approval`, `ApprovalUseCase`, 승인 체인 | 콘텐츠 검수 구조를 일반화해 재사용 |
| 브랜드 에셋 | `asset`, `brandkit`, `template` 모듈 | 플레이북 에셋·템플릿 저장 |
| 브랜드 협업 | `branddeal` | 기존 개인 크리에이터 딜의 캠페인 이관 소스 |
| 인플루언서 매칭 | `influencermatch` | 캠페인 추천 후보 산정에 활용 |
| 크리에이터 네트워크 | `creatornetwork` | 크리에이터 프로필과 연결 관계의 초기 데이터 |
| 결제 | `PaymentService`, `TossPaymentsClient`, Paddle | 브랜드 예산 충전·환불에 활용 |
| 수익 분석 | `revenue`, `revenueanalyzer`, `revenuesplit` | 확정 정산 이후 표시 계층에 활용 |
| 웹훅 | `WebhookUseCase`, `WebhookEventRepository` | 캠페인·게시·정산 이벤트 외부 전달 |
| 활동 로그 | `activitylog` | 감사 로그 UI 기반 |
| 에이전시 | `agency` | 다수 브랜드·크리에이터 운영 모델에 활용 |

### 3.2 그대로 사용할 수 없는 부분

1. `creatormarketplace`
   - 도메인과 SQL은 있지만 application/API 구현이 없다.
   - 현재 모델은 고정 가격 서비스 주문이며 성과형 캠페인에 필요한 예산, CPM, 참여, 게시물, 검증 구조가 없다.
   - 캠페인 핵심 도메인으로 사용하지 않고 기존 listing/order 데이터를 이관하는 레거시 입력으로 취급한다.

2. `influencermatch`, `agency`, `creatornetwork`
   - API 컨트롤러가 `@Profile("wip")`이다.
   - 판매 기능으로 승격하기 전에 영속성, 소유권 검증, 페이지네이션, 테스트를 보강해야 한다.

3. 결제와 수익
   - `Payment`는 사용자 결제 내역이며 캠페인별 예산 예약·차감 원장이 아니다.
   - `RevenueSplit`도 분배 결과 표현에 가깝고, 불변 원장·중복 방지·보류·취소 기능이 부족하다.
   - 캠페인 자금 원장을 별도로 만들고 기존 결제는 입금 근거로만 연결한다.

4. 승인
   - 현재 `Approval`이 `videoId`에 강하게 결합되어 있다.
   - `resourceType`, `resourceId` 기반의 범용 검수 대상으로 점진 확장해야 한다.

5. DB 마이그레이션
   - 실제 Flyway 경로는 `backend/onGo-api/src/main/resources/db/migration`이다.
   - 루트의 `backend/sql`에는 중복 또는 과거 버전 SQL이 있으므로 신규 기능은 여기에 추가하지 않는다.
   - 다음 신규 마이그레이션은 현재 최신 V46 이후인 V47부터 시작한다.

6. 프런트엔드 내비게이션
   - 기존 `router/index.ts`와 `AppLayout.vue`는 크리에이터 운영 도구 중심이다.
   - 역할별 화면을 무작정 추가하지 않고 브랜드 모드와 크리에이터 모드를 명확히 분리해야 한다.

### 3.3 아키텍처 부채 선행 조치

- 55개 `@Profile("wip")` 컨트롤러를 일괄 활성화하지 않는다.
- UGC MVP에서 사용하는 모듈만 영속성·인가·테스트를 갖춘 후 프로필을 제거한다.
- 사용자 ID와 워크스페이스 ID를 혼용하는 기존 코드 경로를 캠페인 개발 전에 정리한다.
- 금액은 `Long` 최소 화폐 단위로 저장하고 `Double`을 사용하지 않는다.
- 캠페인 상태와 금액 변경은 직접 UPDATE하지 않고 도메인 메서드와 원장 이벤트를 통한다.

## 4. 목표 아키텍처

### 4.1 신규 bounded context

```text
campaign
 ├─ Campaign
 ├─ CampaignBudget
 ├─ CampaignTargeting
 └─ CampaignRepository

playbook
 ├─ Playbook
 ├─ PlaybookStep / Slide
 ├─ PlaybookAsset
 └─ PlaybookRepository

creatoroffer
 ├─ CampaignOffer
 ├─ CampaignApplication
 ├─ CampaignParticipant
 └─ CreatorEligibilityPolicy

submission
 ├─ ContentSubmission
 ├─ SubmissionAsset
 ├─ CampaignPost
 ├─ PostMetricSnapshot
 └─ PostVerification

settlement
 ├─ CampaignWallet
 ├─ LedgerEntry
 ├─ CreatorEarning
 ├─ PayoutRequest
 └─ SettlementPolicy

trustsafety
 ├─ FraudSignal
 ├─ ModerationCase
 ├─ Dispute
 └─ BrandSafetyDecision
```

### 4.2 의존 방향

```text
API → Application → Domain ← Infrastructure
                     ↑
          platform ports / payment ports
```

- `campaign`은 SNS SDK나 PG 클라이언트를 직접 참조하지 않는다.
- 게시와 지표 수집은 `CampaignPostPort`와 도메인 이벤트로 연결한다.
- 정산은 분석 테이블을 직접 조회하지 않고 검증 완료 이벤트를 소비한다.
- 비동기 처리는 초기에는 기존 Spring event/scheduler를 사용하되, 원장 이벤트는 outbox로 보장한다.

### 4.3 주요 이벤트

- `CampaignPublished`
- `CreatorApplied`
- `CreatorAccepted`
- `SubmissionCreated`
- `SubmissionApproved`
- `CampaignPostPublished`
- `PostMetricsCollected`
- `PostMetricsVerified`
- `EarningAccrued`
- `CampaignBudgetExhausted`
- `PayoutRequested`
- `PayoutCompleted`
- `DisputeOpened`

모든 이벤트는 `eventId`를 가지며 소비자는 idempotency 테이블 또는 unique key로 중복 처리하지 않는다.

## 5. 데이터베이스 설계

### 5.1 V47 캠페인과 플레이북

신규 테이블:

- `ugc_campaigns`
  - `id`, `workspace_id`, `name`, `description`, `status`
  - `objective`, `total_budget`, `daily_budget`, `currency`
  - `pricing_model`, `rate_per_thousand`, `max_reward_per_creator`
  - `start_at`, `end_at`, `application_deadline`
  - `target_platforms JSONB`, `target_countries`, `target_categories`
  - `created_by`, `published_at`, `created_at`, `updated_at`, `version`
- `ugc_playbooks`
  - 캠페인 연결, 유형(UGC_VIDEO, SLIDESHOW, TESTIMONIAL, HOOK_DEMO), 제목, 상태, 버전
- `ugc_playbook_steps`
  - 순서, step type, hook, instruction, overlay text, duration, example URL
- `ugc_playbook_assets`
  - 기존 asset ID 또는 외부 URL, 사용 목적, 필수 여부
- `ugc_campaign_rules`
  - 필수 해시태그, 필수 멘션, CTA, 금지 표현, 최소/최대 길이

필수 제약:

- 예산과 단가는 0 이상
- `end_at > start_at`
- 공개 캠페인은 하나 이상의 플랫폼과 활성 플레이북 필요
- optimistic lock용 `version` 컬럼
- `workspace_id`, `status`, `start_at/end_at` 복합 인덱스

### 5.2 V48 참여와 제출

- `ugc_campaign_applications`
- `ugc_campaign_participants`
- `ugc_content_submissions`
- `ugc_submission_assets`
- `ugc_submission_reviews`
- `ugc_campaign_posts`

핵심 unique key:

- `(campaign_id, creator_id)` application 중복 방지
- `(campaign_id, creator_id, platform, platform_post_id)` 게시물 중복 방지
- 제출물 revision은 `(submission_id, revision_no)`로 관리

### 5.3 V49 지표와 검증

- `ugc_post_metric_snapshots`
- `ugc_post_verifications`
- `ugc_fraud_signals`
- `ugc_metric_sync_jobs`

원본 지표와 인정 지표를 분리한다.

- 원본: SNS API가 반환한 조회·좋아요·댓글·공유
- 인정: 중복·비정상 증가·캠페인 기간·상한을 반영한 billable views

### 5.4 V50 예산·원장·정산

- `ugc_campaign_wallets`
- `ugc_ledger_entries`
- `ugc_creator_earnings`
- `ugc_payout_accounts`
- `ugc_payout_requests`
- `ugc_disputes`

원장 규칙:

- entry는 수정·삭제하지 않는다. 취소는 반대 방향 entry를 추가한다.
- `idempotency_key` unique
- `AVAILABLE`, `RESERVED`, `SPENT`, `REFUNDABLE` 잔액을 분리한다.
- 게시물별 누적 earning이 캠페인 잔액과 크리에이터 상한을 초과하지 않도록 transaction lock을 사용한다.

### 5.5 V51 outbox와 감사

- `ugc_outbox_events`
- `ugc_audit_logs`
- `ugc_moderation_cases`

outbox publisher는 처리 성공 후 `published_at`을 기록하고 exponential backoff로 재시도한다.

## 6. 백엔드 구현 계획

### 6.1 패키지 및 파일 구조

각 기능은 기존 모듈 구조를 따른다.

```text
backend/onGo-domain/src/main/kotlin/com/ongo/domain/campaign/
backend/onGo-application/src/main/kotlin/com/ongo/application/campaign/
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/CampaignJooqRepository.kt
backend/onGo-api/src/main/kotlin/com/ongo/api/campaign/CampaignController.kt
```

동일한 구조로 `playbook`, `creatoroffer`, `submission`, `settlement`, `trustsafety`를 추가한다.

### 6.2 API 계약

#### 브랜드 캠페인

- `POST /api/v1/campaigns`
- `GET /api/v1/campaigns`
- `GET /api/v1/campaigns/{id}`
- `PUT /api/v1/campaigns/{id}`
- `POST /api/v1/campaigns/{id}/publish`
- `POST /api/v1/campaigns/{id}/pause`
- `POST /api/v1/campaigns/{id}/resume`
- `POST /api/v1/campaigns/{id}/close`
- `GET /api/v1/campaigns/{id}/dashboard`

#### 플레이북

- `POST /api/v1/campaigns/{campaignId}/playbooks`
- `PUT /api/v1/playbooks/{id}`
- `POST /api/v1/playbooks/{id}/duplicate`
- `POST /api/v1/playbooks/{id}/publish`
- `POST /api/v1/playbooks/{id}/ai-draft`

#### 크리에이터 오퍼

- `GET /api/v1/creator/offers`
- `GET /api/v1/creator/offers/{campaignId}`
- `POST /api/v1/creator/offers/{campaignId}/apply`
- `DELETE /api/v1/creator/offers/{campaignId}/application`
- `GET /api/v1/campaigns/{id}/applications`
- `POST /api/v1/campaigns/{id}/applications/{applicationId}/accept`
- `POST /api/v1/campaigns/{id}/applications/{applicationId}/reject`

#### 콘텐츠 제출과 게시

- `POST /api/v1/campaigns/{id}/submissions`
- `GET /api/v1/campaigns/{id}/submissions`
- `GET /api/v1/submissions/{id}`
- `POST /api/v1/submissions/{id}/request-review`
- `POST /api/v1/submissions/{id}/approve`
- `POST /api/v1/submissions/{id}/request-changes`
- `POST /api/v1/submissions/{id}/publish`
- `POST /api/v1/submissions/{id}/external-post`

`publish`는 기존 `StreamPublishUseCase`/`PublishVideoUseCase`를 감싼 application orchestration으로 구현한다. 캠페인 모듈이 플랫폼 client를 직접 호출하지 않는다.

#### 지표·정산

- `GET /api/v1/campaigns/{id}/posts`
- `POST /api/v1/campaign-posts/{id}/sync`
- `GET /api/v1/campaign-posts/{id}/metrics`
- `GET /api/v1/campaigns/{id}/ledger`
- `GET /api/v1/creator/earnings`
- `POST /api/v1/creator/payouts`
- `GET /api/v1/creator/payouts`
- `POST /api/v1/disputes`

### 6.3 권한 확장

`Permission.kt`에 다음을 추가한다.

- `CAMPAIGN_CREATE`, `CAMPAIGN_READ`, `CAMPAIGN_UPDATE`, `CAMPAIGN_PUBLISH`
- `PLAYBOOK_MANAGE`
- `CREATOR_REVIEW`, `SUBMISSION_REVIEW`
- `CAMPAIGN_BILLING_READ`, `CAMPAIGN_BILLING_MANAGE`
- `PAYOUT_REQUEST`, `PAYOUT_REVIEW`
- `MODERATION_MANAGE`

권한 판단은 URL의 userId가 아니라 campaign → workspace → membership 순으로 검증한다.

### 6.4 기존 모듈 연결

1. 업로드
   - `ContentSubmission` 승인 후 기존 업로드 API 호출
   - 결과의 `videoId`와 플랫폼별 `videoUploadId`를 `ugc_campaign_posts`에 연결
   - 외부 앱에서 직접 게시한 경우 URL과 platform post ID를 등록

2. 분석
   - 기존 analytics sync가 저장한 플랫폼 지표를 campaign post snapshot으로 projection
   - 캠페인 지표 배치는 원본 분석 저장과 결합하지 않고 별도 consumer로 구현

3. 승인
   - 1차 MVP는 submission 전용 review 테이블 사용
   - 이후 기존 `Approval`을 범용 resource approval로 마이그레이션

4. 결제
   - Toss/Paddle 결제 완료 → campaign wallet `DEPOSIT` entry
   - 환불 → 사용 가능 잔액 범위에서 reversal entry
   - PG webhook과 ledger entry를 동일 트랜잭션에서 outbox로 연결

5. 웹훅
   - `campaign.published`, `submission.approved`, `post.verified`, `payout.completed` 추가

### 6.5 지표 수집 정책

- 게시 후 1시간: 10분 간격
- 1~24시간: 1시간 간격
- 2~7일: 6시간 간격
- 8~30일: 1일 간격
- 삭제·비공개·API 권한 상실 시 상태와 원인을 별도 기록
- 플랫폼 API rate limit을 채널·플랫폼 단위로 관리
- retryable 오류와 permanent 오류를 구분

### 6.6 보상 계산

```text
증분 인정 조회수 = 현재 인정 누적 조회수 - 직전 정산 누적 조회수
증분 보상 = floor(증분 인정 조회수 × CPM / 1000)
확정 보상 = min(증분 보상, 크리에이터 잔여 상한, 캠페인 가용 잔액)
```

- 모든 계산 입력과 결과를 snapshot에 남긴다.
- CPM 변경은 변경 이후 조회수부터 적용되도록 rate version을 저장한다.
- 조회수 감소 시 이미 지급된 금액을 자동 차감하지 않고 moderation case를 만든다.

## 7. 프런트엔드 구현 계획

### 7.1 역할별 정보 구조

#### 브랜드 모드

- 개요
- 캠페인
- 플레이북
- 콘텐츠 검수
- 크리에이터
- 성과·지출
- 팀·설정

#### 크리에이터 모드

- 오퍼 탐색
- 참여 캠페인
- 제작 작업
- 게시물·성과
- 수익·출금
- 프로필·채널

현재 `AppLayout.vue`에 메뉴를 모두 누적하지 않고 workspace type 또는 mode switch에 따라 메뉴를 구성한다.

### 7.2 신규 라우트

```text
/campaigns
/campaigns/new
/campaigns/:id
/campaigns/:id/playbooks/:playbookId
/campaigns/:id/applications
/campaigns/:id/submissions
/campaigns/:id/analytics
/creator/offers
/creator/offers/:id
/creator/tasks
/creator/earnings
/moderation
```

### 7.3 신규 화면과 컴포넌트

- `CampaignListView.vue`
- `CampaignBuilderView.vue`
- `CampaignDetailView.vue`
- `PlaybookBuilderView.vue`
- `CampaignApplicationsView.vue`
- `SubmissionReviewView.vue`
- `CreatorOffersView.vue`
- `CreatorTaskView.vue`
- `CreatorEarningsView.vue`
- `CampaignAnalyticsView.vue`

핵심 컴포넌트:

- 캠페인 상태 헤더
- 예산 소진 막대와 예상 소진일
- CPM/최대 보상 입력기
- 플레이북 step/slideshow builder
- 필수 문구·금지 표현 검사기
- SNS 미리보기
- 제출 revision 비교
- 게시물 검증 상태 카드
- 원장 타임라인
- 출금 가능·보류·예정 수익 요약

### 7.4 UX 원칙

- 캠페인 공개, 예산 변경, 지급은 결과 preview 후 확인한다.
- 저장 중/게시 중/검증 중/정산 보류를 같은 “진행 중”으로 표현하지 않는다.
- 크리에이터에게 예상 수익과 확정 수익의 차이를 명확히 보여준다.
- 모바일 크리에이터 흐름은 44px 이상 터치 영역과 하단 주요 CTA를 사용한다.
- 캠페인 생성은 임시 저장을 지원하고 새로고침 후 복구한다.
- 플랫폼별 요구사항과 미연동 채널을 제출 전에 검사한다.

## 8. 단계별 개발 로드맵

### Phase 0 — 기반 정리 (2주)

목표: 캠페인 개발 전에 워크스페이스·권한·DB 경로를 안정화한다.

- canonical Flyway 경로 확정 및 중복 SQL 정리 지침 추가
- workspace 접근 검증 공통 서비스 구현
- `Permission` 캠페인 권한 추가
- 캠페인 feature flag 추가
- 돈·시간·상태 값 객체와 공통 idempotency 정책 작성
- 실제 사용할 wip 모듈의 repository/API 테스트 보강

완료 기준:

- 다른 workspace ID로 캠페인 API 접근 시 403
- 신규 migration이 빈 DB와 기존 DB 모두에서 성공
- 캠페인 feature flag off 시 기존 ongo 동작에 변화 없음

### Phase 1 — 캠페인·플레이북 MVP (3주)

- V47 마이그레이션
- campaign/playbook domain, repository, use case, controller
- 캠페인 builder와 플레이북 builder
- 브랜드 에셋·템플릿 연결
- draft/publish/pause/close 상태 머신

완료 기준:

- 브랜드가 draft 캠페인을 저장하고 공개 가능
- 유효하지 않은 예산·기간·플랫폼은 UI와 API 양쪽에서 차단
- 공개 후 핵심 조건 변경은 새 버전 또는 명시적 확인 필요

### Phase 2 — 크리에이터 오퍼·제출·검수 (3주)

- V48 마이그레이션
- 오퍼 검색·지원·수락
- 제출 revision과 검수 의견
- 승인 후 기존 멀티 SNS 게시 연결
- 외부 게시 URL 제출
- 알림과 웹훅

완료 기준:

- 브랜드와 크리에이터 계정으로 전체 제출 흐름 E2E 통과
- 지원 중복, 미참여 제출, 미승인 게시 차단
- 플랫폼 일부 실패 시 성공 플랫폼 상태 유지 및 실패 플랫폼 재시도 가능

### Phase 3 — 지표·검증·캠페인 분석 (3주)

- V49 마이그레이션
- 지표 sync scheduler와 metric snapshot
- 필수 해시태그·게시 기간·게시물 삭제 검사
- 기본 fraud signal
- 캠페인 대시보드와 크리에이터 성과 화면

완료 기준:

- 동일 원본 지표를 재수집해도 중복 누적되지 않음
- 게시물 삭제·비공개·토큰 만료를 구분해 표시
- 캠페인 합계와 게시물 합계가 일치

### Phase 4 — 예산·정산·출금 (4주)

- V50/V51 마이그레이션
- wallet/ledger/earning/payout 도메인
- Toss/Paddle 입금 연결
- 예산 예약, 차감, 환불, 상한 처리
- 출금 계좌와 지급 요청
- 이의제기·보류

완료 기준:

- 동시 정산에서도 캠페인 잔액이 음수가 되지 않음
- 동일 웹훅·지표 이벤트 반복 수신 시 금액 중복 없음
- 원장 합계와 표시 잔액 일치
- 결제·환불·정산 감사 추적 가능

### Phase 5 — 운영·AI·공개 API (3주)

- moderation queue와 fraud rule 강화
- 플레이북 AI 초안·슬라이드 이미지 생성
- Reporting API read endpoints
- MCP는 read-only부터 제공, write는 preview/confirm/audit 적용
- 캠페인 추천과 예산 소진 예측

완료 기준:

- 운영자가 실패 job, 신고, 분쟁을 한 화면에서 처리
- 외부 API token scope와 rate limit 적용
- AI write 작업은 확인 없이는 상태·예산 변경 불가

총 예상: 제품팀 4~5명 기준 약 18주. 결제·정산을 제외한 캠페인/콘텐츠 MVP는 Phase 0~3, 약 11주다.

## 9. 테스트 전략

### 9.1 도메인 단위 테스트

- 캠페인 상태 전이
- 예산·CPM·보상 상한
- 지원 자격과 중복 지원
- 제출 revision
- 지표 증분 계산
- 원장 불변성과 reversal

### 9.2 repository 통합 테스트

- PostgreSQL Testcontainers
- unique key와 optimistic lock
- 동시 정산 및 잔액 잠금
- outbox polling/재시도

현재 Docker가 없는 환경에서 인프라 테스트 2개가 실행되지 않는 문제가 있으므로 CI에는 Docker runner를 필수로 둔다.

### 9.3 API 계약 테스트

- workspace 소유권과 역할별 권한
- pagination/filter/sort
- idempotency key 반복 요청
- 잘못된 상태 전이의 409 응답
- validation error의 일관된 error code

### 9.4 프런트엔드 테스트

- Vitest: store, 계산, 상태 표시
- Vue Testing Library: 캠페인 builder와 검수 흐름
- Playwright: 브랜드 생성 → 크리에이터 참여 → 승인 → 게시 → 지표 → 정산
- 모바일 viewport 및 키보드 접근성

### 9.5 외부 연동 테스트

- SNS sandbox/test channel 실제 게시
- 토큰 만료·rate limit·삭제 게시물
- Toss 테스트 결제·취소·중복 webhook
- 저장소 presigned URL 만료와 대용량 파일

## 10. 관측성·운영

필수 metric:

- 캠페인 공개 성공률
- 오퍼 조회→지원 전환율
- 제출→승인 소요 시간
- 플랫폼별 게시 성공률
- 지표 sync 지연과 실패율
- 검증 제외 조회수 비율
- campaign wallet 가용/예약/사용 잔액 불일치
- payout 처리 시간과 실패율

구조화 로그 공통 필드:

- `workspaceId`, `campaignId`, `creatorId`, `submissionId`, `campaignPostId`, `eventId`

운영 알림:

- 잔액 불일치
- outbox 적체
- SNS sync 6시간 이상 지연
- 지급 실패
- 비정상 조회수 급증
- 캠페인 예산 80/95/100% 소진

## 11. 보안·법무·정산 체크

- 브랜드 에셋과 미공개 플레이북은 참여 승인 전 접근 불가
- 제출 파일 다운로드 URL은 짧은 만료 presigned URL 사용
- 캠페인별 콘텐츠 사용권, 광고 활용권, 게시 유지 기간 동의 기록
- 개인정보와 지급 계좌 정보 암호화 및 접근 감사
- 웹훅 서명 검증과 replay 방지
- 플랫폼 약관상 자동 게시·지표 저장·광고 콘텐츠 표시 의무 검토
- 한국 출시 시 원천징수, 사업소득 처리, 전자금융/선불금 구조를 법률·세무 검토
- 정산 지급대행사가 확정되기 전 자체 출금 기능을 운영 환경에서 활성화하지 않음

## 12. 데이터 이관 전략

- `brand_deals` → 선택적으로 `ugc_campaigns` draft 변환
- `collab_requests` → campaign application history 또는 archived collaboration으로 보존
- `marketplace_listings/orders` → 신규 캠페인 테이블에 직접 1:1 이관하지 않음
- 기존 `video`/`video_uploads` → 새 캠페인 게시물에 nullable reference로 연결
- 기존 revenue 데이터는 과거 통계로 유지하고 신규 earning ledger와 혼합하지 않음

모든 이관은 dry-run report, row count, 실패 row export, rollback SQL을 포함한다.

## 13. 출시 전략

1. 내부 workspace만 feature flag 활성화
2. 테스트 브랜드 2곳·크리에이터 20명 closed beta
3. 결제 없는 포인트형 캠페인으로 게시·검증 안정화
4. 예산 충전과 정산을 제한 금액으로 활성화
5. 운영 지표와 분쟁 처리 SLA 확보 후 공개 출시

기능 flag:

- `ugc.campaign.enabled`
- `ugc.creator-offers.enabled`
- `ugc.metrics-verification.enabled`
- `ugc.wallet.enabled`
- `ugc.payout.enabled`

## 14. 판매 가능 완료 기준

- 브랜드·크리에이터 핵심 E2E 20회 연속 성공
- 지원 SNS별 테스트 계정 실제 게시 및 지표 동기화 성공
- 결제·환불·중복 webhook·동시 정산 시나리오 통과
- 캠페인 잔액 불변성 property test 통과
- P0/P1 보안 취약점 0건
- 접근성 WCAG 2.1 AA 핵심 흐름 통과
- 개인정보 처리방침·이용약관·콘텐츠 사용권 동의 적용
- 운영 runbook, 장애 복구, 정산 reconciliation 절차 작성
- 7일 이상 beta 운영 중 원장 불일치 0건

## 15. 즉시 착수 백로그

### Sprint 1

1. ADR: 캠페인 bounded context와 원장 분리 결정
2. workspace 접근 검증 공통 컴포넌트
3. V47 캠페인·플레이북 migration
4. Campaign/Playbook domain 및 상태 머신 테스트
5. repository와 API skeleton
6. 프런트 캠페인 route/store/type
7. feature flag

### Sprint 2

1. Campaign builder
2. Playbook builder
3. asset/template 연결
4. publish validation
5. 캠페인 목록·상세·상태 변경
6. API 계약 및 접근 권한 테스트

### Sprint 3

1. V48 참여·제출 migration
2. creator offer 목록과 필터
3. application/participant state machine
4. submission revision과 review
5. 승인 후 기존 멀티 SNS 게시 orchestration

## 16. 의사결정이 필요한 항목

개발 착수 전에 제품 책임자가 확정해야 한다.

1. 1차 시장: 한국만 또는 글로벌
2. 브랜드 과금: CPM, 고정 건당, 혼합형 중 MVP 범위
3. 캠페인 참여: 즉시 참여 또는 브랜드 승인제
4. 크리에이터 출금: Toss Payments 외 지급대행사 선정
5. 최소 출금액과 정산 주기
6. 지원 플랫폼 우선순위
7. 콘텐츠를 ongo가 직접 게시할지, 크리에이터가 외부 게시 후 URL만 제출할지
8. 앱 설치·구매 전환 attribution 제공 범위

기본 권장안은 한국 시장, 승인제 참여, CPM+크리에이터별 상한, YouTube/TikTok/Instagram 우선, 직접 게시와 외부 URL 제출 병행이다.

## 17. 최종 권고

Noise와 유사한 외형을 먼저 만드는 대신 Phase 0~3의 거래 핵심 루프를 우선 구현한다. ongo는 이미 콘텐츠 제작·게시·분석 기능이 강하므로, 경쟁력은 크리에이터를 많이 보여주는 데 있지 않고 다음 연결을 얼마나 신뢰성 있게 제공하느냐에 있다.

```text
플레이북 생성 → 콘텐츠 제작 → 멀티 SNS 게시 → 성과 검증 → 정산
```

첫 출시에서는 출금 자동화보다 캠페인·콘텐츠·지표의 정확성을 우선하고, 원장 검증이 끝난 뒤 실화폐 정산을 활성화한다.
