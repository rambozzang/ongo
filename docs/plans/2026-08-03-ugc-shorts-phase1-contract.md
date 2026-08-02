# UGC 쇼츠 Phase 1 — 병렬 작업 계약서

두 작업자가 동시에 작업한다. **자기 소유 파일만 건드린다.** 상대가 만드는 것은 이 문서에
확정된 이름을 그대로 믿고 참조하라. 상대 작업이 끝나기를 기다리지 마라.

상세 설계는 `docs/plans/2026-08-03-ugc-shorts-pipeline-design.md` 5장을 따른다.

## 파일 소유권

### 작업자 A 소유 (다른 파일 금지)

```
backend/onGo-api/src/main/resources/db/migration/V55__create_ugc_shorts_prompts_and_templates.sql
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt
backend/onGo-common/src/main/kotlin/com/ongo/common/enums/Permission.kt
backend/onGo-domain/src/main/kotlin/com/ongo/domain/team/RolePermissions.kt
```

### 작업자 B 소유 (다른 파일 금지)

```
backend/onGo-domain/src/main/kotlin/com/ongo/domain/ugc/shorts/*.kt
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsPromptJooqRepository.kt
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsTemplateJooqRepository.kt
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/*.kt
backend/onGo-api/src/main/kotlin/com/ongo/api/ugc/ShortsPromptController.kt
backend/onGo-api/src/main/kotlin/com/ongo/api/ugc/ShortsTemplateController.kt
backend/onGo-*/src/test/kotlin/**  (신규 테스트만)
```

`frontend/` 는 이미 완료되었다. 양쪽 모두 건드리지 마라.

## 계약 1 — Tables.kt 상수 (작업자 A가 추가, 작업자 B가 사용)

`Tables.kt` 한 파일 안에 **`object Tables`(테이블)와 `object Fields`(필드) 두 객체**가 있다.
테이블 상수는 `Tables` 에, 필드 상수는 `Fields` 에 넣는다. 참조도 각각 `Tables.X`, `Fields.X` 다.
`Fields` 안에서 같은 이름을 다시 선언하면 컴파일 에러다.

### `object Tables` 에 추가할 테이블 상수 3개

```kotlin
// UGC 쇼츠 파이프라인 (V55)
val UGC_SHORTS_PROMPTS = DSL.table("ugc_shorts_prompts")
val UGC_SHORTS_PROMPT_REVISIONS = DSL.table("ugc_shorts_prompt_revisions")
val UGC_SHORTS_TEMPLATES = DSL.table("ugc_shorts_templates")
```

### `object Fields` 에 추가할 필드 상수 23개 (이것만 추가한다)

```kotlin
val STAGE = DSL.field("stage", String::class.java)
val SYSTEM_PROMPT = DSL.field("system_prompt", String::class.java)
val USER_PROMPT = DSL.field("user_prompt", String::class.java)
val EXECUTABLE = DSL.field("executable", Boolean::class.java)
val PROMPT_ID = DSL.field("prompt_id", Long::class.java)
val CHANGE_NOTE = DSL.field("change_note", String::class.java)
val CHANGED_BY = DSL.field("changed_by", Long::class.java)
val ASPECT_RATIO = DSL.field("aspect_ratio", String::class.java)
val BACKGROUND_STYLE = DSL.field("background_style", String::class.java)
val HOOK_FONT_FAMILY = DSL.field("hook_font_family", String::class.java)
val HOOK_FONT_SIZE = DSL.field("hook_font_size", Int::class.java)
val HOOK_FONT_COLOR = DSL.field("hook_font_color", String::class.java)
val HOOK_STROKE_COLOR = DSL.field("hook_stroke_color", String::class.java)
val HOOK_POSITION = DSL.field("hook_position", String::class.java)
val CAPTION_FONT_FAMILY = DSL.field("caption_font_family", String::class.java)
val CAPTION_FONT_SIZE = DSL.field("caption_font_size", Int::class.java)
val CAPTION_FONT_COLOR = DSL.field("caption_font_color", String::class.java)
val CAPTION_STROKE_COLOR = DSL.field("caption_stroke_color", String::class.java)
val CAPTION_POSITION = DSL.field("caption_position", String::class.java)
val SAFE_AREA_TOP = DSL.field("safe_area_top", Int::class.java)
val SAFE_AREA_BOTTOM = DSL.field("safe_area_bottom", Int::class.java)
val REFERENCE_IMAGE_URL = DSL.field("reference_image_url", String::class.java)
val EXTRA_SPEC = DSL.field("extra_spec", String::class.java)
```

### 이미 존재하므로 절대 다시 선언하지 말 것 (그대로 재사용)

`ID`, `WORKSPACE_ID`, `NAME`, `DESCRIPTION`, `REVISION`, `CREATED_BY`, `CREATED_AT`,
`UPDATED_AT`, `VERSION`, `WIDTH`, `HEIGHT`, `IS_DEFAULT`

## 계약 2 — 도메인 시그니처 (작업자 B가 작성)

작업자 B는 아래 시그니처를 **정확히 이대로** 만든다. 임의로 바꾸지 마라.

```kotlin
package com.ongo.domain.ugc.shorts

enum class PipelineStage(val displayName: String, val sortOrder: Int, val aiExecutable: Boolean) {
    TRANSCRIBE("전사", 1, false),
    REFRAME("세로 변환", 2, true),
    SEGMENT("맥락 컷", 3, true),
    SUBTITLE("자막", 4, true),
    HOOK("후킹 문구", 5, true),
    TEMPLATE("템플릿", 6, true),
    RENDER_SPEC("렌더 스펙", 7, false),
    VALIDATE("검증", 8, true),
    SCHEDULE("예약", 9, false),
}

data class ShortsPrompt(
    val id: Long = 0,
    val workspaceId: Long?,          // null = 시스템 기본값
    val stage: PipelineStage,
    val name: String,
    val description: String? = null,
    val systemPrompt: String? = null,
    val userPrompt: String,
    val executable: Boolean = true,
    val revision: Int = 1,
    val createdBy: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)

data class ShortsPromptRevision(
    val id: Long = 0,
    val promptId: Long,
    val revision: Int,
    val systemPrompt: String? = null,
    val userPrompt: String,
    val changeNote: String? = null,
    val changedBy: Long,
    val createdAt: Instant = Instant.now(),
)

data class ShortsTemplate(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val description: String? = null,
    val aspectRatio: String = "9:16",
    val width: Int = 1080,
    val height: Int = 1920,
    val backgroundStyle: String = "BLACK_BARS",
    val hookFontFamily: String? = null,
    val hookFontSize: Int? = null,
    val hookFontColor: String? = null,
    val hookStrokeColor: String? = null,
    val hookPosition: String = "TOP",
    val captionFontFamily: String? = null,
    val captionFontSize: Int? = null,
    val captionFontColor: String? = null,
    val captionStrokeColor: String? = null,
    val captionPosition: String = "BOTTOM",
    val safeAreaTop: Int = 0,
    val safeAreaBottom: Int = 0,
    val referenceImageUrl: String? = null,
    val extraSpec: String? = null,   // JSONB 는 JSON 문자열로 다룬다
    val isDefault: Boolean = false,
    val createdBy: Long,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
)

interface ShortsPromptRepository {
    fun findDefaults(): List<ShortsPrompt>
    fun findDefaultByStage(stage: PipelineStage): ShortsPrompt?
    fun findByWorkspace(workspaceId: Long): List<ShortsPrompt>
    fun findByWorkspaceAndStage(workspaceId: Long, stage: PipelineStage): ShortsPrompt?
    fun save(prompt: ShortsPrompt): ShortsPrompt
    fun update(prompt: ShortsPrompt): ShortsPrompt
    fun deleteByWorkspaceAndStage(workspaceId: Long, stage: PipelineStage): Boolean
    fun saveRevision(revision: ShortsPromptRevision): ShortsPromptRevision
    fun findRevisions(promptId: Long): List<ShortsPromptRevision>
    fun findRevision(promptId: Long, revision: Int): ShortsPromptRevision?
}

interface ShortsTemplateRepository {
    fun findByWorkspace(workspaceId: Long): List<ShortsTemplate>
    fun findById(id: Long): ShortsTemplate?
    fun save(template: ShortsTemplate): ShortsTemplate
    fun update(template: ShortsTemplate): ShortsTemplate
    fun delete(id: Long): Boolean
    fun clearDefault(workspaceId: Long)
}
```

## 계약 3 — 권한 이름 (작업자 A가 추가, 작업자 B가 사용)

`Permission` enum 에 아래 두 값을 추가한다. 이름을 바꾸지 마라.

```kotlin
SHORTS_PIPELINE_VIEW, SHORTS_PIPELINE_MANAGE,
```

`RolePermissions.kt` 에서 기존 `CAMPAIGN_VIEW` 가 부여된 역할에 `SHORTS_PIPELINE_VIEW` 를,
`CAMPAIGN_MANAGE` 가 부여된 역할에 `SHORTS_PIPELINE_MANAGE` 를 같이 넣는다.

## 계약 4 — 프론트엔드가 이미 호출하는 API (변경 금지)

프론트엔드는 완성되어 있고 아래 경로·필드명을 이미 쓰고 있다. 설계 문서 5.3절과 동일하며,
**여기서 벗어나면 프론트가 깨진다.**

베이스 경로: `/api/v1/workspaces/{workspaceId}/ugc/shorts`

```
GET    {base}/prompts
GET    {base}/prompts/{stage}
PUT    {base}/prompts/{stage}          body: { systemPrompt, userPrompt, changeNote }
DELETE {base}/prompts/{stage}
GET    {base}/prompts/{stage}/revisions
POST   {base}/prompts/{stage}/revisions/{revision}/restore

GET    {base}/templates
POST   {base}/templates
GET    {base}/templates/{id}
PUT    {base}/templates/{id}
DELETE {base}/templates/{id}
POST   {base}/templates/{id}/reference-image     multipart 필드명: file
```

응답 필드는 설계 문서 5.3절의 `ShortsPromptResponse` / `ShortsPromptRevisionResponse` /
`ShortsTemplateResponse` TypeScript 인터페이스와 이름·타입이 정확히 일치해야 한다.

## 주의

`backend/build.gradle.kts`, `libs.versions.toml`, `application.yml`, `AiConfig.kt`,
`SttUseCase.kt`, `Dockerfile` 등은 **다른 사람이 Spring Boot 업그레이드 작업 중**이라 이미
수정되어 있다. 절대 건드리지 말고, 그쪽에서 비롯된 빌드 오류는 고치려 하지 마라.
자기가 추가한 코드에서 난 오류만 고친다.
