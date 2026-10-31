# S 보고 — 클립 선택·편집·메타 (term_ee09746a) — S1·S2·S3 완료

근거: `02-our-capability.md` A 표(클립 선택·편집·게시 연결 행). 저장(커밋)은 하지 않았다 — 조정 세션이 한다.
마이그레이션은 **V119 하나**만 썼다.

---

## S1 — 클립 점수·정렬·개수/길이 지정·문장 경계 보정 ✅

### 무엇이 바뀌었나
| 전 | 후 |
|---|---|
| 전사문을 보고 LLM 이 고른 구간을 그대로 저장 | `ShortsSegmentPolicy` 가 **문장(전사 세그먼트) 경계로 맞추고**, 길이를 못 지키면 버리고, 절반 이상 겹치면 점수 낮은 쪽을 버린 뒤 **점수순**으로 정한 개수만 남긴다 |
| 점수·이유 없음 | 클립마다 `score`(0~100)·`scoreReason`. AI 가 점수를 안 주면 **null** 로 두고 맨 뒤로(0점으로 지어내지 않음) |
| 개수 상한 없음, 길이는 프롬프트 권고뿐 | 실행 생성 때 `segment: { clipCount 1~10, minDurationSec/maxDurationSec 5~180 }`. 비우면 5개·15~90초. 실행에 저장돼 재실행도 같은 조건 |
| 경계가 문장 중간 | 시작은 그 시각을 품은 문장의 처음, 끝은 그 문장의 끝. 넘치면 끝 문장을 빼고 모자라면 붙인다. **그래도 안 되면 버린다(문장 중간을 자르지 않음)** |
| seq = AI 가 낸 순서 | seq 1 = 최고 점수. 자동 예약(`ScheduleStageExecutor` 는 seq 순)도 좋은 클립부터 나간다 |

- 점수 기준은 시스템 프롬프트의 단 하나의 기준("롱폼을 안 본 사람이 이 클립만 보고 이해하는가")을 쪼갠 것이다 — 첫 3초에 주제가 드러나는가 / 클립 안에서 완결되는가 / 앞 맥락이 필요 없는가. "반응 좋을 구간" 점수를 따로 만들면 시스템 지시와 충돌하므로 넣지 않았다.
- 조건·점수 형식은 워크스페이스가 프롬프트를 바꿔도 항상 붙는다.
- 조건에 맞는 구간이 하나도 없으면 `SHORTS_SEGMENT_NO_FIT` 으로 단계 실패 → 기존 규칙대로 그 단계 크레딧 환불.
- **크레딧**: 새 AI 호출 없음. SEGMENT 청구액은 이미 출력 4,096토큰 최악값으로 잡혀 있고, 클립 상한 10개(+여유 2)로 응답이 그 안에 들어오게 했다.
- 멱등 키 지문: 새 `segment` 필드는 null 이면 직렬화하지 않아(`@JsonInclude(NON_NULL)`) 기존 요청을 다시 보내도 지문이 같다.

### 변경 파일
- 신규 `onGo-api/.../db/migration/V119__shorts_clip_score_and_platform_meta.sql` — runs: `segment_clip_count`·`segment_min/max_duration_ms`(+CHECK), clips: `score`(0~100 CHECK)·`score_reason`·`platform_meta JSONB`(S3)
- 신규 `application/ugc/shorts/ShortsSegmentPolicy.kt` (+ `PipelineRun.segmentOptions()`)
- `stage/SegmentStageExecutor.kt` — 조건·점수 기준 프롬프트, 정책 적용, 스냅샷에 버린 수 기록
- `stage/ShortsStageAiResults.kt`(SegmentClip.score/reason/hashtags), `stage/ShortsStageExecutor.kt`(ClipCandidate.score/scoreReason/hashtags)
- `ShortsPipelineOrchestrator.kt` — **applyOutput 의 SEGMENT 분기만**(score·reason·platformMeta 저장)
- `ShortsPipelineUseCase.kt` — `createRun`(조건 검증·저장), `toResponse`(segmentOptions·score·scoreReason)
- `dto/ShortsPipelineDtos.kt` — `CreatePipelineRunRequest.segment`, `SegmentOptionsRequest/Response`, `ShortsClipResponse.score/scoreReason`
- 도메인 `PipelineRun`(segment 3필드), `ShortsClip`(score·scoreReason·platformMetaJson)
- 인프라 `Tables.kt`(Fields 4개), `ShortsRunJooqRepository.kt`(삽입·읽기만 — 갱신에는 넣지 않음, 원본 길이와 같은 규칙), `ShortsClipJooqRepository.kt`
- 프런트 `api/ugcShortsPipeline.ts`(타입), 신규 `views/ugc/shortsSegmentOptions.ts`(입력 검증·점수 구간), 신규 `components/ugc/ShortsClipScore.vue`,
  `ShortsPipelineDetailView.vue`(점수 배지·근거), `ShortsPipelineView.vue`(생성 폼 조건 3칸), `locales/{ko,en}/common.json`(`ugc.shorts.editor.*`)

### 검증
- 백엔드 `./gradlew :onGo-api:compileTestKotlin :onGo-application:test --tests …` **BUILD SUCCESSFUL**
  - `ShortsSegmentPolicyTest` 18 (문장 경계·빈틈·넘침/모자람·맞출 수 없으면 버림·범위 밖·점수 정렬·null 점수 맨 뒤·점수 범위 묶기·개수·중복·조건 검증)
  - `SegmentStageExecutorTest` 10 (기존 5 + 신규 5: 점수 전달·정렬, 저장된 조건이 프롬프트·결과에 반영, 워크스페이스 프롬프트 바꿔도 형식 유지, NO_FIT, 경계 보정)
  - 회귀: `ShortsPipelineOrchestratorTest` 30, `ShortsPipelineUseCaseTest` 78, `ScheduleStageExecutorTest` 12, `ShortsPublishAdapterTest` 1 — 전부 통과
- 기존 시험 변경 2곳(완화 아님):
  - `SegmentStageExecutorTest` "정상 추출": 전사 세그먼트가 0~5초 하나뿐인데 후보가 0~30초라 경계 보정 뒤 버려졌다. 세그먼트를 후보를 덮는 실제 모양(3개)으로 바꿨고, 단언은 그대로 정확 비교.
  - 같은 시험의 입력 스냅샷 기대값을 새 형식(조건 포함)으로 — 여전히 문자열 전체 일치.
- 프런트 `vitest run shortsSegmentOptions.test.ts ShortsPipelineView.test.ts` **41 통과**(신규: 조건 변환 7 + 화면 2 — 입력한 값만 보냄, 범위 오류면 실행을 만들지 않고 알림). 기존 "조건 비우면 요청이 예전과 같다" 시험 그대로 통과.
- `vue-tsc --noEmit -p tsconfig.app.json` 통과.

### 남은 점
- 실행 중 조건을 바꿔 다시 고르는 길(재실행 API 에 조건 받기)은 없다. 지금은 새 실행을 만들어야 한다.
- 실제 모델로 돌린 품질 측정은 하지 않았다(운영 AI 키를 쓰지 않음). 점수와 실제 성과의 상관은 게시 후 데이터로 봐야 한다.

---

## S2 — 클립 편집기 ✅

### 무엇이 되나
결과 카드의 **편집** → 모달에서 구간(초)·제목·설명·자막 줄(추가/삭제/문구·시각)·크롭(x/y/너비/높이, 원본 미리보기 위 사각형)·플랫폼별 메타를 고쳐 저장.
- API: `GET …/runs/{runId}/clips/{clipId}/editor`(VIEW), `PATCH …/runs/{runId}/clips/{clipId}`(MANAGE). 보낸 항목만 바꾼다. 크레딧 없음(AI 호출 없음).
- **구간 다듬기**: 자막(클립 기준 상대 ms)을 같은 양만큼 옮기고, 범위 밖 줄은 버리고, 경계에 걸린 줄은 잘라 그 안의 단어 시각만 남긴다. 늘린 구간에는 자막을 지어내지 않고 `uncoveredSubtitleSpan` 으로 알린다. 원본 길이·5~180초 검사.
- **자막 줄 수정**: 문구·시각이 기존 줄과 완전히 같은 줄만 `words` 를 이어받는다. 고친 줄은 `words` 를 지운다(공유 형식 규칙). 빈 줄·범위 밖·겹침·200자 초과는 거절.
- **크롭**: `ShortsCropPolicy.normalizeJson(json, sourceWidth, sourceHeight)` 반환값을 그대로 저장. 화면은 미리보기 `<video>` 의 `videoWidth/videoHeight` 를 함께 보낸다. 깨진 입력은 `SHORTS_CROP_INVALID`.
- **낡은 결과를 남기지 않는다**: 구간·자막·크롭이 실제로 바뀌고 렌더 지시서가 있던 클립이면 → 지시서 재생성, 상태 `RENDER_READY`, `renderedVideoId=null`, 끝난 렌더 작업 삭제(`deleteFinished`, 조건부). 그래서 "다시 렌더"가 실제로 새로 인코딩한다(기존 렌더 API 는 끝난 작업을 그대로 돌려주므로 이게 없으면 다시 렌더가 안 됐다). 제목·설명·메타만 바꾸면 렌더를 건드리지 않는다.
- **편집 불가**: 실행 중 / 렌더 대기·중 / 예약·게시 후 / 버린 클립 → `SHORTS_CLIP_NOT_EDITABLE`, 아무것도 저장 안 함. 화면은 서버가 준 이유를 그대로 보이고 입력·저장을 잠근다.
- **경합**: 클립을 먼저 갱신하고 끝난 렌더 작업을 지운다. 그 사이 렌더가 시작됐으면 삭제가 실패 → 예외로 트랜잭션 전체 롤백.
- 다른 워크스페이스의 실행·클립은 404(존재 숨김).
- 화자 추적(R3, 조정 세션) 버튼을 크롭 영역에 연결했다. 저장 안 한 편집이 있으면 잠근다(결과로 화면을 다시 채우면 그 편집이 사라지므로).

### 변경 파일
- 신규 `application/ugc/shorts/ShortsClipEditUseCase.kt`, `ShortsClipEditPolicy.kt`, `dto/ShortsClipEditDtos.kt`
- `onGo-api/.../ugc/ShortsPipelineController.kt` — 편집기 조회·저장 2개(둘 다 `@RequiresPermission`)
- 도메인 `ShortsRenderJob.kt`(`ShortsRenderJobRepository.deleteFinished`), 인프라 `ShortsRenderJobJooqRepository.kt`
- 프런트 신규 `components/ugc/ShortsClipEditorModal.vue`, `views/ugc/shortsClipEditor.ts`(바뀐 항목만 요청에 담기), `api/ugcShortsPipeline.ts`(getClipEditor/editClip·타입),
  `ShortsPipelineDetailView.vue`(편집 버튼·모달·저장 후 검수 캐시 무효화·알림), i18n `ugc.shorts.editor.*`

### 검증
- `ShortsClipEditPolicyTest` 13, `ShortsClipEditUseCaseTest` 11 — 통과 (다듬기 시 자막·단어 이동, 렌더 지시서 재생성·작업 삭제·연결 해제, 제목만 고치면 렌더 유지, 크롭 정규화 그대로 저장, 깨진 크롭 거절, 편집 불가 6경우 저장 0회, 경합 시 실패, 원본 길이 초과, 렌더 전 클립, 다른 워크스페이스 404, 편집기 데이터)
- 프런트 `shortsClipEditor.test.ts` 12, `ShortsClipEditorModal.test.ts` 11 — 통과
- **찾아 고친 버그**: Vue 는 `type="number"` 입력의 v-model 값을 number 로 넣는다. 처음 만든 변환이 문자열만 받아 구간을 고치는 순간 계산이 예외로 멈췄다(모달 시험이 잡음). 숫자·문자열 둘 다 받고, "2"/"2.0"/2 를 같은 값으로 보게 했다.

## S3 — 플랫폼별 제목·설명·해시태그 → 게시 ✅

### 설계 결정 — 새 AI 호출을 만들지 않았다
플랫폼마다 AI 로 따로 쓰면 실행당 약 12크레딧이 더 든다(최악 모델·출력 4,096토큰 기준 계산 — SEGMENT 8크레딧의 1.5배). 플랫폼 사이 차이는 대부분 길이·해시태그 규칙이라 결정론으로 맞출 수 있다.
- **해시태그만** SEGMENT 응답에 함께 받는다(추가 호출·추가 청구 없음, 출력 상한 안).
- 플랫폼별 제목·설명은 클립 제목·설명을 **게시 검증이 쓰는 그 한도표**(`PlatformUploadCapabilities`)와 캡션 합성 규칙(`PlatformCaptionRules`)으로 맞춘다. 캡션 한도를 넘으면 설명 끝 → 해시태그 순으로 줄인다.
- 해시태그: `#`·공백 제거, 중복(대소문자 무시)·30자 초과·특수문자 제외, 플랫폼당 5개 이하.
- 사용자는 편집기에서 플랫폼별로 고친다. **편집은 한도를 넘으면 자르지 않고 거절**한다(사용자 글이 모르게 잘리면 안 됨).

### 게시 연결 — 태그 빈 값 제거
`ShortsPublishAdapter` 가 대상마다 `ShortsPlatformMeta.forTarget` 으로 값을 고른다: 그 플랫폼 메타 → (없으면) 클립 제목·설명 + 다른 플랫폼 해시태그 → (그것도 없으면) **원본 영상 태그** → 그래도 없으면 빈 목록(지어내지 않음).
예전에는 `tags = emptyList()` 고정이었다. 또 클립 제목이 플랫폼 제목 한도를 넘으면 게시에서 거절됐는데, 이제 게시 전에 한도로 맞춘다.

### 변경 파일
- 신규 `application/ugc/shorts/ShortsPlatformMeta.kt`
- `ShortsPublishAdapter.kt`(메타·원본 태그로 제목·설명·태그 채우기, 요청에 `platformMetaJson`·`sourceVideoId`)
- `stage/ScheduleStageExecutor.kt` — `ShortsPublishRequest` 생성 한 줄에 두 값 전달만
- SEGMENT 쪽 해시태그(S1 파일들), 오케스트레이터 SEGMENT 분기에서 `platform_meta` 기본값 저장
- 편집기 메타 편집(S2 파일들)

### 검증
- `ShortsPlatformMetaTest` 9 — **모든 플랫폼에 대해 "맞춘 값이 게시 검증 한도를 통과"를 같은 한도표로 다시 검사**, 캡션 한도 시 설명 먼저 줄임, 저장된 메타 사용, 대체 순서(다른 플랫폼 태그 → 원본 태그 → 빈 목록), 깨진 저장값, 편집 거절 5경우
- `ShortsPublishAdapterTest` 4(기존 1 + 신규 3: 메타로 게시·태그 채움, 원본 태그 대체, 긴 제목 한도 맞춤)

---

## DB 확인 (Testcontainers 대신)
Docker 가 꺼져 있어 `*IT` 통합 시험은 돌리지 못했다. 대신 로컬 PostgreSQL 15 로 `/tmp` 에 일회용 DB 를 띄워 **V1~V119 전체를 버전 순서로 적용 — 성공**, V119 제약 7건을 직접 넣어 확인(클립 11개·최소>최대·181초·점수 101 거절 / 조건 없음·점수 92+메타·점수 없음 통과) 후 DB 삭제.
→ 남은 확인: Docker 를 켠 뒤 `SchemaDriftGuardIT`·`PgEnumDriftGuardTest`·`ShortsRunSourceDurationIT` 류. 새 컬럼은 enum 이 아니라 pg enum 드리프트와는 무관할 것으로 본다(추정).

## 진행 중 사고
- 00:20 에 조정 세션 Gradle(00:17 시작)이 컴파일 단계라 `GradleWorkerMain` 이 없어 놓치고 컴파일을 시작했다. 확인 즉시 내 실행을 멈췄다. 이후로는 `GradleWrapperMain`(실행 요청)까지 확인한다.

## 최종 검증 (11-01)
- 백엔드 `:onGo-application:test` 전체 **2,207개 통과, 실패 0**(235개 결과 파일). 마지막 실행은 빌드 캐시 복원(`FROM-CACHE`)이었다 — 같은 소스·의존성으로 성공한 실행만 캐시되므로 현재 코드 기준 결과다.
- 프런트 `vitest run src/components/ugc src/views/ugc` **116개 통과**(R 쪽 시험 포함), `vue-tsc --noEmit -p tsconfig.app.json` 통과.
- **막힌 것**: `:onGo-infrastructure:compileKotlin` 실패 — `external/platform/InstagramStreamWriter.kt:6,15 Conflicting import 'Platform'`. 내 파일이 아니다(조정 세션 2순위 Instagram 한도 작업으로 보임). 그래서 api 모듈 시험(`UgcPermissionContractTest` 포함)은 이번에 돌지 못했다. 고쳐지면 다시 돌려 달라. 컨트롤러 새 경로 두 개는 모두 `@RequiresPermission` 을 달았다.
- 참고: 집계를 새로 하려고 `onGo-application/build/test-results/test` 를 지우고 다시 돌렸다(빌드 산출물만, 소스 아님).

## 사용자 설명서
- `frontend/src/components/manual/manualSections.ts` — "쇼츠 실행" 항목에 한국어·영어 각 6줄(클립 고르기 조건, 점수·정렬, 편집기, 자막 강조 꺼짐, 편집 불가 조건, 게시 메타). 줄 단위 삽입만. 설명서 시험 4개·타입 검사 통과.

## 조정 세션에 넘길 것
- 커밋 범위: 위 S1·S2·S3 "변경 파일" 목록 전체 + V119. 내가 건드린 공유 파일은 `00-plan.md` "편집 중 (S)" 에 적은 범위뿐이다.
- `creditCostForStage` 변경 요청은 조정 세션이 취소했으므로 연결하지 않았다.
