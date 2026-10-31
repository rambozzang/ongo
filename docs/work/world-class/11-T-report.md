# T 보고 — AI 가 지어낸 수치 정리 (term_ee09746a) — 완료

근거: `02-our-capability.md` B 절. 원칙: 실측으로 바꿀 수 있으면 실측, 아니면 `estimated + basis` 와 "AI 추정" 배지, 근거 없는 정밀 숫자는 지운다.
저장(커밋)은 하지 않는다. 마이그레이션 없음.

## 조사 결과 (작업 전)
| 대상 | 화면 | 지어낸 것 | 처리 |
|---|---|---|---|
| SEO 점수 | VideosView SEO 모달 | 0~100 점수 4종 전부 AI | **실측 교체**: 제목·설명·태그를 규칙으로 채점, 항목별 근거. AI 는 제안만 |
| 콘텐츠 갭 | 대시보드 카드 | 관련성 점수 0~100, 수요·경쟁 | 점수 삭제, 수요·경쟁은 AI 추정+근거, 영상 0개면 청구 전 거절 |
| 채널 감사 | ChannelAuditView | 종합 점수 0~100, 3개월 성장 전망 | 점수 삭제→실측 30일 지표, 전망은 AI 추정+근거·수치 지어내기 금지 |
| 키워드 리서치 | 없음(API 만) | 기회 점수 1~100, 검색량·경쟁·추세 | 점수 삭제, 나머지 AI 추정+근거, 모르면 null |
| 최적 시간 슬롯 | 없음(API 만) | 예상 시청자 수·경쟁도·점수 | 실측(조회수 중앙값·분석 점수)으로 교체, 데이터 없는 시간대 제외, 경쟁도 삭제 |
| 참여율 벤치마크 | 없음, 컨트롤러도 없음 | 업계 평균·백분위, 내 참여율도 AI 값 | 내 참여율 실측 덮어쓰기, 업계 비교 삭제 |
| 수익 예측 | 없음 | 시나리오·신뢰구간 | 쓰이지 않는 프롬프트·결과·DTO 삭제 |

### 작업 중 발견
- **SEO 모달이 고장 나 있었다**: 프런트는 `categories[]`·`suggestions[]`·`creditsUsed` 를 기대하고 백엔드는 `titleScore`·`titleSuggestions`… 를 준다. `seoModal.result.suggestions.length` 에서 예외 → 결과가 그려지지 않는다(추정, 시험으로 확인 예정). 프런트 시험은 프런트 형태로 목을 만들어 통과하고 있었다.
- 참여율 벤치마크: 프런트 `api/engagementBenchmark.ts`·`stores/engagementBenchmark.ts` 는 있으나 어느 화면도 쓰지 않고, 백엔드 컨트롤러도 없다.


## 무엇을 바꿨나

### 1. SEO 점수 → 실측(규칙 점수) ✅
- 신규 `videoseo/SeoRubric.kt`: 제목(한도·길이·태그 포함)·설명(유무·100자·앞 150자 키워드)·태그(개수·한도·중복·길이)·전반(카테고리·게시 대상·제목 단어) 13개 항목, 합 100점.
  항목마다 통과 여부·점수·**실제 값**(예: "125자 / 한도 100자"). 한도는 게시 검증과 같은 `PlatformUploadCapabilities`, 게시 대상이 여럿이면 가장 엄격한 값.
- AI 에게 점수를 묻지 않는다. 통과 못 한 항목을 프롬프트에 넣어 **제안·관련 키워드만** 받는다(`AiEstimate` "AI 제안" 배지).
- **고장 수리**: 응답을 화면 계약(`categories[].checks`, `suggestions`, `relatedKeywords`, `scoreBasis`, `aiEstimate`, `creditsUsed`)으로 통일. 기존 화면 시험 목도 서버 계약으로 바꿨다.
- 크레딧: 그대로 `VIDEO_SEO_SCORE`(2). 남의 영상은 차감 전 거절, AI 실패는 `withCredits` 안에서 던져 환불.

### 2. 콘텐츠 갭 ✅
- 관련성 점수(0~100) 삭제. 수요·경쟁은 HIGH/MEDIUM/LOW 만 받고 그 밖(예: "월 5만 회")은 null → 화면 "판단 못 함".
- `aiEstimate.basis` 를 **실측 개수로** 만든다: "내 영상 3개의 제목·카테고리·태그와 경쟁 채널 1개의 … 만 보고 AI 가 추정했습니다. 검색량·시장 데이터는 보지 않았습니다."
- 내 영상이 0개면 `CONTENT_GAP_NO_DATA` 로 **AI 호출·차감 전에** 거절.
- api 모듈의 중복 응답 DTO 를 없애고 응용 계층 응답을 그대로 보낸다(새 필드가 옮겨 담기에서 빠지던 구조).

### 3. 채널 감사 ✅
- AI 종합 점수(0~100)를 새로 매기지 않는다(AI 가 보내도 버림). 대신 **감사 시점 실측**(`measured`: 30일 조회수·증감·좋아요·댓글·구독자·분석 영상 수)을 보고서 본문에 함께 저장해 다시 열어도 보인다.
- 예전 보고서의 AI 점수는 지우지 않고 `overallScoreEstimate`(근거: "정해진 계산식이나 측정값이 아니어서 지금은 새로 매기지 않습니다")와 함께만 보인다.
- 성장 전망: 프롬프트에서 미래 수치·증가율 예측 금지, 데이터 부족이면 전망하지 않음. 화면에 AI 추정 배지.
- `overall_score` 컬럼은 NOT NULL 이라 새 보고서에 0 을 넣되 **표시에 쓰지 않는다**(마이그레이션 없음). 본문이 깨졌을 때도 컬럼 값을 점수로 읽지 않는다.

### 4. 키워드 리서치(API 만) ✅
- 기회 점수(1~100) 삭제. 검색량·경쟁도·추세는 정해진 등급만, 그 밖은 null. `aiEstimate`("실제 검색 데이터 없이 AI 모델의 일반 지식으로 추정"), `creditsUsed`.

### 5. 최적 업로드 시간 슬롯(API 만) ✅
- AI 가 지어내던 예상 시청자 수·경쟁도·점수를 버리고 **실측**으로: 조회수 = 그 시간대 게시물 조회수 중앙값, 점수 = 분석 점수를 이번 결과 최고 시간대 대비 %. 경쟁도는 데이터가 없어 `UNKNOWN` 저장·응답 null.
- **측정한 적 없는 시간대는 AI 가 골라도 버린다.** 하나도 안 남으면 기존 추천을 지우지 않고 `AI_PARSE_ERROR`(환불).
- 예전에 저장된 AI 값 행은 시청자 수를 숨기고 `measured=false`+추정 근거.

### 6. 참여율 벤치마크(화면·컨트롤러 없음) ✅
- 응답을 `EngagementBenchmarkResponse` 로: 내 참여율(전체·플랫폼별) **실측**, 수집 안 하는 플랫폼은 null. AI 의 내 참여율·업계 평균·백분위·등급은 버림. 프롬프트에서 업계 비교 삭제.

### 7. 수익 예측 ✅
- 쓰이지 않던 `REVENUE_FORECAST_*` 프롬프트, `RevenueForecastResult`, `revenueforecaster/dto/RevenueForecasterDtos.kt` 삭제(다른 사용처 0 확인). `AiFeature.REVENUE_FORECAST` 는 원장·DB 호환 때문에 남김.

### 공통
- 신규 `application/ai/estimate/AiEstimate.kt`(`basis`, `estimated`), 프런트 `types/aiEstimate.ts`, `components/common/AiEstimateBadge.vue`(배지 → 누르면 근거), i18n `common.aiEstimate.*`.
- 사용자 설명서: SEO(썸네일 점수·키워드 검색량 분석 같은 **없는 기능 설명**을 실제 동작으로 수정)·콘텐츠 갭 한국어·영어.

## 변경 파일
- 백엔드: `ai/PromptTemplates.kt`(대상 함수만), `ai/result/AiStructuredResults.kt`(SEO·갭·감사·키워드 결과, 수익 예측 삭제), `ai/result/ScheduleOptimalResult.kt`, `ai/result/EngagementBenchmarkResult.kt`,
  신규 `ai/estimate/AiEstimate.kt`, `videoseo/{SeoRubric(신규),VideoSeoUseCase,dto/VideoSeoScoreResponse}.kt`, `ai/{ContentGapAnalysisUseCase,dto/ContentGapDtos}.kt`,
  `channelaudit/{ChannelAuditUseCase,ChannelAuditDtos}.kt`, `keyword/{KeywordResearchUseCase,dto/KeywordResearchResponse}.kt`,
  `scheduleoptimizer/{ScheduleOptimizerUseCase(generateOptimalSlots·toResponse),dto/ScheduleOptimizerDto}.kt`, `benchmark/{EngagementBenchmarkUseCase,EngagementBenchmarkResponse(신규)}.kt`,
  `onGo-api/.../ai/AiController.kt`(콘텐츠 갭 엔드포인트만), `onGo-api/.../ai/dto/ContentGapDto.kt`(요청만 남김), 삭제 `revenueforecaster/dto/RevenueForecasterDtos.kt`
- 프런트: `api/videoSeo.ts`, `api/scheduleOptimizer.ts`, `types/{ai,channelAudit,aiEstimate(신규)}.ts`, `components/common/AiEstimateBadge.vue`(신규),
  `views/VideosView.vue`(SEO 모달·import 한 줄만), `components/dashboard/ContentGapCard.vue`, `views/ChannelAuditView.vue`, `locales/{ko,en}/common.json`, `components/manual/manualSections.ts`

## 검증
- 백엔드 신규 시험: `SeoRubricTest` 8, `VideoSeoUseCaseTest` 4, `KeywordResearchUseCaseTest` 1, `ContentGapAnalysisUseCaseTest` 3, `ChannelAuditEstimateTest` 5, `ScheduleOptimalSlotMeasurementTest` 4, `EngagementBenchmarkMeasuredTest` 1 (+ 공용 `ai/estimate/AiChatStub.kt`)
- 회귀: `ChannelAuditVideoEngagementTest` 13, `ScheduleOptimizerUseCaseTest` 11, `BenchmarkPromptMeasurementTest` 10 통과.
  - `BenchmarkPromptMeasurementTest` 는 차감 목의 반환 타입 이름만 새 응답으로 바꿨다(단언 그대로). 반환 타입이 바뀌어 형 변환 예외가 났기 때문.
- **`:onGo-application:test` 전체 2,243개 통과, 실패 0**(246개 결과 파일, 결과 폴더 비우고 재집계 — 직전 실행이 성공해 캐시 복원).
- 프런트 신규: `AiEstimateBadge.test.ts` 3, `ChannelAuditView.estimate.test.ts` 3, `VideosView.seoCredit.test.ts` +1(서버 응답 그대로 항목·근거·배지 렌더), `ContentGapCard.credit.test.ts` +1. 기존 목 2곳을 서버 계약으로 교체.
- **프런트 전체 `vitest run` 165파일 1,375개 통과**, `vue-tsc --noEmit -p tsconfig.app.json` 통과.
- **막힌 것**: `:onGo-api:compileKotlin` 실패 — `api/analytics/AnalyticsController.kt:195 This annotation is not repeatable`(조정 세션 작업 중인 `next-actions`, 앞 엔드포인트의 `@Operation` 다음에 새 `@Operation` 이 붙은 것으로 보임). 그래서 api 모듈 시험은 돌리지 못했다. 내가 고친 api 파일은 `AiController`(콘텐츠 갭)·`ContentGapDto` 뿐이다.

## 남은 것 / 판단 필요
- 참여율 벤치마크: 백엔드 컨트롤러가 없고 프런트 `api/engagementBenchmark.ts`·`stores/engagementBenchmark.ts` 는 어느 화면도 쓰지 않는다. 그 타입은 여전히 `industryAvg`·`percentile` 같은 없는 필드를 선언한다. **기능을 살릴지(컨트롤러+화면) 지울지** 결정 필요 — 이번에는 지우지 않았다.
- 키워드 리서치·최적 시간 슬롯은 API 만 있고 화면이 없다(번역 문구만 남아 있음).
- 콘텐츠 갭 설명서의 "추천 주제를 바로 캘린더에 추가" 문장은 카드에 그런 버튼이 없어 보인다(추정, 이번 범위 밖이라 안 고침).
