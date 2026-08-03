# UGC 쇼츠 Phase 3 — 계약서 (게시 위임 · 엑셀 · 검증 · 중복방지)

선행: Phase 1 `f82403b`, Phase 2 `c657be0`. 브랜치 `feature/ugc-shorts-pipeline`.
설계 배경: `docs/plans/2026-08-03-ugc-shorts-pipeline-design.md`, `...-phase2-design.md`.

**두 작업자가 같은 워크트리(`/Users/bumkyuchun/work/app/ongo`)에서 동시에 일한다.**
자기 소유 파일만 건드려라. 남의 파일은 읽기만 하고, 상대를 기다리지 마라.

Apache POI 의존성(`libs.poi.ooxml`)은 이미 추가되어 컴파일까지 확인했다.
`build.gradle.kts`, `libs.versions.toml` 은 **누구도 건드리지 마라.**

## 배경 — 지금 무엇이 비어 있나

Phase 2 의 `ScheduleStageExecutor` 는 예약 시각을 **계산해 DB에 저장만** 한다.
실제로 플랫폼에 올라가지 않는다. 쇼츠 모듈 어디에도 `PublishVideoUseCase` 호출이 없다.
렌더는 우리가 하지 않으므로, 사용자가 `render.sh` 로 만든 완성 영상을 올려
클립에 연결해야 비로소 게시할 수 있다. 이 연결선을 만드는 것이 Phase 3 의 핵심이다.

---

## 작업자 A (codex) — 게시 위임 · 중복방지 · 검증 저장

### 소유 파일

```
backend/onGo-api/src/main/resources/db/migration/V57__create_ugc_shorts_publications_and_validations.sql
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt
backend/onGo-domain/src/main/kotlin/com/ongo/domain/ugc/shorts/ClipPublication*.kt
backend/onGo-domain/src/main/kotlin/com/ongo/domain/ugc/shorts/ClipValidation*.kt
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsClipPublicationJooqRepository.kt
backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsClipValidationJooqRepository.kt
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsPublishAdapter.kt
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/stage/ScheduleStageExecutor.kt   (수정)
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/stage/ValidateStageExecutor.kt   (수정)
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsPipelineOrchestrator.kt    (수정)
자기 작업의 테스트 파일
```

### V57 스키마

```sql
CREATE TABLE IF NOT EXISTS ugc_shorts_clip_publications (
    id              BIGSERIAL PRIMARY KEY,
    clip_id         BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    platform        VARCHAR(30) NOT NULL,
    video_upload_id BIGINT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    scheduled_at    TIMESTAMP,
    published_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_clip_publications UNIQUE (clip_id, platform)
);
CREATE INDEX IF NOT EXISTS idx_ugc_shorts_clip_publications_clip
    ON ugc_shorts_clip_publications(clip_id);

CREATE TABLE IF NOT EXISTS ugc_shorts_validations (
    id         BIGSERIAL PRIMARY KEY,
    clip_id    BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    rule_code  VARCHAR(50) NOT NULL,
    severity   VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    passed     BOOLEAN NOT NULL DEFAULT TRUE,
    message    TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ugc_shorts_validations_clip
    ON ugc_shorts_validations(clip_id);
```

`Tables.kt` — `object Tables` 에 `UGC_SHORTS_CLIP_PUBLICATIONS`, `UGC_SHORTS_VALIDATIONS` 2개.
`object Fields` 에는 **아래 4개만** 새로 넣어라. 나머지(`ID`, `CLIP_ID`, `PLATFORM`, `STATUS`,
`SCHEDULED_AT`, `PUBLISHED_AT`, `ERROR_MESSAGE`, `CREATED_AT`, `UPDATED_AT`, `MESSAGE`)는
이미 있으니 다시 선언하면 컴파일 에러다. 넣기 전에 반드시 `grep "val X = DSL.field" Tables.kt` 로 확인하라.

```kotlin
val VIDEO_UPLOAD_ID = DSL.field("video_upload_id", Long::class.java)
val RULE_CODE = DSL.field("rule_code", String::class.java)
val SEVERITY = DSL.field("severity", String::class.java)
val PASSED = DSL.field("passed", Boolean::class.java)
```

### 게시 위임

`ShortsPublishAdapter` 를 만들어 기존 `PublishVideoUseCase` 에 위임한다.
기존 `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/CampaignPublishAdapter.kt`
를 먼저 읽고 같은 방식으로 만들어라. 그 어댑터가 `PublishVideoUseCase.publishVideo(userId, videoId, configs)`
를 어떻게 부르는지가 정답이다.

`ScheduleStageExecutor` 수정 — 지금은 시각만 계산한다. 아래로 바꾼다.

- `renderedVideoId` 가 있는 클립만 실제 게시 대상이다. 없는 클립은 `SKIPPED` 로 두고
  스냅샷에 "렌더 영상 미연결" 사유를 남긴다.
- 대상 클립마다 `platforms` 각각에 대해 `ShortsPublishAdapter` 로 게시하되,
  `scheduledAt` 을 넘겨 예약 게시로 건다.
- 게시 결과를 `ugc_shorts_clip_publications` 에 기록한다.
- **중복 방지**: 이미 같은 `(clip_id, platform)` 행이 `PUBLISHED` 또는 `SCHEDULED` 면
  다시 게시하지 않고 건너뛴다. 유니크 제약과 상태 가드 2중으로 막는다.

### 검증 저장

`ValidateStageExecutor` 가 내는 결과를 `ugc_shorts_validations` 에 저장한다.
최소 규칙 4개는 AI 없이도 코드로 판정한다(AI 결과는 추가로 얹는다).

| rule_code | 판정 |
|---|---|
| `HOOK_MISSING` | 선택된 후킹 문구가 없으면 ERROR |
| `SUBTITLE_LINE_LENGTH` | 자막 한 줄이 5~9자를 벗어나면 WARNING |
| `CLIP_DURATION` | 클립 길이가 20초 미만 또는 90초 초과면 WARNING |
| `META_MISSING` | 제목 또는 캡션이 비어 있으면 ERROR |

---

## 작업자 B (kimi) — 엑셀 양방향 · 프론트

### 소유 파일

```
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsScheduleSheetService.kt
backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/dto/ShortsSheetDtos.kt
backend/onGo-api/src/main/kotlin/com/ongo/api/ugc/ShortsSheetController.kt
frontend/src/api/ugcShortsSheet.ts
frontend/src/views/ugc/ShortsPipelineDetailView.vue          (엑셀 UI 추가만)
frontend/src/locales/ko/common.json, frontend/src/locales/en/common.json
frontend/src/components/manual/manualSections.ts
자기 작업의 테스트 파일
```

`ShortsPipelineUseCase.kt`, `ShortsPipelineOrchestrator.kt`, `stage/`, `Tables.kt`,
마이그레이션은 **작업자 A 소유다. 절대 건드리지 마라.**

### 엑셀 내보내기 (Apache POI, .xlsx)

참고 영상 16:18 구간의 "업로드 예약표"다. 컬럼은 이 순서로 고정한다.

```
순번 | 클립ID | 파일명 | 제목 | 후킹문구 | 캡션 | 플랫폼 | 예약시각 | 상태
```

- `파일명` 은 `clip-{seq}.mp4`
- `후킹문구` 는 선택된 후킹(없으면 빈칸)
- `예약시각` 은 `yyyy-MM-dd HH:mm` 문자열
- `클립ID` 열은 가져오기 때 행을 식별하는 키다. 숨기지 말고 그대로 둔다.
- 헤더 행은 굵게, 열 너비 자동 조정

### 엑셀 가져오기

같은 시트를 올리면 **제목·후킹문구·캡션·예약시각** 4개 열만 반영한다.
순번·클립ID·상태는 읽기 전용이며 무시한다.

**반드시 2단계로 나눠라.** 영상에는 없지만, 셀 하나 잘못 건드려 예약 전체가
어긋나는 사고를 막기 위한 것이다.

1. `POST {base}/{runId}/sheet/preview` — 파일을 받아 **변경 diff 만** 돌려준다. DB는 건드리지 않는다.
2. `POST {base}/{runId}/sheet/apply` — 확인된 변경을 실제로 반영한다.

```ts
interface SheetDiffRow {
  clipId: number
  seq: number
  field: 'title' | 'hookText' | 'caption' | 'scheduledAt'
  before: string | null
  after: string | null
}
interface SheetPreviewResponse {
  rows: SheetDiffRow[]
  unknownClipIds: number[]     // 시트에 있으나 이 실행에 없는 클립
  invalidRows: string[]        // 날짜 형식 오류 등
}
```

가져오기 검증 — 예약시각이 파싱되지 않으면 그 행을 `invalidRows` 로 빼고 나머지는 진행한다.
전체를 실패시키지 마라.

### API

베이스는 Phase 2 와 같다: `/api/v1/workspaces/{workspaceId}/ugc/shorts/runs`

```
GET  {base}/{runId}/sheet            .xlsx 다운로드
POST {base}/{runId}/sheet/preview    multipart file → SheetPreviewResponse
POST {base}/{runId}/sheet/apply      multipart file → SheetPreviewResponse (실제 반영분)
```

### 프론트

`ShortsPipelineDetailView.vue` 에 "예약표" 영역을 추가한다.
- 엑셀 내려받기 버튼
- 엑셀 올리기 → `preview` 호출 → `BaseModal` 로 변경 diff 표 표시 → 확인 시 `apply`
- 기존 규칙 그대로: `PageHeader`/`BaseModal`/`ConfirmModal`/`EmptyState`/`LoadingSpinner`,
  `.card`·`btn-*`·`input-field`, `primary-*` 토큰만, `mobile:/tablet:/desktop:`, `mx-auto` 금지
- i18n 은 ko/en 두 파일 **동시** 갱신, 매뉴얼도 sectionsKo/sectionsEn 양쪽

---

## 공통 규칙

- 주석은 한국어. `val` 우선.
- **git commit 하지 마라.** 검증과 커밋은 중앙에서 한다.
- `backend/build.gradle.kts`, `libs.versions.toml`, `application.yml`, `AiConfig.kt`,
  `SttUseCase.kt`, `Dockerfile` 은 다른 사람이 Spring Boot 업그레이드 중이다. 건드리지 마라.
- `onGo-infrastructure:compileTestKotlin` 은 그 업그레이드 탓에 testcontainers 버전이 비어
  실패한다. 고치려 하지 말고, 인프라 통합 테스트도 추가하지 마라.
- 검증은 `./gradlew :onGo-domain:test :onGo-application:test` 와
  `cd frontend && npm run build` 로 한다.
- 프로덕션 코드에 버그를 발견하면 남의 소유면 **고치지 말고 보고**하라.
