# UGC 쇼츠 Phase 2 — 3자 병렬 작업 계약서

작업자 셋이 동시에 작업한다. **자기 소유 파일만 건드린다.** 남이 만드는 것은 이 문서에
확정된 이름을 그대로 믿고 참조하고, 상대를 기다리지 않는다.

설계는 `docs/plans/2026-08-03-ugc-shorts-phase2-design.md`.
Phase 1 결과물(`PipelineStage` enum, 프롬프트/템플릿 테이블과 UseCase)은 이미 커밋되어 있다.

## 파일 소유권

### 작업자 A — 스키마와 상수 (3개 파일만)

```
backend/onGo-api/src/main/resources/db/migration/V56__create_ugc_shorts_pipeline_runs.sql
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt
backend/onGo-common/src/main/kotlin/com/ongo/common/enums/AiFeature.kt
```

### 작업자 B — 백엔드 코드

```
backend/onGo-domain/src/main/kotlin/com/ongo/domain/ugc/shorts/   (Phase 2 신규 파일만 추가)
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsRun*.kt
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsClip*.kt
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/   (Phase 2 신규 파일만 추가)
backend/onGo-api/src/main/kotlin/com/ongo/api/ugc/ShortsPipelineController.kt
신규 테스트 파일
```

Phase 1 에서 만든 `ShortsPrompt*.kt`, `ShortsTemplate*.kt` 는 **읽기만 하고 수정하지 마라.**

### 작업자 C — 프론트엔드

```
frontend/src/api/ugcShortsPipeline.ts
frontend/src/stores/ugcShortsPipeline.ts
frontend/src/views/ugc/ShortsPipelineView.vue
frontend/src/views/ugc/ShortsPipelineDetailView.vue
frontend/src/router/index.ts
frontend/src/locales/ko/common.json, frontend/src/locales/en/common.json
frontend/src/components/manual/manualSections.ts
```

Phase 1 프론트 파일(`ugcShortsPrompt.ts`, `ugcShortsTemplate.ts`, `ugcShorts.ts`,
`ShortsPromptsView.vue`, `ShortsTemplatesView.vue`)은 **수정하지 마라.**

## 계약 1 — Tables.kt (작업자 A가 추가, B가 사용)

`Tables.kt` 한 파일에 **`object Tables`(테이블)와 `object Fields`(필드) 두 객체**가 있다.
참조는 각각 `Tables.X`, `Fields.X`. `Fields` 안에서 같은 이름을 다시 선언하면 컴파일 에러다.

### `object Tables` 에 추가 (4개)

```kotlin
// UGC 쇼츠 파이프라인 실행 (V56)
val UGC_SHORTS_PIPELINE_RUNS = DSL.table("ugc_shorts_pipeline_runs")
val UGC_SHORTS_RUN_STAGES = DSL.table("ugc_shorts_run_stages")
val UGC_SHORTS_CLIPS = DSL.table("ugc_shorts_clips")
val UGC_SHORTS_CLIP_HOOKS = DSL.table("ugc_shorts_clip_hooks")
```

### `object Fields` 에 추가 (21개, 이것만)

```kotlin
val TEMPLATE_ID = DSL.field("template_id", Long::class.java)
val CURRENT_STAGE = DSL.field("current_stage", String::class.java)
val TRANSCRIPT_TEXT = DSL.field("transcript_text", String::class.java)
val RUN_ID = DSL.field("run_id", Long::class.java)
val PROMPT_REVISION = DSL.field("prompt_revision", Int::class.java)
val AI_PROVIDER = DSL.field("ai_provider", String::class.java)
val CREDIT_COST = DSL.field("credit_cost", Int::class.java)
val INPUT_SNAPSHOT = DSL.field("input_snapshot", String::class.java)
val OUTPUT_SNAPSHOT = DSL.field("output_snapshot", String::class.java)
val SEQ = DSL.field("seq", Int::class.java)
val START_MS = DSL.field("start_ms", Long::class.java)
val END_MS = DSL.field("end_ms", Long::class.java)
val SUBTITLE_JSON = DSL.field("subtitle_json", String::class.java)
val CROP_JSON = DSL.field("crop_json", String::class.java)
val RENDER_SPEC = DSL.field("render_spec", String::class.java)
val DEDUP_KEY = DSL.field("dedup_key", String::class.java)
val RENDERED_VIDEO_ID = DSL.field("rendered_video_id", Long::class.java)
val CLIP_ID = DSL.field("clip_id", Long::class.java)
val VARIANT = DSL.field("variant", String::class.java)
val TEXT = DSL.field("text", String::class.java)
val SELECTED = DSL.field("selected", Boolean::class.java)
```

### 이미 존재하므로 다시 선언 금지 (그대로 재사용)

`ID`, `WORKSPACE_ID`, `USER_ID`, `SOURCE_VIDEO_ID`, `STATUS`, `CLIP_COUNT`, `ERROR_MESSAGE`,
`STARTED_AT`, `COMPLETED_AT`, `TITLE`, `CAPTION`, `SCHEDULED_AT`, `STAGE`, `PROMPT_ID`,
`CREATED_AT`, `UPDATED_AT`, `VERSION`

`INPUT_SNAPSHOT` / `OUTPUT_SNAPSHOT` / `SUBTITLE_JSON` / `CROP_JSON` / `RENDER_SPEC` 은 DB가
`JSONB` 다. 쓰기는 Phase 1 의 `ShortsTemplateJooqRepository` 가 `extra_spec` 을 다룬 방식대로
저장소 내부에 `DSL.field(..., JSONB::class.java)` 로컬 필드를 두고 처리한다.

## 계약 2 — AiFeature (작업자 A가 추가, B가 사용)

`AiFeature` enum 끝에 아래를 추가한다. 이름과 크레딧 값을 바꾸지 마라.

```kotlin
SHORTS_REFRAME("쇼츠 세로 변환", 3),
SHORTS_SEGMENT("쇼츠 맥락 컷", 8),
SHORTS_SUBTITLE("쇼츠 자막 생성", 5),
SHORTS_HOOK("쇼츠 후킹 문구", 5),
SHORTS_TEMPLATE("쇼츠 템플릿 적용", 3),
SHORTS_VALIDATE("쇼츠 검증", 3),
```

`TRANSCRIBE` 단계는 기존 `AiFeature.STT`(10)를 재사용한다.

## 계약 3 — 도메인 시그니처 (작업자 B가 작성)

```kotlin
package com.ongo.domain.ugc.shorts

enum class PipelineRunStatus {
    PENDING, RUNNING, AWAITING_HOOK_SELECTION, AWAITING_SCHEDULE, COMPLETED, FAILED, CANCELLED
}

enum class RunStageStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

enum class ClipStatus {
    DRAFT, HOOK_SELECTED, RENDER_READY, RENDERED, SCHEDULED, PUBLISHED, FAILED, DISCARDED
}

enum class HookVariant { A, B, CUSTOM }

data class PipelineRun(
    val id: Long = 0,
    val workspaceId: Long,
    val userId: Long,
    val sourceVideoId: Long,
    val templateId: Long? = null,
    val status: PipelineRunStatus = PipelineRunStatus.PENDING,
    val currentStage: PipelineStage? = null,
    val transcriptText: String? = null,
    val clipCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)

data class RunStage(
    val id: Long = 0,
    val runId: Long,
    val stage: PipelineStage,
    val status: RunStageStatus = RunStageStatus.PENDING,
    val promptId: Long? = null,
    val promptRevision: Int? = null,
    val aiProvider: String? = null,
    val creditCost: Int = 0,
    val inputSnapshot: String? = null,
    val outputSnapshot: String? = null,
    val errorMessage: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
)

data class ShortsClip(
    val id: Long = 0,
    val runId: Long,
    val seq: Int,
    val startMs: Long,
    val endMs: Long,
    val title: String? = null,
    val caption: String? = null,
    val subtitleJson: String? = null,
    val cropJson: String? = null,
    val renderSpec: String? = null,
    val status: ClipStatus = ClipStatus.DRAFT,
    val dedupKey: String? = null,
    val renderedVideoId: Long? = null,
    val scheduledAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class ClipHook(
    val id: Long = 0,
    val clipId: Long,
    val variant: HookVariant,
    val text: String,
    val selected: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

interface PipelineRunRepository {
    fun save(run: PipelineRun): PipelineRun
    fun update(run: PipelineRun): PipelineRun
    fun findById(id: Long): PipelineRun?
    fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int): List<PipelineRun>
    fun countByWorkspace(workspaceId: Long): Long
    fun delete(id: Long): Boolean
}

interface RunStageRepository {
    fun save(stage: RunStage): RunStage
    fun update(stage: RunStage): RunStage
    fun findByRunId(runId: Long): List<RunStage>
    fun findByRunIdAndStage(runId: Long, stage: PipelineStage): RunStage?
    fun deleteFrom(runId: Long, fromSortOrder: Int): Int
}

interface ShortsClipRepository {
    fun saveAll(clips: List<ShortsClip>): List<ShortsClip>
    fun update(clip: ShortsClip): ShortsClip
    fun findByRunId(runId: Long): List<ShortsClip>
    fun findById(id: Long): ShortsClip?
    fun deleteByRunId(runId: Long): Int
}

interface ClipHookRepository {
    fun saveAll(hooks: List<ClipHook>): List<ClipHook>
    fun findByClipIds(clipIds: List<Long>): List<ClipHook>
    fun clearSelection(clipId: Long)
    fun markSelected(clipId: Long, variant: HookVariant, text: String): ClipHook
    fun deleteByClipIds(clipIds: List<Long>): Int
}
```

## 계약 4 — API 경로와 응답 필드 (B와 C가 공유)

설계 문서 5장이 정본이다. 베이스는
`/api/v1/workspaces/{workspaceId}/ugc/shorts/runs` 이고, Phase 1 과 마찬가지로 프론트는
`const base = (workspaceId: number) => \`/workspaces/${workspaceId}/ugc/shorts/runs\`` 형태로
호출한다. 응답 DTO 필드명은 설계 5장의 TypeScript 인터페이스와 **정확히 일치**해야 한다.

## 계약 5 — 재사용할 기존 자산 (작업자 B)

- 전사: `SttUseCase.executeInternal(userId, videoId): SttResult` — 크레딧을 차감하지 않는
  내부용 메서드다. 파이프라인이 크레딧을 직접 관리하므로 이쪽을 쓴다.
  `SttResult(text: String, segments: List<SttSegmentResult>)`,
  `SttSegmentResult(startTime: Double, endTime: Double, text: String)`.
- AI 호출: `ChatClientResolver.resolve(userId)` + `.prompt().system(...).user(...).call().entity(...)`
- 크레딧: `CreditService.validateAndDeduct(userId, feature)` / `refundCredit(userId, cost, name)`
- 레이트 리밋: `AiRateLimiter.checkRateLimit(userId)`
- 입력 방어: `InputSanitizer.sanitize(...)`
- 프롬프트 로딩: Phase 1 의 `ShortsPromptUseCase` 로 워크스페이스 프롬프트를 읽어
  `run_stages.prompt_id` / `prompt_revision` 에 기록한다.
- 비동기: 기존 `backend/onGo-application/.../video/VideoPublishEventListener.kt` 의
  `ApplicationEventPublisher` + `@Async` 패턴을 그대로 따른다.

## 주의

`backend/build.gradle.kts`, `libs.versions.toml`, `application.yml`, `AiConfig.kt`,
`SttUseCase.kt`, `Dockerfile` 등은 **다른 사람이 Spring Boot 업그레이드 작업 중**이다.
절대 건드리지 말고, 거기서 비롯된 오류는 고치려 하지 마라.

`onGo-infrastructure:compileTestKotlin` 은 testcontainers 버전 문제로 현재 실패한다.
이것도 그 업그레이드 작업 탓이다. 인프라 통합 테스트는 이번에 추가하지 마라.

빌드 검증은 중앙에서 한다. git commit 하지 마라. 주석은 한국어로.
