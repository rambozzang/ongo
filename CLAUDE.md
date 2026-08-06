# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**onGo**는 한국 콘텐츠 크리에이터를 위한 멀티 플랫폼 관리 SaaS입니다.

Reference: `CreFlow_Development_Guide_v2.0_1.md` contains the full specification (UI wireframes, business rules, DB schema, API design).

### 핵심 기능

1. **멀티 플랫폼 동시 업로드** — 영상 하나를 YouTube, TikTok, Instagram Reels, Naver Clip에 한 번에 게시
   - Tus 프로토콜 기반 이어받기 업로드
   - 플랫폼별 상태 추적: `DRAFT → UPLOADING → PROCESSING/REVIEW → PUBLISHED/FAILED/REJECTED`
2. **AI 메타데이터 최적화** — 각 플랫폼에 맞는 제목, 설명, 해시태그를 AI가 자동 생성
3. **AI 크리에이터 도구** — 스크립트 작성, 썸네일 생성, 댓글 자동 답변, SEO 분석, 트렌드 예측
4. **통합 분석 대시보드** — 전체 플랫폼의 조회수, 구독자, 수익 등을 하나의 화면에서 크로스 플랫폼 비교
5. **스케줄링 & 캘린더** — 예약 게시, 최적 업로드 시간 추천
6. **채널 관리** — OAuth로 연동된 채널의 토큰 상태, 동기화 관리
7. **구독 & 결제** — Free / Starter / Pro / Business 4단계 요금제 (PortOne V2 결제)
   - 신규 결제는 전부 PortOne. Paddle/Toss 코드는 기존 결제 레코드 처리용 레거시이며 체크아웃 UI에서는 사용하지 않음
   - 웹훅은 Standard Webhooks 서명 검증(`webhook-id`/`webhook-signature`/`webhook-timestamp`) 후 paymentId로 PortOne API 재조회해 금액·상태를 확인

## Tech Stack

- **Backend**: Spring Boot 4.0.7 + JDK 25 + Kotlin, Gradle 9
- **DB Access**: jOOQ 3.19+ (sole DB access layer — no JPA/Hibernate)
- **AI**: Spring AI 2.x + Alibaba Cloud Model Studio (Qwen, Kimi, GLM, MiniMax 4종을 DashScope API 하나로 통합, 기본값 QWEN, STT는 Whisper)
- **Frontend**: Vue.js 3 + Vite + Tailwind CSS
- **Database**: PostgreSQL 16 (JSONB, partitioning, full-text search)
- **Cache**: Caffeine (in-process) + Bucket4j for rate limiting
- **Async**: Spring Events + Virtual Threads (no external MQ in Phase 1)
- **Storage**: MinIO (local dev) → AWS S3 (production)
- **File Upload**: Tus Protocol (resumable uploads)
- **CI/CD**: Jenkins + Docker + Gradle 9

## Architecture

Modular Monolith with Clean Architecture layers:

```
onGo-api/              # Controllers (presentation layer)
onGo-application/      # UseCases + Spring Event handlers
onGo-domain/           # Entities, Repository interfaces, domain logic
onGo-infrastructure/   # jOOQ repos, platform clients, Spring AI config, S3, payment
onGo-common/           # Shared utilities (ResData, etc.)
```

Key architectural decisions:

- **jOOQ only** — no JPA/Hibernate. All DB access through jOOQ type-safe queries with code generation from DB schema
- **Caffeine + Spring Events + Virtual Threads** — no Redis/RabbitMQ in Phase 1
- Platform API clients use `@HttpExchange` interfaces (youtube/, tiktok/, instagram/, naverclip/)
- AI calls use `ChatClient.builder()` with Structured Output for typed JSON responses
- AI 제공자는 `ChatClientRegistryImpl`에서 동적 선택 (사용자 설정 기반)
- Alibaba Cloud Model Studio(DashScope) — `sk-` API 키 하나로 Qwen/Kimi/GLM/MiniMax 4종 호출, OpenAI 호환 API

## API Response Convention

All API responses use the `ResData<T>` wrapper (defined in `onGo-common`):

```kotlin
data class ResData<T>(
    var success: Boolean = true,
    val message: String? = null,
    var data: T? = null,
    val error: String? = null,
)
```

Frontend TypeScript interface mirrors this structure.

## AI Credit System

AI features consume credits (deducted per call). Credit deduction order:

1. Free monthly credits first (reset on 1st of month)
2. Purchased credits (FIFO by expiration date)
3. When exhausted → `InsufficientCreditException` → only AI features disabled

Credit deduction uses **pessimistic locking** (`FOR UPDATE`) on `ai_credits` table for concurrency safety.

## Local Development

### Database (Docker)

```bash
docker-compose up -d    # Start PostgreSQL 16
docker-compose down     # Stop
```

Connection: `localhost:5432`, user `ongo_user`, password `ongo_password`, database `ongo_db`

### Build & Run

```bash
# Backend
./gradlew build
./gradlew bootRun

# Frontend
cd frontend
npm install
npm run dev

# jOOQ code generation (after DB schema changes)
./gradlew generateJooq
```

### API Documentation

Swagger UI available at `http://localhost:8777/swagger-ui.html` when backend is running.

## Key Business Rules

- Social login only (Google/Kakao OAuth 2.0), no email/password
- JWT: Access Token 30min, Refresh Token 7 days
- Platform tokens stored AES-256 encrypted
- File upload validation: extension → MIME type → file size (client + server dual validation)
- Upload status flow: `DRAFT → UPLOADING → [per-platform: PROCESSING/REVIEW → PUBLISHED/FAILED/REJECTED]`
- Plan tiers: Free / Starter (9,900 KRW) / Pro (19,900 KRW) / Business (49,900 KRW) with different limits on uploads, storage, connected platforms, and free AI credits

## API Prefix

All endpoints use `/api/v1/` prefix. Key groups: `auth/`, `channels/`, `videos/`, `schedules/`, `analytics/`, `ai/`, `credits/`, `subscriptions/`, `payments/`

## Frontend Common Components

페이지 헤더는 반드시 `PageHeader.vue` 공통 컴포넌트를 사용할 것:

```vue
<PageHeader :title="$t('xxx.title')" :description="$t('xxx.description')">
  <template #actions><!-- 우측 CTA 버튼 --></template>
  <template #title-suffix><!-- 제목 옆 배지 --></template>
</PageHeader>
```

주요 공통 컴포넌트 (`frontend/src/components/common/`):
- `PageHeader.vue` — 페이지 헤더 (title + description + actions/title-suffix 슬롯)
- `PageGuide.vue` — 접을 수 있는 페이지 안내
- `EmptyState.vue` — 빈 상태 표시
- `ConfirmModal.vue` — 확인/취소 모달
- `LoadingSpinner.vue` — 로딩 스피너
- `CreditDisplay.vue` — AI 크레딧 잔여량 표시

## User Manual Maintenance

When adding new features or modifying existing ones, **always update the user manual** at `frontend/src/views/UserManualView.vue`. The manual contains bilingual (Korean/English) documentation for all features. Update both language versions in the `sectionsKo` and `sectionsEn` arrays to keep them in sync.
