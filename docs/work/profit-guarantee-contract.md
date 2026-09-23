# 무조건 수익 구조 — 작업 계약 (Claude · Codex)

작성 2026-09-24. 대표 지시: **"현재 서버와 시스템으로 무조건 수익이 나는 구조여야 한다."**
= 어떤 사용자가 어떤 경로로 무엇을 쓰든 **그 사용이 만드는 외부 원가 ≤ 그 사용이 가져오는 순매출**이
코드로 보장돼야 한다. 무료 사용자의 원가는 상한이 있어야 한다.

## 공통 금지
`설정_작업.txt` 금지 · 운영 DB/설정/비밀값 금지 · **제품의 실제 외부 API 호출 금지** · 가짜 성공 금지 ·
기존 테스트 완화·삭제 금지 · git checkout/reset/revert/stash 금지 · Gradle 동시 실행 오류는 재실행.
요금제 가격(₩9,900/19,900/49,900)과 크레딧 팩 가격은 바꾸지 않는다(단계 C 에서 Claude 가 판단).

## 단계 A — Codex: 원가 발생 지점 전수 조사 (코드 변경 없음)
보고서: `docs/work/profit-cost-audit.md`. 다음을 **빠짐없이** 표로:

1. **돈이 나가는 외부 호출 전부** — LLM(ChatClient/ChatModel), STT, TTS, 이미지 생성, 임베딩, 번역, 기타 유료 API.
   각 호출 지점마다: 파일:줄, 호출 경로(사용자 요청/스케줄러/이벤트), **어떤 AiFeature 로 크레딧을 차감하는가
   (없으면 "무과금")**, 차감이 호출 **전**인지 후인지, 실패 시 환불 여부, 입력 크기가 사용자 데이터에 비례하는지
   (댓글 목록·영상 수 등 — 상한이 있는가), `maxTokens` 설정 여부.
2. **무과금 호출** — 크레딧 없이 유료 모델을 부르는 경로(스케줄러 자동 실행, 크레딧 0 기능 SENTIMENT_ANALYSIS 등).
   각각 누가 얼마나 자주 트리거할 수 있는지.
3. **제공자 선택** — `ChatClientResolver` 기본·대체 순서, 사용자가 고를 수 있는 제공자, 각 제공자의 실제 모델명
   (`AiConfig`·application.yml; OpenAI chat 은 모델 미지정이면 Spring AI 기본값이 무엇인지 소스/의존성에서 확인).
4. **크레딧 없이 반복 가능한 무거운 서버 작업** — ffmpeg 렌더·다운로드 등 CPU·디스크를 쓰는 경로와 동시 실행 제한.
5. **무료 사용자가 만들 수 있는 월 최대 원가 요소** — 무료 크레딧, 저장공간, 무과금 호출, 렌더.

## 단계 B — Claude: 단가표와 보장 장치 설계·구현
Codex 보고서를 받은 뒤 Claude 가 정한다. 파일 소유는 단계 B 착수 시 이 문서에 추가한다.

## 단계 B — 설계 확정 (Claude, 2026-09-24)

기준값은 `AiUnitEconomics`(application/ai/economics) 한 곳이다. 크레딧 1개 원가 예산 = 최저 순매출 ₩4.40 × 0.5 = **₩2.20**.
모든 채팅 호출은 `AiSpendContext` 예산 안에서만 실행된다 — 예산이 없으면 가로채기(`AiCostGuardAdvisor`)가 거부한다.
`CreditService.withCredits` 는 이미 예산을 연다(Claude 가 적용). **예산을 열지 않은 채팅 호출은 이제 실패한다.**

### B1 — Claude 소유
- `application/ai/economics/*` (AiUnitEconomics, AiPriceBook, AiSpendContext), `CreditService.withCredits`
- `infrastructure/ai/AiConfig.kt`, 신규 `infrastructure/ai/AiCostGuardAdvisor.kt` (모든 ChatClient 에 부착, 생각 모드 끄기)
- `ChatClientResolver.kt`, 설정의 AI 제공자 선택(SettingsUseCase·SettingsView) — 저가 제공자(QWEN·KIMI·GLM·MINIMAX)만
- `AiUnitEconomicsTest`, 매뉴얼

### B2 — Codex 소유 (C6)
1. **예산 열기** — `withCredits` 를 쓰지 않고 크레딧을 받는 모든 채팅 호출 경로에서, 받은 크레딧만큼
   `AiSpendContext.withBudget(unitEconomics.budgetKrw(credits), label) { ... }` 로 감싼다:
   AI 파이프라인 작업자(`executeInternal` 단계별), 쇼츠 단계 실행기(단계 크레딧), 번역(언어별 크레딧),
   AI 배치 처리(항목별), 그 밖에 `validateAndDeduct` 후 채팅을 부르는 곳 전부(grep 으로 찾아 보고서에 표).
2. **감정 분석** — 무료 요금제는 실행하지 않는다. 배치당 댓글 `SENTIMENT_MAX_COMMENTS_PER_BATCH`(25 — 50 은 ₩10 예산을 입력만으로 다 쓴다),
   사용자당 하루 `SENTIMENT_BATCHES_PER_DAY`(8) 배치 상한, 배치마다 `SENTIMENT_BATCH_BUDGET_KRW` 예산.
   상한을 넘은 댓글은 분석하지 않고 남긴다(가짜 감정값 금지 — 미분석으로 둔다).
3. **TTS** — 무과금이다. 새 `AiFeature`(예: VIDEO_TTS)로 `ceil(글자수/1000) × unitEconomics.ttsCreditsPer1000Chars()`
   크레딧을 TTS 호출 **전에** `withCredits` 로 받는다. 부족하면 호출하지 않는다.
4. **STT 길이 비례 과금** — 직접 `SttUseCase.execute` 는 영상 길이와 무관하게 10 크레딧이다(3시간도 10).
   쇼츠처럼 길이를 재서 `ceil(분/10) × unitEconomics.sttCreditsPer10Minutes(설정 모델)` 로 받는다. 쇼츠
   `ShortsPipelineCreditRequirements` 의 TRANSCRIBE 도 같은 함수로. 화면의 크레딧 추정(`useShortsCreditEstimate` 등)이
   서버 값과 같게 — 프런트가 상수를 따로 들고 있으면 서버가 준 값을 쓰게 바꾼다.
5. 각 항목 테스트 + 변이 검증. 보고서 `docs/work/profit-leaks-codex-report.md`.

### B2 추가 (C6-6) — 쇼츠 LLM 단계 크레딧을 입력 상한에서 계산
가로채기가 켜지면 입력이 큰 단계는 고정 크레딧 예산으로 거부된다. `ShortsPipelineCreditRequirements` 에서:
- **SEGMENT** — 전사문 전체(상한 없음) + 타임코드 8,000자. `max(현재 8, unitEconomics.creditsForLlmCall(
  transcriptCharsFor(분) + 8,000 + 프롬프트 여유 1,500))`. 실행 전 크레딧 판정(`SHORTS_INSUFFICIENT_CREDIT_FOR_RUN`)과
  실제 차감·환불·`RunStage.creditCost` 가 같은 값을 써야 한다(`creditCostOf` 한 곳).
- **SUBTITLE** — 입력 8,000자 상한 + 출력 최대. `max(현재 5, creditsForLlmCall(8,000 + 1,500))`.
- 나머지 단계(HOOK·TEMPLATE·VALIDATE·REFRAME)는 입력 상한이 작아 현재 값이 예산 안이다 — 테스트로 고정.
- 화면 추정(`useShortsCreditEstimate`)이 서버 값을 쓰게. 단위경제 문서(shorts-unit-economics.md) 숫자 갱신은 Claude 가 한다.
