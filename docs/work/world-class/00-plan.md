# 세계 최고 수준 — 이 분야 3위 안 (작업 계획·계약)

작성 2026-10-31 · Claude(조정). 대표 지시: "현재 수준을 다시 판단해 이 분야에서 무조건 3위 안에 드는 기능을 만들고 완성도를 높여라."
협업: Claude(이 세션, 조정·구현) + Claude(term_ee09746a, 조사·구현). 같은 작업 폴더·같은 브랜치(feature/monetization-publish-hardening).

## 공통 규칙
- `설정_작업.txt` 읽기·수정 금지. 운영 서버·운영 DB·비밀값·실제 결제·실제 외부 SNS 게시 금지.
- 가짜 성공·임의 데이터로 화면을 채우지 않는다. 기존 시험 완화·삭제 금지.
- Gradle 은 **한 번에 한 세션만** 돌린다(같은 폴더에서 동시 실행하면 결과 XML 이 깨진다). 돌리기 전 `pgrep -f GradleWorkerMain` 확인.
- 저장(커밋)은 조정 세션(이 세션)만 한다. 운영 반영(main 합치기)은 하지 않는다.
- 사용자에게 보이는 글은 한국어. CLAUDE.md·.impeccable.md 준수.

## 1단계 — 판단 (동시 진행)
- A. 경쟁 조사 — **term_ee09746a 담당** → `01-competitors.md`
- B. 우리 핵심 흐름 실동작 점검 — **조정 세션 담당** → `02-our-capability.md`

## 2단계 — 3위 안에 드는 기능 선정·구현 (1단계 후 이 문서에 추가)

## 2단계 — 1순위: "한국어 쇼츠 품질 1등" (2026-11-01 확정)

근거: 01-competitors.md 6·7장(생성→게시→성과→댓글을 한 제품에 갖춘 곳 없음, 한국어 쇼츠 품질이 빈자리),
02-our-capability.md A(세로 변환·자막·클립 선택·편집기 격차). 쇼츠 상위 3(Opus·Vizard·Klap)의 필수 기능
— 점수순 자동 추출, 화자 추적 리프레임, 강조 자막, 길이 지정, 바로 게시, 플랫폼별 메타 — 을 모두 갖추고
한국어에서 그들보다 낫게 만든다.

### 공유 데이터 형식 (둘 다 지킨다 — 하위 호환)
- `shorts_clips.subtitle_json`: `[{ startMs, endMs, text, words?: [{ startMs, endMs, text }] }]` — **클립 시작 기준 상대 ms**.
  `words` 는 없을 수 있다(예전 데이터·편집된 줄). 렌더는 `words` 가 있으면 단어 강조, 없으면 줄 단위로 그린다.
  **편집기가 줄 문구를 바꾸면 그 줄의 `words` 를 지운다**(문구와 타이밍이 어긋난 강조를 그리지 않는다).
- `shorts_clips.crop_json`: `{ x, y, width, height, mode?: "CROP"|"FIT_BLUR"|"FIT_BARS", sourceWidth?, sourceHeight?, keyframes?: [{ atMs, x, y, width, height }] }`.
  검증·정규화는 Claude 의 `ShortsCropPolicy`(신규)만 쓴다 — 편집기는 이 함수를 불러 저장한다.

### R — Claude(조정 세션) 소유: 화질·자막
- `ai/SttUseCase.kt`(단어 타임스탬프), `ugc/shorts/stage/SubtitleStageExecutor.kt`, `ugc/shorts/ShortsRenderSpecBuilder.kt`,
  `infrastructure/render/FfmpegVideoRenderer.kt`, `domain/ugc/shorts/VideoRenderer.kt`, `ugc/shorts/stage/ReframeStageExecutor.kt`,
  신규 `ugc/shorts/ShortsCropPolicy.kt`, `ugc/shorts/KoreanCaptionLayout.kt`, 템플릿 스타일 프리셋
- R1 단어 단위 강조 자막 + 한국어 줄바꿈 + 스타일 프리셋, 후킹 첫 몇 초 강조
- R2 9:16 강제·범위 고정, FIT_BLUR/FIT_BARS 실제 반영
- R3 화자 추적 리프레임(클립별 프레임 표본 → 비전 모델 → 키프레임 → 시간에 따른 크롭)

### S — 협업 세션(term_ee09746a) 소유: 클립 선택·편집·메타
- `ugc/shorts/stage/SegmentStageExecutor.kt`, `ShortsPromptDefaults.kt`(SEGMENT 부분), `stage/ShortsStageAiResults.kt`(Segment 결과),
  `ShortsPipelineOrchestrator.kt`(SEGMENT 저장 부분), `ShortsPublishAdapter.kt`, 클립 편집 API(신규 UseCase/Controller 메서드),
  `frontend/src/views/ugc/ShortsPipelineDetailView.vue`, 관련 store/api/types, 마이그레이션 **V119**(필요 시 한 개만)
- S1 클립 점수(0~100)와 이유, 점수순 정렬, 개수·길이 사용자 지정, 경계를 전사 세그먼트(문장) 경계로 보정
- S2 클립 편집기: 구간 다듬기·자막 줄 수정·제목/설명 수정·크롭 조정 → 다시 렌더(크롭은 ShortsCropPolicy 로 정규화)
- S3 클립별 플랫폼 제목·설명·해시태그 → 게시 시 태그 채우기(빈 값 금지)
- 크레딧: 새 AI 호출은 반드시 크레딧을 받고 원가 예산 안에서(project_profit_guarantee 메모리 — `AiUnitEconomics`·`AiSpendContext`).

### 겹치는 파일
- `ShortsPipelineOrchestrator.kt`: S 는 SEGMENT·편집 부분, R 은 REFRAME·TEMPLATE·크롭 복사 부분만. 같은 함수를 동시에 고치지 않는다 — 고치기 전 이 문서에 "편집 중: 함수명" 을 적는다.
- i18n: 각자 `ugc.shorts.editor.*`(S) / `ugc.shorts.caption.*`(R) 키만 추가, 줄 단위 삽입.

### 편집 중 (S — term_ee09746a)
- `ShortsPipelineOrchestrator.applyOutput` 의 **SEGMENT 분기만** (점수·정렬·플랫폼 메타 저장). REFRAME/TEMPLATE 분기는 건드리지 않는다.
- `ShortsPipelineUseCase`: `createRun`(클립 개수·길이 옵션 저장), `ShortsClip.toResponse`(점수·메타 필드 추가)만.
- ~~`ShortsPipelineCreditRequirements`~~ → **안 건드린다.** S3 는 새 AI 호출 없이 SEGMENT 응답에 해시태그만 받고, 플랫폼별 제목·설명은 `PlatformUploadCapabilities` 한도로 결정론적으로 맞춘다(별도 호출이면 실행당 약 12크레딧 추가라 버림).
- `stage/ScheduleStageExecutor.kt`: `ShortsPublishRequest(...)` 생성 한 줄에 `platformMetaJson` 전달만.
- 렌더 작업 저장소에 `deleteFinished(runId, clipId)` 추가(편집 후 낡은 렌더 결과 무효화). `ShortsRenderUseCase` 는 안 건드린다.
- 프런트 `ShortsPipelineView.vue`: 생성 폼에 클립 개수·길이 입력만.
- 도메인 `ShortsClip`·`PipelineRun` 필드 추가와 `ShortsClipJooqRepository`·`PipelineRunJooqRepository` 컬럼 추가(V119).

- 조정 세션 요청 수락(11-01): `creditCostForStage(..., clipCount: Int? = null)` 가 열리면 `ShortsPipelineUseCase` 의 선견적·선검사 호출부에 `run.segmentOptions().effectiveClipCount`(상한)를 넘긴다.
  제안: 실제 TEMPLATE 차감은 그 시점 **버리지 않은 클립 수**로 — 맥락 컷이 상한보다 적게 고르거나 사용자가 버리면 안내액보다 더 받지 않게.

### 편집 중 (R — Claude)
- `ShortsPipelineOrchestrator.loadTranscriptSegments` 만 (전사 스냅샷에서 단어 시각 복원). applyOutput 은 건드리지 않는다.
- `stage/ShortsStageExecutor.kt` 의 `TranscriptSegmentMs` 에 `words` 필드 한 줄 추가(기본값 빈 목록 — 기존 호출 그대로 컴파일됨). ClipCandidate 등 다른 선언은 건드리지 않는다.
- `ai/SttUseCase.kt`·`ai/result/AiStructuredResults.kt`(SttResult.words)·신규 `ai/audio/VerboseTranscriber.kt`·`infrastructure/ai/OpenAiVerboseTranscriber.kt`.

### R 진행 기록
- **발견(운영 영향)**: Spring AI 2.0 의 전사 모델은 VERBOSE_JSON 을 요청해도 본문 글자만 돌려준다(`extractText` → `verbose.text()`).
  그래서 `SttUseCase.parseSegments` 는 늘 빈 목록 → **쇼츠 자막이 비고 SEGMENT 에 타임코드가 안 들어갔다.** `VerboseTranscriber` 로 원문 JSON 을 받아 고친다.
- `ShortsCropPolicy`(정규화 유일 지점) 완료: `normalizeJson(json, sourceWidth?, sourceHeight?)` — 편집기는 영상 요소의 `videoWidth/videoHeight` 를 같이 넘기면 정확하다.
  깨진 입력은 `BusinessException("SHORTS_CROP_INVALID")`. 저장은 반환된 JSON 그대로.
- 렌더: 크롭이 없거나 맞춤 모드면 템플릿 배경(`BLURRED`→흐린 배경, 그 밖→검은 띠). 더는 늘이지 않는다.
- (11-01) R1·R2·R3 구현·시험 통과(도메인·응용·인프라 21개 시험 묶음, 실제 ffmpeg 인코딩 10건 포함).
  - R1: 단어 시각(전사 → 자막 단계 → subtitle_json.words), 한국어 묶음 배치(KoreanCaptionLayout), 프리셋 POP/CLEAN/BOX + 강조색(템플릿 extraSpec), 후킹 첫 3.5초, 가운데 정렬 버그 수정, 세이프 영역.
    한글 글꼴(Pretendard, OFL) 서버 동봉 — `ass=filename=…:fontsdir=…`. 인코더에 자막 필터(libass)가 없으면 가용성에서 미리 "불가".
  - R2: ShortsFilterGraph(서버 렌더·render.sh 공용), 크롭 9:16·범위 고정, FIT_BLUR/FIT_BARS, 크롭 없으면 템플릿 배경.
  - R3: 클립별 "화자 추적 적용"(POST …/clips/{id}/track-speaker) — 장면 12칸 격자 → Qwen 비전 → 머무름-이동 크롭 → 편집기 저장 경로. 적용 시에만 크레딧(실패·인물 부족은 환불). **실제 비전 모델 호출은 검증 못 함**(외부 호출 금지).
  - 편집기 버튼: `components/ugc/ShortsSpeakerTrackButton.vue` — S 가 ShortsClipEditorModal 에 배치.

## 3단계 — 2순위: "믿을 수 있는 숫자 + 내 성과로 배우는 AI" (2026-11-01, 1순위 저장 1cc08164)

경쟁 조사 결론: 조용한 실패·지어낸 숫자 없음 = 신뢰가 차별점. 그리고 생성→게시→분석 루프를 **닫는** 제품이 없다.

### T — 협업 세션 소유: AI 가 지어낸 수치 정리
대상(02 B 절): 키워드 검색량·경쟁도(KEYWORD_RESEARCH), 콘텐츠 갭 수요(CONTENT_GAP_ANALYSIS), 채널 감사 점수·성장 전망(CHANNEL_AUDIT),
수익 예측 시나리오(REVENUE_FORECAST), SEO 점수(VIDEO_SEO_SCORE), 참여율 벤치마크(ENGAGEMENT_BENCHMARK, 컨트롤러 없음), 스케줄 최적화 audienceOnline.
- 각 수치마다: **실측으로 바꿀 수 있으면 실측**(우리 DB 의 analytics_daily 등), 아니면 응답 DTO 에 `estimated: true` + `basis`(무엇을 근거로 추정했는지 한 문장)를 붙이고
  화면에 "AI 추정" 배지와 근거를 보인다. 근거 없이 숫자만 내는 프롬프트는 "모르면 null" 로 바꾼다.
- 공통 배지 컴포넌트 1개(`components/common/AiEstimateBadge.vue`)를 만들어 쓴다. i18n `common.aiEstimate.*`.
- 파일: 위 기능의 UseCase·DTO·프롬프트(PromptTemplates 의 해당 함수만)·뷰. **GenerateMetaUseCase·PromptTemplates 의 메타 생성 함수는 R 소유라 건드리지 않는다.**

### M — Claude 소유: 성과 기반 메타데이터
- GenerateMetaUseCase·메타 프롬프트: 내 채널에서 성과 좋은 영상(실측 조회·참여)의 제목·태그 패턴을 근거로 넣는다. 데이터 부족이면 넣지 않고 그 사실을 응답에 표시.
- 플랫폼 규칙을 PlatformUploadCapabilities 한도표에서 생성(TikTok 150자 오기 수정, 전 플랫폼), 해시태그 수도 플랫폼별.
- Compose 의 tone/category 고정값 문제는 화면 쪽이라 마지막에 본다.

### 겹침
- `PromptTemplates.kt`: T 는 위 대상 함수만, M 은 메타 생성 함수만. 같은 함수를 고치지 않는다.
- `manualSections.ts`·locale: 줄 단위 삽입.

### 편집 중 (T — term_ee09746a)
- `PromptTemplates.kt`: `VIDEO_SEO_*`, `CONTENT_GAP_*`, `CHANNEL_AUDIT_*`, `KEYWORD_RESEARCH_*`, `ENGAGEMENT_BENCHMARK_*`, `SCHEDULE_SUGGESTION_*` 와 **쓰이지 않는** `REVENUE_FORECAST_*`(삭제)만. 메타 생성 함수는 안 건드린다.
- `ai/result/AiStructuredResults.kt`: 위 기능의 결과 타입만(`VideoSeoScoreResult`, `ContentGapResult`, `ChannelAuditResult`, `KeywordResearchResult`, `RevenueForecastResult`(삭제)). `SttResult` 등 R 쪽은 안 건드린다.
- 유스케이스·DTO: `videoseo/*`, `ai/ContentGapAnalysisUseCase`+DTO, `channelaudit/*`, `keyword/*`, `scheduleoptimizer/ScheduleOptimizerUseCase.generateOptimalSlots`, `benchmark/*`. 마이그레이션 없음.
- 프런트: `api/videoSeo.ts`, `VideosView.vue` SEO 모달만, `components/dashboard/ContentGapCard.vue`, `ChannelAuditView.vue`, 신규 `components/common/AiEstimateBadge.vue`, i18n `common.aiEstimate.*`.

### M 진행 기록 (Claude, 11-01)
- 메타 생성: 한도표 기반 플랫폼 규칙(MetaPlatformRules, TikTok 150자 오기 제거, 플랫폼별 해시태그 수), 생성 직후 한도 맞춤,
  내 채널 실측 근거(MetaPerformanceGrounding — 최근 90일 조회수 상위 5·하위 3 제목·태그, 6개 미만이면 미사용+이유). 응답 `grounding` → AiView·Compose 표시.
- "다음에 할 일"(NextActionsUseCase, GET /analytics/next-actions): 최근 14일 조회수 상위인데 쇼츠 실행이 없는 영상 → 오늘 화면 카드 → 쇼츠 실행 만들기(영상 선택된 채로).
  ShortsPipelineView 는 S 가 끝낸 파일이라 onMounted 에 미리 선택 몇 줄만 넣었다.
