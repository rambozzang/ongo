# ongo 유료 파일럿 MVP 개발 구현 계획서

> 작성일: 2026-07-17  
> 구현 대상: `/Users/bumkyuchun/work/app/ongo`  
> 구현 주체: Claude Code  
> 목표 기간: 6주  
> 목표: K-뷰티·커머스 브랜드 또는 대행사가 실제 UGC 캠페인을 운영하고 비용을 지불할 수 있는 최소 제품을 출시한다.

## 0. Claude Code 실행 지침

이 문서는 아이디어 문서가 아니라 구현 순서와 완료 조건을 정의하는 작업 명세다. Claude Code는 아래 규칙을 지킨다.

1. 루트 `CLAUDE.md`를 먼저 읽고 기존 Clean Architecture와 코딩 규칙을 유지한다.
2. 작업 시작 전 관련 기존 구현을 검색하고 같은 패턴을 재사용한다.
3. DB 접근은 jOOQ만 사용한다. JPA/Hibernate를 추가하지 않는다.
4. 신규 Flyway SQL은 `backend/onGo-api/src/main/resources/db/migration`에만 추가한다.
5. 현재 최신 V46 다음인 V47부터 순서대로 작성한다.
6. API 응답은 모두 `ResData<T>`를 사용하고 `/api/v1/` 접두사를 유지한다.
7. 모든 데이터 접근은 로그인 사용자와 `workspaceId`의 관계를 서버에서 검증한다.
8. 금액은 최소 화폐 단위의 `Long`으로 처리하고 `Double`을 사용하지 않는다.
9. 프런트엔드는 기존 Vue 3, Pinia, Vue Router, Tailwind 패턴과 공통 컴포넌트를 재사용한다.
10. 사용자 화면을 추가하면 `UserManualView.vue`의 한국어·영어 안내도 함께 갱신한다.
11. 각 작업은 테스트와 빌드가 통과한 상태에서 끝낸다. 관련 없는 기존 코드는 대규모 리팩터링하지 않는다.
12. 아래 Sprint 순서를 지키고, 선행 Sprint의 완료 조건이 충족되지 않으면 다음 Sprint로 넘어가지 않는다.

## 1. 사업 가설과 MVP 경계

### 1.1 검증할 가설

> 브랜드 또는 대행사는 여러 SNS의 UGC 캠페인 제작·승인·게시·성과 집계를 하나의 업무 흐름으로 처리할 수 있다면 월 구독료 또는 캠페인 운영비를 지불한다.

### 1.2 핵심 고객

- 1순위: K-뷰티 및 소비재 브랜드를 여러 곳 관리하는 마케팅 대행사
- 2순위: 월 10건 이상 숏폼 콘텐츠를 제작하는 D2C 브랜드
- 초기 운영 지역: 대한민국
- 지원 언어: 한국어 우선, 기존 i18n 구조를 활용한 영어 병기

### 1.3 6주 MVP에 반드시 포함할 것

- 브랜드 워크스페이스 단위 캠페인 생성·수정·공개·종료
- 캠페인 플레이북과 필수 제작 규칙
- 초대 링크 또는 이메일 기반 크리에이터 모집
- 크리에이터 지원·수락·거절
- 콘텐츠 파일/URL 제출, 수정 요청, 승인
- 승인 콘텐츠의 기존 멀티 SNS 게시 흐름 연결
- 외부에서 이미 게시한 SNS URL 등록
- 캠페인·크리에이터·게시물별 성과 조회
- 고정 제작비와 수동 확정 보상액 관리
- 지급 대상 CSV 내보내기
- 주요 상태 변경 감사 로그

### 1.4 이번 MVP에서 구현하지 않을 것

- 관리자 로그인 개편
- 공개형 크리에이터 마켓플레이스
- AI 자동 크리에이터 추천과 자동 매칭
- 조회수 기반 자동 송금, 에스크로, 전자지갑
- 세금계산서·원천징수 자동화
- 고급 부정 트래픽 탐지
- 캠페인별 별도 채팅
- 모바일 네이티브 앱
- 55개 `@Profile("wip")` 컨트롤러 일괄 활성화
- 기존 `creatormarketplace`를 캠페인 핵심 모델로 확장

## 2. 성공·중단 기준

### 2.1 제품 완료 기준

- 브랜드가 10분 이내에 캠페인을 작성하고 초대 링크를 발급한다.
- 크리에이터가 링크 접속 후 5분 이내에 지원하고 제출 요구사항을 확인한다.
- 브랜드가 제출물을 승인하거나 수정 요청하고 모든 이력을 확인한다.
- 승인된 영상이 기존 게시 기능으로 전달되며 플랫폼별 결과가 캠페인에 연결된다.
- 게시물 성과와 지급 확정 금액을 캠페인 대시보드 및 CSV에서 확인한다.
- 다른 워크스페이스 사용자가 URL의 ID를 조작해도 데이터를 조회·변경할 수 없다.

### 2.2 사업 검증 기준

출시 후 8주 안에 다음을 측정한다.

- 고객 인터뷰 20곳 이상
- 유료 파일럿 3곳 이상
- 계약 또는 선결제 합계 1,000만원 이상
- 고객당 실제 캠페인 2개 이상 반복
- 기존 스프레드시트/메신저 운영 대비 업무시간 50% 이상 절감
- 캠페인당 제출 완료율 70% 이상

유료 파일럿이 없으면 자동 정산과 공개 마켓플레이스 개발을 시작하지 않는다.

## 3. 현재 소스 활용 방침

| 현재 기능 | 처리 방침 |
|---|---|
| `workspace`, `team`, `PermissionService` | 캠페인 소유권과 브랜드 팀 권한의 기준으로 재사용 |
| `StreamPublishUseCase`, `PublishVideoUseCase` | 승인된 제출물의 SNS 게시 실행에 재사용 |
| `AnalyticsUseCase`, `AnalyticsSyncScheduler` | 게시물 지표 원천으로 재사용 |
| `asset`, `brandkit`, `template` | 플레이북 자료와 브랜드 에셋에 재사용 |
| `approval` | 기존 video 결합을 건드리지 않고 MVP 제출 검수는 신규 submission review로 구현 |
| `activitylog` | 캠페인 상태 변경 기록에 연결 |
| `webhook` | 파일럿 고객의 외부 자동화가 필요할 때 캠페인 이벤트를 확장 |
| `payment`, `revenue` | MVP 자동 지급에는 사용하지 않고 조회·CSV만 제공 |
| `creatormarketplace` | 고정가 서비스 모델이므로 이번 구현에서 사용하지 않음 |
| `influencermatch`, `creatornetwork`, `agency` | WIP 상태를 유지하고 이번 MVP의 필수 의존성으로 만들지 않음 |

## 4. 목표 업무 흐름

```text
브랜드: 캠페인 초안 → 플레이북 작성 → 공개/초대
                                         ↓
크리에이터: 초대 접속 → 지원 → 수락 확인 → 콘텐츠 제출
                                         ↓
브랜드: 제출 검토 → 수정 요청/승인 → 게시 요청 또는 외부 URL 등록
                                         ↓
시스템: SNS 게시 상태 연결 → 지표 동기화 → 지급액 수동 확정 → CSV 출력
```

### 4.1 상태 모델

`CampaignStatus`

```text
DRAFT → RECRUITING → ACTIVE → COMPLETED
                    ↘ PAUSED
DRAFT/RECRUITING/PAUSED → CANCELLED
```

`ApplicationStatus`

```text
APPLIED → ACCEPTED | REJECTED | WITHDRAWN
```

`SubmissionStatus`

```text
DRAFT → SUBMITTED → CHANGES_REQUESTED → SUBMITTED → APPROVED | REJECTED
APPROVED → PUBLISHING → PUBLISHED | PUBLISH_FAILED
```

상태 변경은 엔티티의 명시적 도메인 메서드로만 수행하고 허용되지 않은 전이는 예외 처리한다.

## 5. 데이터베이스 구현

### 5.1 V47 — 캠페인과 플레이북

파일: `V47__create_ugc_campaigns_and_playbooks.sql`

테이블:

- `ugc_campaigns`
  - `id UUID PK`
  - `workspace_id UUID NOT NULL`
  - `name VARCHAR(150) NOT NULL`
  - `description TEXT`
  - `status VARCHAR(30) NOT NULL`
  - `objective VARCHAR(50) NOT NULL`
  - `total_budget BIGINT NOT NULL DEFAULT 0`
  - `currency VARCHAR(3) NOT NULL DEFAULT 'KRW'`
  - `fixed_reward_per_creator BIGINT NOT NULL DEFAULT 0`
  - `start_at TIMESTAMPTZ`, `end_at TIMESTAMPTZ`
  - `created_by UUID NOT NULL`
  - `created_at`, `updated_at`, `version BIGINT`
- `ugc_playbooks`
  - 캠페인당 MVP에서는 활성 플레이북 1개
  - `title`, `summary`, `content_type`, `revision`, timestamps
- `ugc_playbook_steps`
  - `playbook_id`, `sort_order`, `step_type`, `title`, `instruction`, `example_url`, `required`
- `ugc_campaign_rules`
  - `campaign_id`, `rule_type`, `value`, `required`, `sort_order`
- `ugc_campaign_invites`
  - `campaign_id`, 해시 처리된 `token_hash`, `expires_at`, `max_uses`, `used_count`, `active`

필수 제약 및 인덱스:

- 예산과 보상액은 0 이상
- 종료일은 시작일보다 이후
- `(workspace_id, status, created_at DESC)` 인덱스
- `(campaign_id, sort_order)` unique
- 초대 토큰 원문은 DB에 저장하지 않음

### 5.2 V48 — 지원, 참여, 제출과 검수

파일: `V48__create_ugc_applications_and_submissions.sql`

- `ugc_campaign_applications`
  - `(campaign_id, creator_id)` unique
  - 지원 메시지, 포트폴리오 URL, 상태, 처리자, 처리 시각
- `ugc_campaign_participants`
  - 수락된 사용자, agreed reward, joined_at, active
  - `(campaign_id, creator_id)` unique
- `ugc_content_submissions`
  - participant, revision, caption, status, submitted_at, approved_at
- `ugc_submission_assets`
  - 기존 asset/video ID를 nullable FK 또는 명시적 resource type/id로 연결
  - 외부 파일 URL을 직접 신뢰하지 말고 허용된 저장소 URL만 사용
- `ugc_submission_reviews`
  - reviewer, decision, comment, created_at
- `ugc_campaign_posts`
  - submission, platform, 기존 publish/video 참조, external_post_url, platform_post_id, status
  - `(campaign_id, platform, platform_post_id)` unique where not null

### 5.3 V49 — 성과, 보상 확정과 감사

파일: `V49__create_ugc_metrics_rewards_and_audit.sql`

- `ugc_post_metric_snapshots`
  - `campaign_post_id`, `captured_at`, views, likes, comments, shares
  - `(campaign_post_id, captured_at)` unique
- `ugc_reward_confirmations`
  - participant당 한 행
  - base_amount, bonus_amount, total_amount, status, note, confirmed_by, confirmed_at
  - status: `DRAFT`, `CONFIRMED`, `PAID_EXTERNALLY`, `CANCELLED`
- `ugc_audit_events`
  - workspace, campaign, actor, action, resource_type/id, before/after JSONB, created_at

이번 단계에는 wallet/ledger/payout 테이블을 만들지 않는다. 실제 PG 송금이 추가될 때 별도 마이그레이션으로 설계한다.

### 5.4 jOOQ 생성

각 마이그레이션 적용 후 프로젝트의 기존 DB 및 jOOQ 생성 절차를 확인하고 다음을 실행한다.

```bash
cd backend
./gradlew generateJooq
```

생성 파일을 수작업으로 편집하지 않는다.

## 6. 백엔드 구현 구조

기존 모듈 구조에 다음 패키지를 추가한다.

```text
onGo-domain/.../ugc/
  campaign/
    Campaign.kt
    CampaignStatus.kt
    CampaignRepository.kt
    Playbook.kt
  participation/
    CampaignApplication.kt
    CampaignParticipant.kt
    ParticipationRepository.kt
  submission/
    ContentSubmission.kt
    SubmissionReview.kt
    CampaignPost.kt
    SubmissionRepository.kt
  reward/
    RewardConfirmation.kt
    RewardRepository.kt

onGo-application/.../ugc/
  CampaignUseCase.kt
  ParticipationUseCase.kt
  SubmissionUseCase.kt
  CampaignPublishingUseCase.kt
  CampaignAnalyticsUseCase.kt
  RewardUseCase.kt
  CampaignExportUseCase.kt

onGo-infrastructure/.../ugc/
  JooqCampaignRepository.kt
  JooqParticipationRepository.kt
  JooqSubmissionRepository.kt
  JooqRewardRepository.kt

onGo-api/.../ugc/
  CampaignController.kt
  CampaignParticipationController.kt
  CampaignSubmissionController.kt
  CampaignAnalyticsController.kt
  CampaignRewardController.kt
```

패키지의 정확한 base namespace와 DTO 위치는 기존 campaign과 유사한 모듈의 관례를 확인해 맞춘다.

### 6.1 권한

기존 권한 체계에 다음 권한을 추가하거나 가장 가까운 기존 권한과 매핑한다.

- `CAMPAIGN_VIEW`
- `CAMPAIGN_MANAGE`
- `CAMPAIGN_REVIEW`
- `CAMPAIGN_REWARD_MANAGE`

규칙:

- 브랜드 API는 캠페인의 `workspace_id`와 현재 사용자의 멤버십을 항상 검증한다.
- 크리에이터는 본인의 application, participant, submission만 접근한다.
- 공개 초대 토큰 API는 캠페인의 공개 가능 정보만 반환한다.
- 컨트롤러에서 전달받은 `userId`를 신뢰하지 않고 인증 principal에서 얻는다.

## 7. REST API 명세

### 7.1 브랜드 캠페인

```text
POST   /api/v1/workspaces/{workspaceId}/ugc/campaigns
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}
PATCH  /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}
POST   /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/publish
POST   /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/pause
POST   /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/complete
PUT    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/playbook
POST   /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/invites
```

목록 API는 `page`, `size`, `status`, `query`를 지원하고 size 최대값을 제한한다.

### 7.2 크리에이터 지원

```text
GET    /api/v1/ugc/invites/{token}
POST   /api/v1/ugc/invites/{token}/applications
GET    /api/v1/ugc/me/applications
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/applications
POST   /api/v1/workspaces/{workspaceId}/ugc/applications/{applicationId}/accept
POST   /api/v1/workspaces/{workspaceId}/ugc/applications/{applicationId}/reject
```

수락은 application 상태 변경과 participant 생성을 하나의 트랜잭션으로 처리한다.

### 7.3 제출과 검수

```text
POST   /api/v1/ugc/me/campaigns/{campaignId}/submissions
GET    /api/v1/ugc/me/campaigns/{campaignId}/submissions
POST   /api/v1/ugc/me/submissions/{submissionId}/submit
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/submissions
POST   /api/v1/workspaces/{workspaceId}/ugc/submissions/{submissionId}/request-changes
POST   /api/v1/workspaces/{workspaceId}/ugc/submissions/{submissionId}/approve
POST   /api/v1/workspaces/{workspaceId}/ugc/submissions/{submissionId}/publish
POST   /api/v1/ugc/me/submissions/{submissionId}/external-posts
```

`request-changes`에는 비어 있지 않은 사유가 필요하다. 승인되지 않은 제출물은 게시할 수 없다.

### 7.4 성과와 보상

```text
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/analytics
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/participants
PUT    /api/v1/workspaces/{workspaceId}/ugc/participants/{participantId}/reward
POST   /api/v1/workspaces/{workspaceId}/ugc/participants/{participantId}/reward/confirm
POST   /api/v1/workspaces/{workspaceId}/ugc/participants/{participantId}/reward/mark-paid
GET    /api/v1/workspaces/{workspaceId}/ugc/campaigns/{campaignId}/rewards.csv
```

CSV는 UTF-8 BOM, 안전한 파일명, 명시적인 헤더를 사용한다. `=`, `+`, `-`, `@`로 시작하는 사용자 문자열은 CSV formula injection을 방지한다.

## 8. 기존 멀티 SNS 기능 연결

### 8.1 직접 게시

- `CampaignPublishingUseCase`가 submission을 검증한다.
- APPROVED 상태와 소유권을 확인한 후 기존 `StreamPublishUseCase` 또는 `PublishVideoUseCase`를 호출한다.
- 기존 video/publish ID와 campaign post를 연결한다.
- 플랫폼별 성공·실패 상태를 campaign post에 반영한다.
- 부분 성공을 전체 실패로 덮어쓰지 않는다.
- 동일 요청 재시도 시 중복 게시되지 않도록 idempotency key를 둔다.

### 8.2 외부 게시물

- MVP에서는 URL과 플랫폼 게시물 ID를 등록할 수 있다.
- 지원 플랫폼 도메인 allowlist를 적용한다.
- URL만으로 소유권이 증명된 것으로 간주하지 않고 화면에 `외부 등록` 표시를 한다.
- 플랫폼 API가 제공하는 범위에서만 지표를 동기화한다.

### 8.3 성과 집계

- 기존 분석 데이터와 `ugc_campaign_posts`를 platform post ID 또는 기존 publish ID로 연결한다.
- 캠페인 대시보드는 최신 스냅샷을 합산한다.
- API 오류 시 마지막 성공 값과 `lastSyncedAt`, 오류 상태를 함께 반환한다.
- 숫자가 없을 때 0으로 위장하지 않고 unavailable 상태를 구분한다.

## 9. 프런트엔드 구현

### 9.1 브랜드 화면

```text
/ugc/campaigns                         캠페인 목록
/ugc/campaigns/new                     캠페인 작성 wizard
/ugc/campaigns/:id                     캠페인 개요
/ugc/campaigns/:id/playbook            플레이북 편집
/ugc/campaigns/:id/applications        지원자 관리
/ugc/campaigns/:id/submissions         제출물 검수
/ugc/campaigns/:id/analytics           성과 분석
/ugc/campaigns/:id/rewards             지급 확정 및 CSV
```

필수 컴포넌트:

- `CampaignStatusBadge.vue`
- `CampaignForm.vue`
- `PlaybookEditor.vue`
- `InviteLinkPanel.vue`
- `ApplicationTable.vue`
- `SubmissionReviewDrawer.vue`
- `CampaignMetricCards.vue`
- `RewardConfirmationTable.vue`

### 9.2 크리에이터 화면

```text
/ugc/invite/:token                     캠페인 공개 설명 및 지원
/creator/campaigns                     내 캠페인
/creator/campaigns/:id                 플레이북·일정·보상 확인
/creator/campaigns/:id/submit          콘텐츠 제출
```

### 9.3 UX 요구사항

- 페이지 헤더는 `PageHeader.vue`를 사용한다.
- 로딩, 빈 상태, 오류, 권한 없음, 연결 끊김 상태를 모두 구현한다.
- 캠페인 작성은 `기본 정보 → 플레이북 → 보상·일정 → 검토` 4단계다.
- 작성 중 새로고침해도 서버의 DRAFT가 유지된다.
- 상태 변경과 승인·거절·지급 확정은 확인 모달과 결과 toast를 제공한다.
- 제출물 검수 화면에서 영상, 캡션, 플레이북 규칙, 과거 검수 이력을 한 화면에 보여준다.
- 모바일 최소 375px에서 지원과 제출 흐름이 동작해야 한다.
- 모든 신규 문구를 `ko`, `en` locale에 추가한다.

## 10. Sprint별 구현 순서

### Sprint 1 — 캠페인 기반 (1주)

작업:

1. V47 migration과 jOOQ 생성
2. Campaign/Playbook 도메인과 상태 전이 테스트
3. repository 및 use case
4. 캠페인 CRUD·공개 API
5. 캠페인 목록·작성 wizard·상세 화면
6. 워크스페이스 인가 테스트

완료 조건:

- 캠페인 DRAFT 저장, 수정, 공개, 목록 조회가 UI에서 가능하다.
- 플레이북이 없거나 기간이 잘못되면 공개되지 않는다.
- 다른 워크스페이스의 캠페인 접근이 403/404 정책에 맞게 차단된다.
- backend 관련 테스트와 frontend build가 통과한다.

### Sprint 2 — 모집과 참여 (1주)

작업:

1. V48 중 application/participant 테이블
2. 안전한 초대 토큰 발급·만료·사용 제한
3. 공개 캠페인 페이지와 지원 API
4. 지원자 목록, 수락, 거절
5. 크리에이터 `내 캠페인` 화면
6. 중복 지원과 동시 수락 테스트

완료 조건:

- 실제 초대 링크를 통해 지원하고 브랜드가 수락할 수 있다.
- 만료·비활성 토큰 및 중복 지원이 차단된다.
- application 수락과 participant 생성이 원자적으로 처리된다.

### Sprint 3 — 제출과 승인 (1주)

작업:

1. V48 나머지 submission/review/post 테이블
2. 기존 asset/video 업로드 연결
3. 제출, 재제출, 수정 요청, 승인 API
4. 크리에이터 제출 화면
5. 브랜드 검수 화면과 이력
6. 상태 전이, 파일 소유권, 권한 테스트

완료 조건:

- 크리에이터가 콘텐츠를 제출하고 수정 요청 후 새 revision을 제출할 수 있다.
- 승인 전 게시가 불가능하다.
- 모든 검수 판단에 사용자, 시각, 사유가 남는다.

### Sprint 4 — 멀티 SNS 게시 연결 (1주)

작업:

1. 기존 게시 use case adapter 작성
2. 승인 제출물 게시 API 및 idempotency
3. 외부 게시물 URL 등록
4. 플랫폼별 상태 표시와 재시도
5. 부분 성공·토큰 만료·rate limit 테스트

완료 조건:

- 최소 2개 실제 SNS sandbox/test 계정에서 end-to-end 게시가 확인된다.
- 플랫폼 하나가 실패해도 성공한 게시 결과가 보존된다.
- 재시도가 중복 콘텐츠를 만들지 않는다.

### Sprint 5 — 성과와 지급 CSV (1주)

작업:

1. V49 migration과 repository
2. 기존 analytics 연결 및 최신 지표 집계
3. 캠페인 analytics API와 화면
4. 보상 수정·확정·지급 완료 표시
5. 지급 CSV export 및 보안 테스트

완료 조건:

- 캠페인·참여자·게시물별 지표와 마지막 동기화 시각을 확인한다.
- 확정 총액이 캠페인 예산을 넘으면 경고 및 확정 차단 정책이 작동한다.
- CSV가 한글 Excel에서 정상적으로 열리고 formula injection이 차단된다.

### Sprint 6 — 파일럿 출시 안정화 (1주)

작업:

1. 전체 happy path E2E 테스트
2. 오류·빈 상태·모바일·접근성 점검
3. 감사 로그와 운영 조회 보강
4. 핵심 funnel event 기록
5. 파일럿용 샘플 캠페인과 사용자 매뉴얼
6. 성능, 보안, 배포 및 rollback rehearsal

완료 조건:

- 브랜드 생성부터 지급 CSV까지 staging E2E가 통과한다.
- P0/P1 결함이 없다.
- 파일럿 고객이 개발자 도움 없이 핵심 흐름을 완료한다.
- 운영자가 실패 게시와 지표 동기화 문제를 식별할 수 있다.

## 11. 테스트 계획

### 11.1 도메인 단위 테스트

- 모든 상태 전이의 성공·실패
- 캠페인 공개 조건
- 예산과 보상액 경계값
- 중복 지원과 participant 생성
- 제출 revision 증가와 승인 조건
- 지급 확정 후 수정 제한

### 11.2 repository 통합 테스트

- PostgreSQL 기준 migration 적용
- jOOQ CRUD와 페이지네이션
- unique/check/FK 제약
- optimistic locking
- workspace 격리

### 11.3 API 테스트

- 인증 없음, 권한 없음, 다른 workspace 접근
- 잘못된 UUID, 누락 필드, 잘못된 상태 전이
- idempotent publish와 application accept
- 초대 토큰 만료·횟수 제한
- CSV content type, filename, injection 방지

### 11.4 프런트엔드 검증

```bash
cd frontend
npm run build
```

- TypeScript 오류 0건
- 주요 화면 375px, 768px, 1440px 확인
- 키보드로 작성·지원·검수 가능
- 한국어/영어 누락 키 없음

### 11.5 백엔드 검증

프로젝트 실제 task를 `./gradlew tasks`로 확인한 후 최소 다음을 실행한다.

```bash
cd backend
./gradlew test
./gradlew build
```

기존 unrelated 실패가 있으면 새 기능 실패와 분리해 원인 및 재현 명령을 기록한다.

## 12. 관측성과 운영

필수 로그/메트릭:

- campaign created/published/completed
- invite viewed → applied → accepted funnel
- submission created/submitted/approved와 소요 시간
- publish success/failure를 플랫폼별 집계
- analytics sync success/failure/age
- reward confirmed/marked paid 총액
- workspace와 campaign correlation ID

로그에 OAuth token, 초대 token 원문, 개인정보, 전체 request body를 남기지 않는다.

파일럿 운영 대시보드에서 최소 다음을 확인할 수 있어야 한다.

- 활성 캠페인 수
- 지원→수락→제출→승인 전환율
- 평균 제출 및 승인 소요 시간
- SNS별 게시 실패율
- 고객별 반복 캠페인 수

## 13. 보안과 데이터 정책

- 초대 토큰은 충분한 entropy를 가진 난수로 생성하고 hash만 저장한다.
- 다운로드/미디어 접근은 기존 signed URL 또는 권한 있는 API를 사용한다.
- 플랫폼 OAuth token은 기존 암호화 저장 체계를 유지한다.
- 모든 mutation은 서버에서 workspace와 resource ownership을 재검증한다.
- 상태 변경과 금액 변경은 audit event를 남긴다.
- 사용자 생성 URL은 protocol/domain을 검증하고 화면 출력 시 escape한다.
- 삭제는 MVP에서 hard delete 대신 취소/비활성 상태를 우선한다.
- 개인정보 보유·삭제 정책과 외부 SNS 데이터 이용 범위는 출시 전 약관에 명시한다.

## 14. Claude Code 작업 보고 형식

각 Sprint 완료 시 다음 형식으로 보고한다.

```text
1. 구현 완료 항목
2. 변경 파일 목록
3. DB migration 및 jOOQ 변경
4. 실행한 테스트와 결과
5. 직접 확인이 필요한 외부 SNS 항목
6. 남은 위험과 다음 Sprint 진입 가능 여부
```

큰 작업을 한 번에 구현하지 말고 아래 단위로 나눈다.

- migration/domain
- repository/application
- API/authorization
- frontend
- tests/manual

각 단위 전에 현재 구현 패턴을 확인하고, 완료 후 diff와 테스트 결과를 검토한다. 자동 생성 파일, lockfile, 포맷 변경이 불필요하게 확산되지 않도록 한다.

## 15. 첫 실행 요청문

Claude Code에는 다음 요청문과 이 문서 경로를 전달한다.

```text
docs/plans/2026-07-17-ugc-paid-pilot-implementation.md를 전체 읽고,
루트 CLAUDE.md와 현재 소스 구조를 확인한 뒤 Sprint 1만 구현해줘.

계획과 소스가 충돌하면 임의로 대규모 구조를 바꾸지 말고,
충돌 위치·현재 구현·가장 작은 해결안을 먼저 보고해줘.
DB는 jOOQ만 사용하고 신규 Flyway migration은 canonical 경로에 추가해줘.
인가와 테스트를 생략하지 말고 backend test/build 및 frontend build 결과까지 보고해줘.
관리자 로그인, 공개 마켓플레이스, 자동 정산은 구현하지 마.
```

## 16. 파일럿 이후 의사결정

다음 단계는 코드 완성도가 아니라 유료 고객 데이터로 결정한다.

- 유료 고객과 반복 캠페인이 확인되면: 대행사 다중 브랜드 운영, 자동 청구, 정산 원장을 우선한다.
- 크리에이터 모집이 병목이면: 제한된 업종/지역 크리에이터 풀과 추천을 추가한다.
- 콘텐츠 검수 시간이 병목이면: 플레이북 규칙 자동 검사와 AI 보조를 추가한다.
- 성과 증명이 병목이면: UTM/쿠폰/광고 계정 데이터와 매출 귀속을 추가한다.
- 유료 전환이 없으면: 공개 마켓플레이스나 자동 정산을 추가하지 않고 고객·문제 정의를 재검증한다.

이 MVP의 목적은 Noise의 기능 수를 따라잡는 것이 아니다. ongo가 이미 가진 멀티 SNS 게시와 분석 기반을 브랜드의 반복적인 UGC 운영 업무에 연결하고, 고객이 그 절감 효과에 실제로 비용을 지불하는지 확인하는 것이다.
