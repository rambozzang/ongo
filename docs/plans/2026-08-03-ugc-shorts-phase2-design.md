# UGC 쇼츠 파이프라인 Phase 2 — 실행 오케스트레이션

작성일: 2026-08-03
선행: Phase 1 (프롬프트·템플릿 관리) 완료, 커밋 `f82403b`
전체 설계: `docs/plans/2026-08-03-ugc-shorts-pipeline-design.md`

## 1. 목표

롱폼 영상 하나를 넣으면 클립 여러 개가 나오고, 사람이 후킹 문구를 고르면 렌더 지시서까지
만들어지는 파이프라인. 참고 영상의 "이 영상 쇼츠로 만들어 줘" 한 마디에 해당한다.

## 2. 실행 모델 — 자동 실행 + 2개 게이트 + 단계별 재실행

```
[생성] → TRANSCRIBE → REFRAME → SEGMENT → SUBTITLE → HOOK
                                                       ↓
                                        ★ AWAITING_HOOK_SELECTION  (사람이 A/B 선택)
                                                       ↓
                                         TEMPLATE → RENDER_SPEC → VALIDATE
                                                       ↓
                                        ★ AWAITING_SCHEDULE  (사람이 예약 확정)
                                                       ↓
                                                   SCHEDULE → COMPLETED
```

게이트는 두 곳뿐이다. 나머지는 자동으로 이어 달린다.
어느 단계든 프롬프트를 고친 뒤 **그 단계부터 다시 실행**할 수 있다. 재실행하면 그 단계와
이후 단계의 결과가 무효화되고 다시 계산된다.

### 비동기 처리

기존 `VideoPublishEventListener` 패턴을 그대로 따른다 — 컨트롤러는 실행을 등록하고 즉시
반환하며, `ApplicationEventPublisher`로 이벤트를 쏘고 `@Async` 리스너가 가상 스레드에서
단계를 순서대로 돈다. 프론트는 실행 상태를 폴링한다.

### 크레딧

AI를 호출하는 단계마다 차감한다. 단계 실패 시 그 단계분만 환불한다
(기존 `RepurposeUseCase`의 환불 패턴과 동일).

| Stage | AiFeature | 크레딧 |
|---|---|---|
| TRANSCRIBE | 기존 `STT` 재사용 | 10 |
| REFRAME | `SHORTS_REFRAME` | 3 |
| SEGMENT | `SHORTS_SEGMENT` | 8 |
| SUBTITLE | `SHORTS_SUBTITLE` | 5 |
| HOOK | `SHORTS_HOOK` | 5 |
| TEMPLATE | `SHORTS_TEMPLATE` | 3 |
| VALIDATE | `SHORTS_VALIDATE` | 3 |

`RENDER_SPEC`과 `SCHEDULE`은 AI를 쓰지 않으므로 차감하지 않는다.

## 3. 데이터 모델 (V56)

```sql
CREATE TABLE ugc_shorts_pipeline_runs (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    source_video_id BIGINT NOT NULL,
    template_id     BIGINT,                      -- ugc_shorts_templates.id (NULL 이면 기본 템플릿)
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    current_stage   VARCHAR(30),
    transcript_text TEXT,
    clip_count      INT NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_ugc_shorts_runs_workspace ON ugc_shorts_pipeline_runs(workspace_id, created_at DESC);
CREATE INDEX idx_ugc_shorts_runs_user ON ugc_shorts_pipeline_runs(user_id, status);

CREATE TABLE ugc_shorts_run_stages (
    id              BIGSERIAL PRIMARY KEY,
    run_id          BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    stage           VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    prompt_id       BIGINT,       -- 실행에 쓴 프롬프트 (추적용 스냅샷)
    prompt_revision INT,
    ai_provider     VARCHAR(20),
    credit_cost     INT NOT NULL DEFAULT 0,
    input_snapshot  JSONB,
    output_snapshot JSONB,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_run_stages UNIQUE (run_id, stage)
);
CREATE INDEX idx_ugc_shorts_run_stages_run ON ugc_shorts_run_stages(run_id);

CREATE TABLE ugc_shorts_clips (
    id                BIGSERIAL PRIMARY KEY,
    run_id            BIGINT NOT NULL REFERENCES ugc_shorts_pipeline_runs(id) ON DELETE CASCADE,
    seq               INT NOT NULL,
    start_ms          BIGINT NOT NULL,
    end_ms            BIGINT NOT NULL,
    title             VARCHAR(300),
    caption           TEXT,
    subtitle_json     JSONB,
    crop_json         JSONB,
    render_spec       JSONB,
    status            VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    dedup_key         VARCHAR(120),
    rendered_video_id BIGINT,
    scheduled_at      TIMESTAMP,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_clips_seq UNIQUE (run_id, seq),
    CONSTRAINT chk_ugc_shorts_clips_range CHECK (end_ms > start_ms)
);
CREATE UNIQUE INDEX uq_ugc_shorts_clips_dedup
    ON ugc_shorts_clips(dedup_key) WHERE dedup_key IS NOT NULL;

CREATE TABLE ugc_shorts_clip_hooks (
    id         BIGSERIAL PRIMARY KEY,
    clip_id    BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    variant    VARCHAR(10) NOT NULL,     -- A / B / CUSTOM
    text       VARCHAR(300) NOT NULL,
    selected   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_clip_hooks UNIQUE (clip_id, variant)
);
CREATE INDEX idx_ugc_shorts_clip_hooks_clip ON ugc_shorts_clip_hooks(clip_id);
```

### 상태값

- `PipelineRunStatus`: `PENDING`, `RUNNING`, `AWAITING_HOOK_SELECTION`, `AWAITING_SCHEDULE`, `COMPLETED`, `FAILED`, `CANCELLED`
- `RunStageStatus`: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `SKIPPED`
- `ClipStatus`: `DRAFT`, `HOOK_SELECTED`, `RENDER_READY`, `RENDERED`, `SCHEDULED`, `PUBLISHED`, `FAILED`, `DISCARDED`

`dedup_key`는 `"{runId}:{seq}"` 형식으로 클립 생성 시 채운다. 중복 게시 차단은 Phase 3에서
플랫폼을 붙여 확장한다.

## 4. 렌더 산출물 3종

`RENDER_SPEC` 단계가 클립마다 아래를 만들어 `render_spec` JSONB에 담고, 다운로드 API로
내보낸다. 실제 인코딩은 하지 않는다.

**render-spec.json**
```json
{
  "clipSeq": 1,
  "source": { "videoId": 123, "fileUrl": "..." },
  "cut": { "startMs": 65000, "endMs": 118000 },
  "reframe": { "targetWidth": 1080, "targetHeight": 1920,
               "crop": { "x": 420, "y": 0, "width": 1080, "height": 1920 } },
  "hook": { "text": "유튜브로 리스크 없이 시작하는 법", "position": "TOP" },
  "subtitles": [ { "startMs": 0, "endMs": 1200, "text": "쇼츠를 멈추는" } ],
  "template": { "id": 3, "backgroundStyle": "BLACK_BARS", "captionFontFamily": "Pretendard" }
}
```

**clip-{seq}.ass** — 템플릿의 폰트·색·외곽선을 `[V4+ Styles]`에 반영하고, 자막 세그먼트를
`Dialogue` 줄로 적는다. 후킹 문구는 클립 전체 구간에 걸친 별도 스타일 줄로 넣는다.

**render.sh** — 위 둘을 물린 ffmpeg 명령. 크롭 → 스케일 → 자막 번인 순서로 필터를 건다.

```bash
ffmpeg -ss {start} -to {end} -i "{source}" \
  -vf "crop={w}:{h}:{x}:{y},scale=1080:1920,ass=clip-{seq}.ass" \
  -c:v libx264 -preset medium -crf 20 -c:a aac -b:a 128k \
  "clip-{seq}.mp4"
```

## 5. API

베이스: `/api/v1/workspaces/{workspaceId}/ugc/shorts/runs`

```
POST   {base}                          실행 생성 및 시작
       body: { sourceVideoId, templateId? }
GET    {base}                          실행 목록 (페이지네이션)
GET    {base}/{runId}                  실행 상세 (단계 + 클립 + 후킹 포함)
POST   {base}/{runId}/stages/{stage}/rerun     해당 단계부터 재실행
POST   {base}/{runId}/hooks             후킹 일괄 선택
       body: { selections: [ { clipId, variant, customText? } ], discardClipIds: [] }
POST   {base}/{runId}/schedule          예약 확정
       body: { startAt, intervalHours, platforms: [] }
GET    {base}/{runId}/clips/{clipId}/render-spec    render-spec.json 다운로드
GET    {base}/{runId}/render-bundle     3종 산출물 zip 다운로드
DELETE {base}/{runId}                   실행 취소/삭제
```

응답 DTO는 `ResData<T>`로 감싼다.

```ts
interface PipelineRunResponse {
  id: number
  sourceVideoId: number
  sourceVideoTitle: string | null
  templateId: number | null
  status: PipelineRunStatus
  currentStage: PipelineStage | null
  clipCount: number
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

interface PipelineRunDetailResponse {
  run: PipelineRunResponse
  stages: RunStageResponse[]
  clips: ShortsClipResponse[]
}

interface RunStageResponse {
  stage: PipelineStage
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
  promptId: number | null
  promptRevision: number | null
  aiProvider: string | null
  creditCost: number
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
}

interface ShortsClipResponse {
  id: number
  seq: number
  startMs: number
  endMs: number
  durationMs: number
  title: string | null
  caption: string | null
  status: string
  scheduledAt: string | null
  hooks: ClipHookResponse[]
  subtitleCount: number
  hasRenderSpec: boolean
}

interface ClipHookResponse {
  id: number
  variant: 'A' | 'B' | 'CUSTOM'
  text: string
  selected: boolean
}
```

### 오류 코드

| 코드 | 상황 |
|---|---|
| `SHORTS_RUN_NOT_FOUND` | 실행 없음 |
| `SHORTS_RUN_INVALID_STATE` | 현재 상태에서 불가능한 요청 (예: 후킹 미선택 상태에서 예약) |
| `SHORTS_CLIP_NOT_FOUND` | 클립 없음 |
| `SHORTS_STAGE_NOT_RERUNNABLE` | 재실행 불가 단계 |
| `SHORTS_SOURCE_VIDEO_NOT_FOUND` | 원본 영상 없음 |
| `ACCESS_DENIED` | 다른 워크스페이스/사용자 자원 |

## 6. 화면

`ShortsPipelineView` (`/ugc/shorts/runs`, `/ugc/shorts/runs/:id`)

- 실행 목록 — 원본 영상, 상태 배지, 클립 수, 생성 시각
- 실행 생성 — 영상 선택 + 템플릿 선택 모달
- 실행 상세
  - 9단계 진행 표시 (단계별 상태·소요 시간·사용 크레딧·사용 프롬프트 개정)
  - 단계마다 "이 단계부터 다시 실행" 버튼
  - `AWAITING_HOOK_SELECTION` 이면 클립별 A/B 카드와 직접 입력란, 클립 제외 토글
  - `AWAITING_SCHEDULE` 이면 시작 일시·간격·플랫폼 입력
  - 렌더 산출물 다운로드 버튼
- 실행 중에는 3초 간격 폴링, 종료 상태면 중단

공통 규칙은 Phase 1과 동일하다 — `PageHeader`/`OTabs`/`BaseModal`/`ConfirmModal`/
`EmptyState`/`LoadingSpinner`, `.card`·`btn-*`·`input-field`, `primary-*` 토큰만,
`mobile:/tablet:/desktop:` 브레이크포인트, `mx-auto` 금지.

## 7. 테스트

- 상태 전이 규칙 (게이트에서 멈추는지, 잘못된 전이를 막는지)
- 단계 재실행 시 이후 단계 무효화
- 후킹 선택 반영과 클립 제외
- 렌더 스펙 생성 결과의 구조와 ffmpeg 명령 문자열
- 크레딧 차감·실패 시 환불
- 워크스페이스 격리

## 8. 검증 기준

- `./gradlew compileKotlin` 통과, 도메인·애플리케이션 테스트 전부 통과
- V56을 실제 PostgreSQL에 적용해 테이블 4개 생성 확인
- `npm run build` 통과
- 프론트 응답 필드명이 5장 TypeScript 인터페이스와 일치
