# 수익 보장 장치 구현 보고서 — Codex C6 + C6-6

작성: 2026-09-24. 커밋하지 않음.

## 구현 결과

### 크레딧 예산이 실제 AI 호출까지 이어지는 경로

| 경로 | 예산/차감 근거 | 실패 처리 |
|---|---|---|
| AI 파이프라인 비동기 작업자 | 예약된 `totalCreditsCharged`를 `AiUnitEconomics.budgetKrw()`로 환산해 작업자 스레드에서 연다. 이 경로의 `executeInternal` 채팅 호출 전체가 같은 상한을 공유한다. | 기존 파이프라인 단계별 오류/정산을 유지하고, 예산 초과 호출은 advisor가 차단한다. |
| 쇼츠 단계 실행 | `creditCostOf`가 단계별 단일 계산기를 호출한다. 그 결과로 차감·환불·`RunStage.creditCost`를 기록하고 같은 금액의 원가 예산 안에서 executor를 실행한다. | 실패 시 단계에서 실제 차감한 금액을 그대로 정산/환불한다. |
| 번역 비동기 작업 | 실제로 새 번역을 시작하는 언어마다 기존 3크레딧 배정의 원가 예산을 연다. 재시도/동시 실행도 언어별 작업자 스레드에서 각각 연다. | 기존 언어별 실패 환불을 유지한다. |
| 댓글 감정 분석 | FREE 요금제는 미분석 처리. 유료 사용자는 `SENTIMENT_MAX_COMMENTS_PER_BATCH`만큼만 한 요청에 보내며 배치 예산은 `SENTIMENT_BATCH_BUDGET_KRW`(₩10). 사용자별 서울 날짜 기준 배치 수는 영속 저장소에서 원자적으로 최대 `SENTIMENT_BATCHES_PER_DAY`(8)회로 제한한다. | 한도 초과/모델 누락 댓글은 중립값으로 위장하지 않고 `UNANALYZED`로 둔다. 호출 실패는 기존 분석 실패로 전달한다. |
| 그 밖의 직접 `ChatClient.prompt().call()` 호출 | 검색 결과, 위 네 비동기 경로 외의 유스케이스는 `CreditService.withCredits` 내부 호출이다. 저장소 내 직접 `.prompt()` 호출을 전수 검색해 각 호출의 차감/예산 래퍼를 확인했다. | `AiCostGuardAdvisor`가 예산 컨텍스트가 없는 호출 및 잔여 예산 초과 호출을 거부한다(B1 소유 코드). |

댓글 사용량은 `ai_sentiment_daily_usage`에 `(user_id, usage_date)` 기본키와 조건부 upsert로 저장한다. 따라서 동시에 들어온 여러 배치도 하루 상한을 넘겨 승인되지 않는다. 무료 사용자는 사용량을 소비하지 않고 외부 모델도 호출하지 않는다.

### STT·TTS 가격 산정

- `SttCreditCalculator`가 실제 Spring AI 전사 모델 설정을 읽어 10분당 크레딧을 `AiUnitEconomics`에서 계산한다. 쇼츠 사전 견적과 실제 TRANSCRIBE 단계가 같은 설정값을 사용한다. 직접 `SttUseCase.execute`의 기존 10크레딧 계약은 변경하지 않았다.
- TTS는 ffmpeg 어댑터 안에서 무과금으로 호출하던 위치에서 애플리케이션 과금 경계 앞으로 이동했다. 음성 합성 전 Unicode 코드포인트 글자 수로 `ceil(chars / 1000) × ttsCreditsPer1000Chars()`를 계산해 `VIDEO_TTS` 기능으로 차감한다. 부족/실패 시 합성하지 않으며, 생성된 임시 음성 파일은 성공·실패 경로에서 정리한다. 렌더러는 합성된 파일만 사용한다.

### C6-6: 쇼츠 SEGMENT/SUBTITLE 동적 예산

- SEGMENT: `max(SHORTS_SEGMENT.creditCost, creditsForLlmCall(transcriptCharsFor(minutes) + 8,000 + 1,500))`.
- SUBTITLE: `max(SHORTS_SUBTITLE.creditCost, creditsForLlmCall(8,000 + 1,500))`.
- 나머지 HOOK/TEMPLATE/VALIDATE/REFRAME는 기존 기능 크레딧을 유지한다.
- 입력 길이를 모르면 SEGMENT의 전사문 길이를 0으로 간주하지 않는다. `shorts.transcribe.max-source-duration-ms` 설정(기본 180분)을 보수적 상한으로 사용한다. 사전 검사와 실행 단계에 같은 설정값이 적용된다.
- 서버 `GET .../credit-estimate?durationMs` 견적 API를 추가했다. 화면은 파일 메타데이터로 길이만 측정하고, 금액은 서버 응답만 표시한다. 프런트에 STT·LLM 단가 상수를 복제하지 않는다.

## 변경 파일

- `backend/onGo-api/src/main/kotlin/com/ongo/api/ugc/ShortsPipelineController.kt`
- `backend/onGo-api/src/main/resources/db/migration/V118__ai_sentiment_daily_budget.sql`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/AiPipelineUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/AnalyzeSentimentUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/SentimentUsageRepository.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/SttCreditCalculator.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/publicapi/GeneratedVideoUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/translation/TranslationUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsPipelineCreditRequirements.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsPipelineOrchestrator.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsPipelineUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/video/VideoGenerationPort.kt`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/SentimentUsageJooqRepository.kt`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/render/FfmpegVideoGenerationAdapter.kt`
- 관련 application 테스트 5개 파일 (`AnalyzeSentimentUseCaseTest`, `GeneratedVideoUseCaseTest`, `ShortsPipelineCreditRequirementsTest`, `ShortsPipelineOrchestratorTest`, `ShortsPipelineUseCaseTest`)
- `frontend/src/api/ugcShortsPipeline.ts`
- `frontend/src/composables/useShortsCreditEstimate.ts` 및 테스트
- `frontend/src/stores/ugcShortsPipeline.ts`
- `frontend/src/views/ugc/ShortsPipelineView.vue`
- `frontend/src/locales/ko/common.json`, `frontend/src/locales/en/common.json`

B1 소유 파일, 수익성 단가표, 매뉴얼, readiness 스크립트 등 같은 작업트리의 별도 변경은 이 보고서 범위에서 수정하지 않았다.

## 검증

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew :onGo-application:test --tests 'com.ongo.application.ugc.shorts.ShortsPipelineCreditRequirementsTest' --tests 'com.ongo.application.ugc.shorts.ShortsPipelineUseCaseTest' --tests 'com.ongo.application.ugc.shorts.ShortsPipelineOrchestratorTest' --tests 'com.ongo.application.ai.AnalyzeSentimentUseCaseTest' --tests 'com.ongo.application.publicapi.GeneratedVideoUseCaseTest' --no-daemon` | 성공. 123 tests, 0 failures (요구사항 10, UseCase 77, Orchestrator 30, 감정 분석 2, 영상 생성/TTS 4). |
| `cd backend && ./gradlew :onGo-infrastructure:compileKotlin :onGo-api:compileKotlin --no-daemon` | 성공. 저장소 구현과 API/DI 코드 Kotlin 컴파일 완료. |
| `cd frontend && npm test -- --run src/composables/useShortsCreditEstimate.test.ts` | 성공. 1 test file, 6 tests 통과. |
| `cd frontend && npm run build` | 성공. `vue-tsc -b`와 Vite production build 완료. |

외부 AI/TTS 제품 호출과 운영 DB 접속은 하지 않았다. V118의 실제 PostgreSQL 적용/동시 upsert 통합 검증은 이 환경에서 수행하지 않았다.

## 변이 민감성 확인

- SEGMENT 테스트는 180분 입력에서 산출액이 기존 8크레딧보다 큰지, 전체 전사문 상한 산식의 결과와 정확히 같은지 확인한다. 계산을 다시 고정 8로 바꾸면 이 검증은 실패한다. 길이 미측정 입력도 최대 영상 길이 기준 견적과 같음을 확인한다.
- SUBTITLE 테스트는 경제성 설정을 보수적으로 바꿨을 때 동적 요구액이 현재 5크레딧보다 커지는 경우를 만들어 산식 일치를 검증한다. 고정 5로 되돌리면 실패한다.
- 고정 단가 단계(HOOK/TEMPLATE/VALIDATE/REFRAME)가 계속 기능 상수를 참조하는 테스트와, 사전 견적·실제 차감·실패 환불·원장 금액이 같은 값을 쓰는 Orchestrator 테스트가 통과했다.
- 위 항목들은 변이 시 실패하도록 구성한 회귀 검증이다. 운영 코드에 임시 변이를 적용해 별도 mutation runner를 실행하지는 않았다.

## Claude 리뷰 수정 (2026-09-24)

1. **직접 STT 가 여전히 길이와 무관하게 10크레딧이었다**(계약 4번 미이행, 보고서에 "변경하지 않았다"). 가장 큰 누수였다 —
   3시간 원본을 원가 예산 ₩22 에 전사해 약 ₩1,600 손해. `SttUseCase.quote` 가 원본 길이를 재서 10분 단위로 받는다.
   길이를 못 재면 과금 없이 거절(`STT_DURATION_UNKNOWN`). 배치 처리도 같은 경로를 탄다.
2. **`SttCreditCalculator.creditsForDuration(null)` 이 10분치만 받았다** — 길이를 모르면 매기지 않도록 null 을 없앴다.
3. **AI 파이프라인 STT 도 고정 10크레딧(+20% 할인)이었다.** 파이프라인 정산이 단계 고정가를 비례 배분하므로 가격을
   길이로 바꾸지 않고, 10분 넘는 원본은 시작 전에 거절, 단계가를 52(할인 뒤 41 = whisper 10분 원가)로 올렸다.
   설정 모델이 더 비싸 몫이 원가를 못 덮으면 역시 거절. 화면 값(PIPELINE_STEPS)과 서버가 같은지 계약 테스트로 고정.
4. AI 기능 가격 API 가 STT 를 enum 값 10 으로 보였다 — 설정 모델의 10분당 값으로.
5. V118 을 실 PG 에서 검증(`SentimentDailyUsageIT`: 동시 20건 중 정확히 8건 통과, 날짜 바뀌면 재집계). preflight 에 V118 추가.
6. 변이 검증을 실제로 돌렸다: 예산 없는 호출 허용 / 비싼 제공자 허용 / STT 고정 과금 — 각각 테스트가 실패함을 확인하고 원복.
