# UGC 쇼츠 파이프라인 설계

작성일: 2026-08-03
참고 영상: https://www.youtube.com/watch?v=Bnp0HyZXOAk (비블, "클로드 코드로 쇼츠 자동화")

## 1. 배경

롱폼 영상 하나에서 쇼츠 여러 개를 만들어 여러 플랫폼에 예약 게시하는 흐름을, 참고 영상의
프로세스를 그대로 옮겨 onGo 기능으로 구현한다. 영상에서는 클로드 코드에 매번 프롬프트를
입력해 처리하지만, 우리는 그 프롬프트를 **DB에 저장해 편집·복원·이력 관리**하고 각 단계를
UseCase로 고정한다.

### 영상의 9단계와 원문 프롬프트

| # | 단계 | 영상 속 프롬프트 / 지시 |
|---|------|------------------------|
| 1 | 전사 | "이 영상에서 오디오를 뽑아서 전사해 줘" |
| 2 | 세로 변환 | "9대 16 비율 1080에 1920 사이즈로 바꾸고 얼굴이 이렇게 들어가게 양옆을 잘라서 새로운 형태로 만들어 달라. 인물이 중심에 오게 세팅해 달라" |
| 3 | 맥락 컷 | "전사본을 읽고 앞뒤 설명 없이 그것만 봐도 말이 되는 40초에서 60초 사이의 구간을 맥락에 맞게 개수도 정해서 알아서 뽑아 달라" |
| 4 | 자막 | "자막은 글자수로 끊지 말고 맥락 위주로 끊되 한 줄이 다섯자에서 아홉자 사이가 되게 맞춰 줘" |
| 5 | 후킹 A/B | 쇼츠별 후킹 문구를 A안·B안으로 제시 → 사용자가 선택/수정/삭제 |
| 6 | 템플릿 | 위아래 검은 배경, 인물 중앙, 후킹·자막 폰트와 색. 레퍼런스 캡처 첨부가 가장 정확 |
| 7 | 렌더 | 컷 + 크롭 + 자막 굽기 |
| 8 | 예약 업로드 | "8월 13일부터 하루 간격으로 7시에 예약해 줘" |
| 9 | 검증·중복방지 | 화면 깨짐·소리·후킹 확인, 이미 업로드된 것은 재게시 차단 |

영상 07:12 구간에서 **나쁜 프롬프트**를 명시한다 — "반응 좋았던 구간" 또는 "네가 핵심이라고
생각하는 구간을 잘라 줘"는 쓰지 말 것. 이 제약을 `SEGMENT` 기본 프롬프트에 반영한다.

## 2. 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| 렌더링 | **스펙까지만.** 실제 인코딩은 하지 않음 | onGo는 배포 허브이고 ffmpeg 의존성이 없음. 서버 인코딩은 Phase 1 무-MQ 정책과 충돌 |
| 배치 | UGC 하위 신규 패키지 `ugc/shorts` | 기존 UGC 자산(플레이북, 게시 어댑터) 재사용 |
| 엑셀 | Apache POI, xlsx 양방향 | 영상의 "엑셀에서 셀만 고치면 그 내용으로 업로드" 흐름과 동일 |
| 프롬프트 | 파이프라인 9단계만 DB화 | 기존 `PromptTemplates.kt` 60여 개는 그대로 두어 회귀 위험 차단 |
| 실행 모델 | 자동 실행 + 단계별 재실행 | 프롬프트를 고친 뒤 그 단계만 다시 돌리는 것이 이 기능의 핵심 가치 |
| 보조 기능 | STT 연결·중복방지·AI 검증·레퍼런스 이미지 모두 포함 | |

### 렌더 경계를 메우는 방법

`RENDER_SPEC` 단계가 **실행 가능한 산출물 3종**을 만든다. 사용자는 스크립트를 그대로 돌려
완성 영상을 얻고, 그 파일을 업로드하면 검증·예약·게시는 우리 시스템이 이어받는다.

- `render-spec.json` — 컷 구간, 크롭 박스, 자막 타이밍, 후킹 배치, 템플릿 값
- `clip-{n}.ass` — 5~9자 규칙이 적용된 자막 파일 (스타일 포함)
- `render.sh` — 위 둘을 물린 ffmpeg 명령 스크립트

## 3. 클로드 코드가 하던 일 → onGo 기능 매핑

| 영상에서 클로드 코드가 한 일 | onGo 대응 | 재사용 자산 |
|---|---|---|
| 오디오 전사 | `TRANSCRIBE` 단계 | `SttUseCase` |
| 세로 변환 지시 | `REFRAME` 단계 → 크롭 박스 산출 | 신규 |
| 맥락 단위 컷 제안 | `SEGMENT` 단계 | `RepurposeUseCase` 로직 |
| 자막 생성 (5~9자) | `SUBTITLE` 단계 | `SubtitleSegment` |
| 후킹 A안·B안 | `HOOK` 단계 | 신규 |
| 템플릿 적용 | `TEMPLATE` 단계 | 신규 |
| 렌더 | `RENDER_SPEC` 단계 → 산출물 3종 | 신규 |
| 엑셀 예약표 생성 | POI 내보내기/가져오기 | 신규 |
| 예약 업로드 | `SCHEDULE` 단계 | `PublishVideoUseCase(scheduledAt)` |
| 중복 업로드 방지 | `clips.dedup_key` 유니크 + 상태 가드 | 신규 |
| 검증 에이전트 | `VALIDATE` 단계 | 신규 |

## 4. 전체 데이터 모델

기존 관례를 따른다 — `BIGSERIAL` PK / `BIGINT` FK, UUID 미사용, jOOQ 코드 생성기 없이
`Tables.kt`에 상수 수동 추가.

```
V55  ugc_shorts_prompts           단계별 프롬프트 (시스템 기본 + 워크스페이스 오버라이드)
     ugc_shorts_prompt_revisions  개정 이력 (복원·롤백)
     ugc_shorts_templates         템플릿 (폰트/색/배경/세이프에어리어 + 레퍼런스 이미지)

V56  ugc_shorts_pipeline_runs     실행 1건 = 롱폼 1개              [Phase 2]
     ugc_shorts_run_stages        단계 상태 + 사용 프롬프트 스냅샷  [Phase 2]
     ugc_shorts_clips             클립 N개                          [Phase 2]
     ugc_shorts_clip_hooks        후킹 A/B/CUSTOM                   [Phase 2]

V57  ugc_shorts_validations       클립별 검증 결과                  [Phase 3]
```

### 프롬프트 오버라이드 규칙

`ugc_shorts_prompts.workspace_id`는 **nullable**이다.

- `NULL` — 시스템 기본 프롬프트. V55 마이그레이션에서 9행 INSERT. 수정·삭제 불가.
- 값 있음 — 해당 워크스페이스의 오버라이드.

조회는 워크스페이스 행이 있으면 그것을, 없으면 시스템 기본 행을 반환한다.
**기본값 복원**은 워크스페이스 행을 삭제하는 것으로 구현한다.

## 5. Phase 1 상세 명세 (이번 구현 범위)

프롬프트와 템플릿 관리. 이것만으로 "각 단계별 프롬프트를 정확한 텍스트로 가져와 관리한다"는
요구가 충족된다.

### 5.1 마이그레이션 V55

파일: `backend/onGo-api/src/main/resources/db/migration/V55__create_ugc_shorts_prompts_and_templates.sql`

```sql
CREATE TABLE IF NOT EXISTS ugc_shorts_prompts (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT,                          -- NULL = 시스템 기본값
    stage         VARCHAR(30)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    description   TEXT,
    system_prompt TEXT,
    user_prompt   TEXT NOT NULL,
    executable    BOOLEAN NOT NULL DEFAULT TRUE,   -- 실제 AI 호출에 사용되는지
    revision      INT     NOT NULL DEFAULT 1,
    created_by    BIGINT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version       BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT chk_ugc_shorts_prompts_revision CHECK (revision >= 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ugc_shorts_prompts_default
    ON ugc_shorts_prompts(stage) WHERE workspace_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ugc_shorts_prompts_workspace
    ON ugc_shorts_prompts(workspace_id, stage) WHERE workspace_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ugc_shorts_prompt_revisions (
    id            BIGSERIAL PRIMARY KEY,
    prompt_id     BIGINT NOT NULL REFERENCES ugc_shorts_prompts(id) ON DELETE CASCADE,
    revision      INT    NOT NULL,
    system_prompt TEXT,
    user_prompt   TEXT   NOT NULL,
    change_note   VARCHAR(300),
    changed_by    BIGINT NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_prompt_revisions UNIQUE (prompt_id, revision)
);
CREATE INDEX IF NOT EXISTS idx_ugc_shorts_prompt_revisions_prompt
    ON ugc_shorts_prompt_revisions(prompt_id, revision DESC);

CREATE TABLE IF NOT EXISTS ugc_shorts_templates (
    id                   BIGSERIAL PRIMARY KEY,
    workspace_id         BIGINT NOT NULL,
    name                 VARCHAR(150) NOT NULL,
    description          TEXT,
    aspect_ratio         VARCHAR(10) NOT NULL DEFAULT '9:16',
    width                INT NOT NULL DEFAULT 1080,
    height               INT NOT NULL DEFAULT 1920,
    background_style     VARCHAR(30) NOT NULL DEFAULT 'BLACK_BARS',
    hook_font_family     VARCHAR(100),
    hook_font_size       INT,
    hook_font_color      VARCHAR(20),
    hook_stroke_color    VARCHAR(20),
    hook_position        VARCHAR(20) NOT NULL DEFAULT 'TOP',
    caption_font_family  VARCHAR(100),
    caption_font_size    INT,
    caption_font_color   VARCHAR(20),
    caption_stroke_color VARCHAR(20),
    caption_position     VARCHAR(20) NOT NULL DEFAULT 'BOTTOM',
    safe_area_top        INT NOT NULL DEFAULT 0,
    safe_area_bottom     INT NOT NULL DEFAULT 0,
    reference_image_url  VARCHAR(500),
    extra_spec           JSONB,
    is_default           BOOLEAN NOT NULL DEFAULT FALSE,
    created_by           BIGINT NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ugc_shorts_templates_size CHECK (width > 0 AND height > 0)
);
CREATE INDEX IF NOT EXISTS idx_ugc_shorts_templates_workspace
    ON ugc_shorts_templates(workspace_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ugc_shorts_templates_default
    ON ugc_shorts_templates(workspace_id) WHERE is_default;
```

### 5.2 시스템 기본 프롬프트 시드 (V55 INSERT)

영상에서 발화된 문장을 그대로 옮기되, 구조화 출력을 위한 지시를 덧붙인다.
`executable = false`인 단계는 텍스트로 보관만 하고 AI 호출에는 쓰지 않는다.

| stage | name | executable | 비고 |
|---|---|---|---|
| `TRANSCRIBE` | 전사 | false | Whisper STT가 처리, 프롬프트는 참고용 |
| `REFRAME` | 세로 변환 | true | 크롭 박스 산출 |
| `SEGMENT` | 맥락 컷 | true | 핵심 단계 |
| `SUBTITLE` | 자막 | true | 5~9자 규칙 |
| `HOOK` | 후킹 문구 | true | A/B안 |
| `TEMPLATE` | 템플릿 | true | 레퍼런스 이미지 동반 |
| `RENDER_SPEC` | 렌더 스펙 | false | 결정론적 합성 |
| `VALIDATE` | 검증 | true | 게시 전 점검 |
| `SCHEDULE` | 예약 | false | 결정론적 처리 |

시드 `user_prompt` 원문:

- **TRANSCRIBE** — `이 영상에서 오디오를 뽑아서 전사해 줘.`
- **REFRAME** — `9대 16 비율, 그러니까 1080x1920 사이즈로 바꾸고 얼굴이 들어가게 양옆을 잘라서 새로운 형태로 만들어 줘. 인물이 중심에 오게 세팅해 줘.`
- **SEGMENT** — `전사본을 읽고, 앞뒤 설명 없이 그것만 봐도 말이 되는 40초에서 60초 사이의 구간을 맥락에 맞게 개수도 정해서 알아서 뽑아 줘.`
- **SUBTITLE** — `자막은 글자수로 끊지 말고 맥락 위주로 끊되, 한 줄이 다섯 자에서 아홉 자 사이가 되게 맞춰 줘. 쇼츠는 자막이 빠르게 치고 들어가야 눈이 따라온다.`
- **HOOK** — `각 쇼츠마다 후킹 문구를 A안과 B안 두 가지로 만들어 줘.`
- **TEMPLATE** — `위아래 검은 배경을 넣고 인물을 가운데 두는 템플릿으로 쇼츠를 만들어 줘. 첨부한 레퍼런스 캡처와 같은 형태로 맞춰 줘.`
- **RENDER_SPEC** — `확정된 컷 구간, 크롭 박스, 자막 타이밍, 후킹 문구, 템플릿 값을 합쳐 렌더 지시서를 만들어 줘.`
- **VALIDATE** — `화면이 깨지지 않았는지, 소리가 찢어지지 않았는지, 문단이 제대로 나뉘었는지, 후킹이 제대로 나왔는지 검증해 줘.`
- **SCHEDULE** — `8월 13일부터 하루 간격으로 오전 7시에 예약해 줘.`

`SEGMENT`의 `system_prompt`에는 영상이 지적한 안티패턴을 명시한다:

```
반드시 지킬 것: "반응이 좋았던 구간" 또는 "네가 핵심이라고 생각하는 구간"을 고르는 방식으로
판단하지 마세요. 판단 기준은 오직 하나입니다 — 롱폼을 보지 않은 사람이 그 클립만 보고도
내용을 이해할 수 있는가.
```

모든 시스템 프롬프트 끝에는 기존 `PromptTemplates.INJECTION_GUARD`와 동일한 인젝션 가드
문구를 포함한다.

### 5.3 API 계약

모든 응답은 `ResData<T>`로 감싼다. 인증은 `@CurrentUser userId: Long`.

기존 UGC 컨트롤러 관례를 그대로 따라 **워크스페이스를 경로 변수로 받는다**
(`CampaignController`가 `/api/v1/workspaces/{workspaceId}/ugc/campaigns`를 쓰는 것과 동일).
아래 표기의 `{base}`는 `/api/v1/workspaces/{workspaceId}/ugc/shorts`를 뜻한다.

#### 프롬프트

```
GET    {base}/prompts
       → ResData<ShortsPromptResponse[]>   9단계 전체, stage 순서 고정

GET    {base}/prompts/{stage}
       → ResData<ShortsPromptResponse>

PUT    {base}/prompts/{stage}
       body: { systemPrompt: string|null, userPrompt: string, changeNote: string|null }
       → ResData<ShortsPromptResponse>     워크스페이스 오버라이드 생성/갱신, revision +1

DELETE {base}/prompts/{stage}
       → ResData<ShortsPromptResponse>     오버라이드 삭제 = 기본값 복원

GET    {base}/prompts/{stage}/revisions
       → ResData<ShortsPromptRevisionResponse[]>   최신순

POST   {base}/prompts/{stage}/revisions/{revision}/restore
       → ResData<ShortsPromptResponse>     해당 개정 내용으로 새 개정 생성
```

```ts
interface ShortsPromptResponse {
  id: number
  stage: PipelineStage          // 'TRANSCRIBE' | 'REFRAME' | ... | 'SCHEDULE'
  name: string
  description: string | null
  systemPrompt: string | null
  userPrompt: string
  executable: boolean
  revision: number
  customized: boolean           // true = 워크스페이스 오버라이드, false = 시스템 기본값
  defaultSystemPrompt: string | null   // 복원 미리보기용
  defaultUserPrompt: string
  updatedAt: string | null
}

interface ShortsPromptRevisionResponse {
  revision: number
  systemPrompt: string | null
  userPrompt: string
  changeNote: string | null
  changedBy: number
  createdAt: string
}
```

#### 템플릿

```
GET    {base}/templates            → ResData<ShortsTemplateResponse[]>
POST   {base}/templates            → ResData<ShortsTemplateResponse>
GET    {base}/templates/{id}       → ResData<ShortsTemplateResponse>
PUT    {base}/templates/{id}       → ResData<ShortsTemplateResponse>
DELETE {base}/templates/{id}       → ResData<Unit>
POST   {base}/templates/{id}/reference-image
       multipart: file              → ResData<ShortsTemplateResponse>
```

```ts
interface ShortsTemplateResponse {
  id: number
  name: string
  description: string | null
  aspectRatio: string           // '9:16'
  width: number
  height: number
  backgroundStyle: 'BLACK_BARS' | 'BLURRED' | 'SOLID'
  hookFontFamily: string | null
  hookFontSize: number | null
  hookFontColor: string | null
  hookStrokeColor: string | null
  hookPosition: 'TOP' | 'CENTER' | 'BOTTOM'
  captionFontFamily: string | null
  captionFontSize: number | null
  captionFontColor: string | null
  captionStrokeColor: string | null
  captionPosition: 'TOP' | 'CENTER' | 'BOTTOM'
  safeAreaTop: number
  safeAreaBottom: number
  referenceImageUrl: string | null
  isDefault: boolean
  createdAt: string | null
  updatedAt: string | null
}
```

템플릿 생성/수정 요청 본문은 위에서 `id`, `referenceImageUrl`, `createdAt`, `updatedAt`을
제외한 필드로 구성한다. 레퍼런스 이미지는 별도 multipart 엔드포인트로 올린다.

#### 오류

기존 관례대로 `BusinessException`을 던진다.

| 코드 | 상황 |
|---|---|
| `SHORTS_PROMPT_STAGE_INVALID` | 알 수 없는 stage |
| `SHORTS_PROMPT_NOT_CUSTOMIZED` | 기본값 복원 대상 오버라이드 없음 |
| `SHORTS_PROMPT_REVISION_NOT_FOUND` | 롤백 대상 개정 없음 |
| `SHORTS_TEMPLATE_NOT_FOUND` | 템플릿 없음 |
| `ACCESS_DENIED` | 다른 워크스페이스 자원 접근 |

### 5.4 권한과 크레딧

- `Permission`에 `SHORTS_PIPELINE_VIEW`, `SHORTS_PIPELINE_MANAGE` 추가.
  `RolePermissions.kt` 기본 매핑도 갱신 — `CAMPAIGN_VIEW`/`CAMPAIGN_MANAGE`와 같은 역할에 부여.
- Phase 1은 AI를 호출하지 않으므로 크레딧 차감 없음.
  `AiFeature` 추가(`SHORTS_SEGMENT` 8, `SHORTS_HOOK` 5, `SHORTS_SUBTITLE` 5, `SHORTS_VALIDATE` 3)는
  Phase 2에서 수행한다.

### 5.5 파일 배치

**백엔드**

```
onGo-api/src/main/resources/db/migration/V55__create_ugc_shorts_prompts_and_templates.sql
onGo-api/.../api/ugc/ShortsPromptController.kt
onGo-api/.../api/ugc/ShortsTemplateController.kt

onGo-domain/.../domain/ugc/shorts/PipelineStage.kt
onGo-domain/.../domain/ugc/shorts/ShortsPrompt.kt
onGo-domain/.../domain/ugc/shorts/ShortsPromptRevision.kt
onGo-domain/.../domain/ugc/shorts/ShortsPromptRepository.kt
onGo-domain/.../domain/ugc/shorts/ShortsTemplate.kt
onGo-domain/.../domain/ugc/shorts/ShortsTemplateRepository.kt

onGo-application/.../application/ugc/shorts/ShortsPromptUseCase.kt
onGo-application/.../application/ugc/shorts/ShortsTemplateUseCase.kt
onGo-application/.../application/ugc/shorts/ShortsPromptDefaults.kt
onGo-application/.../application/ugc/shorts/dto/ShortsPromptDtos.kt
onGo-application/.../application/ugc/shorts/dto/ShortsTemplateDtos.kt

onGo-infrastructure/.../persistence/jooq/ShortsPromptJooqRepository.kt
onGo-infrastructure/.../persistence/jooq/ShortsTemplateJooqRepository.kt
onGo-infrastructure/.../persistence/jooq/Tables.kt          (상수 추가)
onGo-common/.../common/enums/Permission.kt                  (권한 추가)
onGo-domain/.../domain/team/RolePermissions.kt              (매핑 추가)
```

**프론트엔드**

```
frontend/src/api/ugcShortsPrompt.ts
frontend/src/api/ugcShortsTemplate.ts
frontend/src/stores/ugcShorts.ts
frontend/src/views/ugc/ShortsPromptsView.vue
frontend/src/views/ugc/ShortsTemplatesView.vue
frontend/src/router/index.ts                (라우트 2개)
frontend/src/locales/ko/common.json         (기존 ugc 키 아래 shorts 추가)
frontend/src/locales/en/common.json         (동일 키를 영어로)
frontend/src/views/UserManualView.vue       (sectionsKo / sectionsEn 양쪽)
```

i18n은 로케일별 단일 파일 구조다. `locales/{ko,en}/common.json`에 이미 최상위 `ugc` 키가
있으므로 그 아래 `ugc.shorts.*`로 추가한다. 두 언어를 반드시 같이 갱신한다.

### 5.6 화면 요구사항

API 클라이언트는 기존 `frontend/src/api/ugcCampaign.ts` 패턴을 그대로 따른다 — 모듈 상단에
`const base = (workspaceId: number) => \`/workspaces/${workspaceId}/ugc/shorts/prompts\`` 를 두고,
모든 함수가 `workspaceId`를 첫 인자로 받으며 `unwrapResponse`로 `ResData`를 벗긴다.
라우터 경로에는 `workspaceId`를 넣지 않는다(기존 UGC 라우트와 동일).

**ShortsPromptsView** (`/ugc/shorts/prompts`)

- `PageHeader` 사용
- 9단계를 카드 목록으로 표시. 각 카드에 단계명·설명·`executable` 배지·`customized` 배지
- 카드 클릭 → `BaseModal`로 편집. 시스템/사용자 프롬프트 textarea, 변경 메모 입력
- "기본값으로 복원" 버튼 — `ConfirmModal`로 확인 후 DELETE
- "개정 이력" 탭 — 개정 목록과 각 개정으로 롤백하는 버튼
- 기본값과 현재 값이 다르면 카드에 "수정됨" 표시

**ShortsTemplatesView** (`/ugc/shorts/templates`)

- `PageHeader` + 템플릿 카드 그리드, `EmptyState` 처리
- 생성/수정 모달 — 크기, 배경 스타일, 후킹/자막 폰트·크기·색·위치, 세이프에어리어
- 레퍼런스 이미지 업로드와 미리보기
- 기본 템플릿 지정 토글

공통 규칙 — `PageHeader`/`OTabs`/`BaseModal`/`ConfirmModal`/`EmptyState`/`LoadingSpinner` 사용,
`.card`·`btn-primary`·`btn-secondary`·`btn-danger`·`input-field` 클래스, `primary-*` 색 토큰만
사용(indigo 금지), `mobile:/tablet:/desktop:` 브레이크포인트, `mx-auto` 금지.

## 6. Phase 2·3 개요 (이번 범위 아님)

**Phase 2 — 파이프라인 실행**: V56, 9단계 오케스트레이션, STT 연결, 후킹 A/B 선택,
렌더 스펙 산출물 3종, 단계별 재실행, `AiFeature` 추가.

**Phase 3 — 예약·엑셀·검증**: V57, Apache POI 양방향(가져오기 시 변경 diff 확인),
`dedup_key` 중복 방지, 검증 규칙, `ShortsPublishAdapter` → `PublishVideoUseCase` 위임.

## 7. 테스트

- 도메인 단위 테스트 — `PipelineStage` 매핑, 프롬프트 개정 증가 규칙
- UseCase 테스트 — 오버라이드 생성/복원/롤백, 워크스페이스 격리
- jOOQ 통합 테스트 — 기존 `Ugc*JooqRepositoryIT` 패턴을 따라 Testcontainers 사용
- 프론트 — `npm run build` 타입 체크 통과

## 8. 검증 기준

- `./gradlew build` 통과
- `cd frontend && npm run build` 통과
- V55 적용 후 `ugc_shorts_prompts`에 시스템 기본값 9행 존재
- 프롬프트 편집 → 개정 2 생성 → 이력 조회 → 롤백 → 기본값 복원까지 왕복 동작
