# 단계 A — 원가 발생 지점 전수 조사

조사일: 2026-09-24. 저장소 소스만 읽었으며 제품 외부 API 호출, 운영 설정/DB 조회, Gradle 실행은 하지 않았다. “확인”은 코드에서 직접 확인한 사실, “미확인/추정”은 실제 계약·청구서·운영 계측 없이는 단정할 수 없는 비용이다. `설정_작업.txt`는 열지 않았다. 코드 수정 및 커밋 없음.

검색 범위/재현:

```bash
rg -n 'ChatClient|ChatModel|\.call\(|\.prompt\(|TranscriptionModel|TextToSpeech|ImageModel|EmbeddingModel' backend --glob '*.kt' --glob '*.java' --glob '*.yml' --glob '*.yaml' --glob '*.properties'
rg -n 'RestClient|WebClient|RestTemplate|HttpClient|\.post\(\)|\.get\(\)|\.send\(|\.synthesize\(|transcriptionModel\.call' backend/onGo-application/src/main/kotlin backend/onGo-infrastructure/src/main/kotlin backend/onGo-api/src/main/kotlin --glob '*.kt'
```

아래는 운영 소스의 실제 호출점이며 테스트의 mock/stub 호출은 제외했다. Spring AI `ChatClient`의 `.call()`이 공급자 요청을 발생시키는 지점이다. 동일한 유스케이스의 `executeInternal` 경로도 별도로 표시했다. 이 내부 함수는 자체 차감하지 않으며 현재 호출자 파이프라인이 먼저 총액을 예약한다.

## 1. LLM·STT·TTS 실제 호출 지점

| 기능 / 확인된 호출 위치 | 호출 경로·입력량/상한 | 크레딧 및 순서 | 실패 환불 | 출력 상한 |
|---|---|---|---|---|
| 메타 생성 `GenerateMetaUseCase.kt:27` (`executeInternal`), `:53` (`execute`) | 사용자 API; 내부 버전은 AI pipeline. 입력 script, platforms, tone, category. 명시적 문자열 길이 상한은 이 함수에 없음(HTTP/request validation은 별도 확인 필요). | 공개 실행은 `META_GENERATION` 5, `withCredits`가 호출 전에 차감. 내부 실행은 pipeline 선차감에 포함. | `withCredits` 블록 예외면 환불. 내부는 pipeline 정산 규칙. | 호출별 `maxTokens` 없음. provider 기본 설정 적용. |
| 해시태그 `GenerateHashtagsUseCase.kt:26`, `:51` | 사용자 API / pipeline 내부. title/category/platforms 입력, 명시 길이 상한은 함수에 없음. | `HASHTAG_RECOMMENDATION` 3 선차감. 내부는 pipeline 총액에 포함. | 예외면 환불/파이프라인 미사용분 정산. | per-call maxTokens 없음. |
| 스크립트 분석 `AnalyzeScriptUseCase.kt:24`, `:50` | 사용자 API / pipeline 내부. 사용자 script 전체, 명시 길이 상한 없음. | `SCRIPT_ANALYSIS` 5 선차감. 내부는 pipeline 총액에 포함. | 동일. | per-call maxTokens 없음. |
| STT `SttUseCase.kt:152` | 사용자 요청 또는 쇼츠/AI pipeline worker. 원본에서 추출한 오디오 청크를 순차 호출. 파트 수는 영상 길이에 비례하며 서비스 코드의 총 길이 상한은 확인되지 않음. | 직접 `execute`: `STT` 10을 모델 호출보다 먼저 차감. Shorts는 `TRANSCRIBE` stage 시작 전에 길이 기반 비용을 차감. AI pipeline은 선택한 단계 총액을 worker 시작 전 예약. `executeInternal` 자체는 무차감(선예약 전제). | 직접 경로는 `withCredits`가 모든 Throwable을 환불. Pipeline/Shorts는 실패·미사용분 정산 및 recovery. 이미 수행된 공급자 청구 자체를 돌려받는 것은 아님. | OpenAI transcription 옵션에는 모델·언어·응답형식, per-call maxTokens 없음. 모델명 설정 참조 `spring.ai.openai.audio.transcription.options.model`, 기본 `whisper-1`. |
| 댓글 답변 `GenerateReplyUseCase.kt:38` | 사용자 단건 요청. comment/context 문자열; 명시적 입력 최대 길이는 이 함수에서 확인 안 됨. | `COMMENT_REPLY` 2 선차감. | 예외/파싱 실패 시 `withCredits` 환불. | per-call maxTokens 없음. |
| 댓글 일괄 초안 `GenerateReplyUseCase.kt:80` | 사용자 batch API. 실제 조회된 comments 전체를 한 prompt로 구성. `commentIds` 개수 상한을 이 UseCase에서 확인하지 못함. | `BATCH_REPLY_DRAFT` 3 × 실제 댓글 수를 한 번에 선차감. 크레딧이 prompt 크기에 비례하는 설계. | 예외 시 전체 allocation 환불. | per-call maxTokens 없음. 반환 draft 수를 별도로 제한하는 공급자 옵션 없음. |
| 감정 분석 `AnalyzeSentimentUseCase.kt:35` | 댓글 동기화의 신규 댓글 batch. 각 댓글은 200자까지만 보내지만 리스트 길이 상한은 여기 없음. `CommentSyncScheduler`가 5분마다 동기화를 시도하며 채널/신규 댓글 수에 따라 반복 가능. | `SENTIMENT_ANALYSIS` **0 크레딧**. 별도 호출 직전 credit check/deduction 없음. rate limiter만 확인. 무과금 유료 LLM 경로. | 환불 불필요(차감 없음); 실패는 오류로 반환되고 댓글 sync 쪽에서 처리. 공급자 비용이 이미 나갔다면 보상 없음. | per-call maxTokens 없음. |
| FAQ 클러스터링 `FaqClusteringUseCase.kt:56` | 사용자 기능; FAQ/문답 목록 크기가 입력. 명시 상한 미확인. | `FAQ_CLUSTERING` 5 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 경쟁자 인사이트 `CompetitorInsightUseCase.kt:128` | 사용자 요청. 경쟁 채널/영상 수가 입력; 파일의 `take`/요청 제한 및 수집 상한 추가 확인 필요. | `COMPETITOR_INSIGHT` 8 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 트렌드 분석 `TrendUseCase.kt:64` | 사용자 요청, 오늘 트렌드 최대 20개를 prompt에 포함. | `TREND_ANALYSIS` 이름으로 고정 비용(소스 상수 `ANALYSIS_CREDIT_COST`; AiFeature enum 항목은 아님) 선차감. | `withCredits` 예외 환불. | per-call maxTokens 없음. |
| 콘텐츠 갭 `ContentGapAnalysisUseCase.kt:55` | 사용자 요청, 영상 목록에서 최대 20개 사용. 각 영상 메타데이터 크기 상한 별도 확인 필요. | `CONTENT_GAP_ANALYSIS` 10 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 스케줄 추천 `SuggestScheduleUseCase.kt:45` (`executeInternal`), `:85` (`execute`) | 사용자 API / AI pipeline 내부. 내부 heatmap 집계 결과를 prompt에 넣음; 행 크기 제한은 이 함수에서 확인되지 않음. | `SCHEDULE_SUGGESTION` 3 선차감. 내부는 pipeline 총액에 포함. | 사용자 경로 `withCredits` 환불; pipeline은 미실행분 정산. | per-call maxTokens 없음. |
| SEO 점수 `VideoSeoUseCase.kt:62` | 사용자 요청. video title/description/tags 등 사용자 메타데이터; 길이 상한은 함수 밖 validation 여부 추가 확인 필요. | `VIDEO_SEO_SCORE` 2 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 성과 리포트 `GenerateReportUseCase.kt:58` | 사용자 요청; 조회 기간/성과 데이터에 비례. 저장소 조회 기간·행 수의 최종 상한 추가 확인 필요. | `PERFORMANCE_REPORT` 8 선차감. | 예외/파싱 실패 시 환불. | per-call maxTokens 없음. |
| 수익 리포트 `GenerateRevenueReportUseCase.kt:72` | 사용자 요청; 기간별 수익/채널 자료. 요청 기간 상한 여부 추가 확인 필요. | `REVENUE_REPORT` 8 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 주간 다이제스트 `WeeklyDigestUseCase.kt:101` | **스케줄러 자동 실행**: `WeeklyDigestScheduler.kt:24` 매주 월요일 09:00, Pro/Business 활성 구독자별 1회 시도. 사용자별 통계 입력량은 데이터에 비례. | `WEEKLY_DIGEST` 8 선차감. 크레딧 부족/rate limit이면 호출 전 skip. 자동 실행이지만 무료 플랜 대상은 아님. | 모델/저장 오류면 `withCredits` 환불. 공급자 청구는 환불되지 않음. | per-call maxTokens 없음. |
| 메타 리라이트 `MetaRewriteUseCase.kt:119` | 사용자 요청. 원래 title/description/tags, 플랫폼별 prompt; 입력 길이 상한은 호출 지점에서 확인되지 않음. | `META_REWRITE` 3 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 전략 코치 `StrategyCoachUseCase.kt:68` | 사용자 요청; 최근 영상은 최대 15개. | `STRATEGY_COACH` 10 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 스케줄 옵티마이저 `ScheduleOptimizerUseCase.kt:127` | 사용자 요청; 저장된 schedule/channel 데이터에 의존. 입력 상한 추가 확인 필요. | `SCHEDULE_SUGGESTION` 3 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 채널 오디트 `ChannelAuditUseCase.kt:111` | 사용자 요청; 채널 analytics/영상 개수에 의존. 조회 범위 상한은 유스케이스·repository 추가 확인 필요. | `CHANNEL_AUDIT` 15 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 키워드 리서치 `KeywordResearchUseCase.kt:42` | 사용자 요청; keyword와 수집된 결과량. | `KEYWORD_RESEARCH` 3 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 수익 인사이트 `RevenueInsightUseCase.kt:85` | 사용자 요청; revenue data 입력 크기에 의존, 기간 상한 추가 확인 필요. | `REVENUE_INSIGHT` 5 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 리퍼포즈 `RepurposeUseCase.kt:78` | 사용자 요청; 원본 콘텐츠 길이/선택 플랫폼에 의존. | `CONTENT_REPURPOSE` 10 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 참여도 벤치마크 `EngagementBenchmarkUseCase.kt:154` | 사용자 요청; 비교 영상/성과 수에 의존. | `ENGAGEMENT_BENCHMARK` 3 선차감. | 예외 시 환불. | per-call maxTokens 없음. |
| 번역 `TranslationUseCase.kt:288` | 사용자 요청/번역 worker·retry. 원문 길이 × target languages. | `validateAndDeduct(userId, totalCost, "TRANSLATION")` (`:153`)에서 번역 실행 전에 예상 총액 차감. `perLanguage` allocation으로 언어별 비용 기록. | 실패 언어는 allocation 기반 부분 환불; 전체 실패/복구는 translation recovery (`:384` 이하). 공급자 과금과 앱 환불은 별개. | per-call maxTokens 없음. 텍스트/언어 개수 상한은 추가 확인 필요. |
| 쇼츠 REFRAME `ReframeStageExecutor.kt:39` | 사용자가 시작한 Shorts pipeline worker. | `SHORTS_REFRAME` 3를 stage 시작·영속 행 저장 전에 차감 (`ShortsPipelineOrchestrator.kt:212`, stage map). | 해당 stage 실패 시 allocation 기준 해당 단계분 환불; 성공 단계 비용 유지. | per-call maxTokens 없음. |
| 쇼츠 SEGMENT `SegmentStageExecutor.kt:46` | Shorts pipeline worker. | `SHORTS_SEGMENT` 8 stage 선차감. | 실패 단계만 정산/환불. | per-call maxTokens 없음. |
| 쇼츠 SUBTITLE `SubtitleStageExecutor.kt:50` | Shorts pipeline worker. | `SHORTS_SUBTITLE` 5 stage 선차감. | 실패 단계만 정산/환불. | per-call maxTokens 없음. |
| 쇼츠 HOOK `HookStageExecutor.kt:45` | Shorts pipeline worker. | `SHORTS_HOOK` 5 stage 선차감. | 실패 단계만 정산/환불. | per-call maxTokens 없음. |
| 쇼츠 TEMPLATE `TemplateStageExecutor.kt:46` | Shorts pipeline worker. | `SHORTS_TEMPLATE` 3 stage 선차감. | 실패 단계만 정산/환불. | per-call maxTokens 없음. |
| 쇼츠 VALIDATE `ValidateStageExecutor.kt:113` | Shorts pipeline worker. | `SHORTS_VALIDATE` 3 stage 선차감. | 실패 단계만 정산/환불. | per-call maxTokens 없음. |
| Pipeline 내부의 STT | `TranscribeStageExecutor.kt:24` → `SttUseCase.executeInternal` → `SttUseCase.kt:152`. 사용자 시작 Shorts workflow. | `STT`는 입력 길이 기반 금액을 TRANSCRIBE stage에서 선차감; `executeInternal`는 중복 차감하지 않음. | stage 단위 실패 정산. | per-call maxTokens 없음. |
| 선택형 텍스트-슬라이드 영상 TTS `OpenAiTextToSpeechAdapter.kt:46` | Public API `GeneratedVideoUseCase.kt:69` → FFmpeg adapter `FfmpegVideoGenerationAdapter.kt:41`. 사용자가 voice를 넣을 때 1회. 텍스트 최대 4,000자, 결과 오디오 전체를 byte array로 받음. | **무과금**: 어떠한 `AiFeature` 차감도 없음. API key/base URL/model 설정 시 OpenAI-compatible `/audio/speech` 호출. 단일 요청 길이는 제한하지만 사용자가 반복 호출 가능. | 실패를 크레딧으로 보상할 것이 없음. 나중 단계 storage/render 실패 시 이미 나간 TTS 비용은 환불 불가. | TTS 요청에 maxTokens 없음; 텍스트 글자수만 제한. 모델 기본 `gpt-4o-mini-tts`, base URL configurable. |

일반 LLM 호출 중 사용자 호출은 대체로 요청 전에 `CreditService.withCredits`로 차감하고 블록 성공 시 확정, 예외 시 앱 크레딧 환불한다. `CreditService.kt:73-98`은 차감 커밋 → 외부 호출 블록 → 실패 시 allocation 환불 순서다. 이 환불은 외부 공급자 비용의 환불이 아니다. Pipeline 전체는 `AiPipelineUseCase.kt:109-148`에서 실행 전에 총액 차감 후 job을 저장하고, 실패/취소/재시작 정산에서 미사용분을 돌려준다. Shorts는 stage 시작 전에 stage 행과 함께 차감한다.

## 2. 무과금 또는 원가 보장 미확인 호출

| 경로 | 근거 | 사용자 트리거/빈도 및 노출 |
|---|---|---|
| 댓글 감정 분석 | `SENTIMENT_ANALYSIS` 비용 0 (`AiFeature.kt`), `AnalyzeSentimentUseCase.kt:16-43`에 CreditService 없음. | `CommentSyncScheduler.kt` 매 5분 주기에서 연결된 채널 sync; 새 댓글 수만큼 prompt가 커질 수 있음. 댓글 한 건당 200자 truncate은 확인했으나 batch 상한은 미확인. scheduler 주기가 고정이어도 사용자 연결 채널·댓글 수에 따라 총 비용 상한이 코드상 보이지 않음. |
| TTS 영상 생성 | 위 `OpenAiTextToSpeechAdapter` 호출, credit service 연결 없음. | Public API에 노출. 4,000자 제한/렌더 180초 제한은 확인. 요청 호출 빈도 제한·월 quota·사용자 비용 차감은 해당 흐름에서 확인하지 못함. |
| Pipeline 내부 `executeInternal` | 해당 함수 자체는 크레딧을 쓰지 않음. 현재 `AiPipelineUseCase`가 각 단계 비용을 사전 합산·차감한 뒤 호출. | 현재 직접 호출자 검색상 AI pipeline worker만 확인됨. 경로가 추가되면 무료 모델 호출이 되므로 내부 함수 접근/호출자의 선차감 보장이 핵심. |
| 수동 게시/외부 API | 주요 플랫폼 호출은 사용자가 연결 계정으로 직접 게시/통계 조회; `AiFeature` 크레딧 없음. 공급자 API의 사용량 과금은 코드상 확인되지 않으나 quota·계정요금/대역폭 비용 여부는 외부 약관/운영계약 확인 필요. |

`AiFeature.SENTIMENT_ANALYSIS` 외에 확인한 모델 호출 중 직접적인 무차감은 TTS와 pipeline 내부 사전예약 경로뿐이다. `ImageModel`/`EmbeddingModel` 검색은 실행 가능한 운영 코드에서 발견되지 않았다. `TranslationUseCase`는 AI ChatClient 호출로 구현되어 있고 별도 번역 SaaS 호출은 발견되지 않았다.

## 3. Chat provider 선택·실 모델 설정

| 항목 | 코드에서 확인한 사실 |
|---|---|
| 사용자 선택 | `ChatClientResolver.kt:20-32`: userId별 `UserSettings.defaultAiProvider`를 읽고 사용할 수 없으면 fallback. `SettingsUseCase`는 default provider 선택을 저장한다. API가 노출하는 사용자 선택은 AiProvider enum 기준. |
| 기본/대체 순서 | 선택 provider가 사용 가능하면 그대로 사용. 아니면 `CLAUDE → OPENAI → GEMINI → QWEN → KIMI → GLM → MINIMAX` (`ChatClientResolver.kt:38-57`). 실제 사용 가능 여부는 Registry가 credentials를 검사. Gemini bean은 `spring.ai.google.genai.enabled=true`일 때만 존재. |
| 기본 사용자 설정 | 새 설정 기본값은 `SettingsUseCase.kt`/`UserSettings` 확인상 QWEN. 단, 선택 불가하면 resolver fallback. |
| Claude | `AiConfig.kt:20-22`의 Anthropic ChatModel. `application.yml:67`: `claude-sonnet-4-5-20250514`, max-tokens 4096. |
| OpenAI Chat | `AiConfig.kt:24-26`; application 설정의 OpenAI chat model 문자열은 검색된 `application.yml` 구간에 없음. **정확한 실제 모델명 미확인.** 기본으로 모델을 지정하지 않았을 때의 Spring AI fallback도 코드 소스 내에서 확인하지 못함. 의존성 jar/공식 문서 확인이 필요하며 임의 추정하지 않음. |
| Gemini | `AiConfig.kt:28-31`; `application.yml:81`: `gemini-2.5-pro`, temperature 0.7. max output token은 해당 config에 명시되지 않음. |
| Qwen/Kimi/GLM/MiniMax | `AiConfig.kt:34-91`, 모두 DashScope OpenAI-compatible `OpenAiChatModel`; 실제 모델명 각각 `qwen3.5-plus`, `kimi-k2.5`, `glm-5`, `minimax-m2.5`. 공유 builder에서 `maxTokens(4096)`. |
| 요청별 token limit | 개별 ChatClient `.prompt().call()` 경로에서 `maxTokens` 옵션 호출은 확인하지 못했다. Claude/DashScope 구성값 외에는 공급자 기본값/응답 상한에 의존. prompt 입력 상한이 불명확한 여러 기능과 합쳐져 비용 상한이 코드에서 일정하지 않음. |
| STT/TTS 공급자 | STT는 고정 `OpenAiAudioTranscriptionModel`, 모델명 설정 `spring.ai.openai.audio.transcription.options.model` 기본 whisper-1. TTS는 OpenAI-compatible configurable base URL/model, 기본 `https://api.openai.com/v1` + `gpt-4o-mini-tts`. TTS는 ChatClientResolver/provider 선택을 따르지 않음. |

## 4. 기타 외부 API 및 결제·저장 비용 경로

이 구간은 호출 자체와 원가가 다르므로 API별로 구분한다. 소셜 API는 계정 소유자 토큰으로 게시/조회하는 OAuth integration이며, 앱 코드가 공급자의 별도 호출 요금을 지급한다고 확인할 수는 없었다. 다만 quota, 유료 계정, API 변경, 외부 대역폭은 별도 계약 확인 대상이다.

| 외부 시스템 / 소스 위치 | 트리거 및 사용자 데이터 규모 | `AiFeature` / 비용 판단 |
|---|---|---|
| YouTube Data/Analytics/OAuth `infrastructure/external/youtube/YouTubeClient.kt`, `YouTubeConfig.kt` | OAuth 연결, 영상/채널 조회·업로드·댓글·analytics; 사용자가 연결/게시, 스케줄된 채널 sync도 호출. 영상 수/동기화 범위에 비례. | 크레딧 없음. 무료 quota인지 유료 비용인지 코드에서 보장되지 않음(YouTube API quota/계정 조건 확인 필요). |
| TikTok `external/tiktok/TikTokClient.kt` | OAuth, posting, comments/metrics; 요청/예약 게시가 트리거. | 크레딧 없음. 외부 요금 확인 안 됨; quota/개발자 계정 제한. |
| Instagram `external/instagram/InstagramClient.kt` | OAuth, media publishing, comments/insights; user/scheduled publishing. | 크레딧 없음; 공급자 과금 확인 안 됨. |
| Threads `external/threads/ThreadsClient.kt`; Facebook `external/facebook/FacebookClient.kt` | OAuth/publishing/metrics; user/scheduler. | 크레딧 없음; 계정 API 정책·quota 확인 필요. |
| Pinterest `external/pinterest/PinterestClient.kt`; LinkedIn `external/linkedin/LinkedInClient.kt`; Tumblr `external/tumblr/TumblrClient.kt`; Vimeo `external/vimeo/VimeoClient.kt`; WordPress `external/wordpress/WordPressClient.kt`; Dailymotion `external/dailymotion/DailymotionClient.kt`; Twitter `external/twitter/TwitterClient.kt`; Naver Clip `external/naverclip/NaverClipClient.kt` | 각각 사용자 연결 후 콘텐츠 게시·조회; publish dispatcher는 예약된 요청을 반복 재시도할 수 있음. 파일 upload의 네트워크 사용량은 파일 크기에 비례. | 크레딧 없음. 소스상 usage-billing API 없음. 공급자 비용/요금제/쿼터 및 재시도 횟수는 코드 밖 조건 확인 필요. |
| Google Drive `external/googledrive/GoogleDriveOAuthClient.kt`; Google/Kakao OAuth `infrastructure/security/oauth/{Google,Kakao}OAuth2Service.kt` | 사용자 OAuth 로그인·연결과 token/profile API. 사용자 로그인/연결 때 호출. | 크레딧 없음. OAuth/API 사용료·제한은 코드로 확인 불가; 호출 본문상 업로드 AI 원가 경로는 아님. |
| Google Trends `external/trends/GoogleTrendsService.kt` | 사용자 검색 및 매일 `TrendSyncScheduler` 수집. | 크레딧 없음. 구현/데이터 제공 조건에 비용이 명시돼 있는지는 외부 계약 확인 필요. |
| OpenAI-compatible TTS | 위 표의 `OpenAiTextToSpeechAdapter.kt:46`; 사용자 public video generation에서 선택적으로 호출. | 크레딧 없음; 명시적 잠재 usage charge. 가장 뚜렷한 매출-원가 누수. |
| S3/MinIO `external/storage/S3StorageClient.kt`, `MinioStorageClient.kt` | 영상 원본/생성물 업로드·다운로드·보존·삭제. 사용자 데이터 크기 × 보존기간/전송량. | AiFeature 없음. S3 운영 시 저장·요청·egress 비용이 사용자 업로드/생성·보존량에 따라 늘어남. 앱 수준 quota·무료 사용자별 저장용량 한도는 이번 경로에서 확인되지 않음. MinIO 자체는 서버 디스크·대역폭 비용. |
| 결제사 PortOne/Toss/Paddle `infrastructure/payment/*Client.kt` | 결제/취소/빌링·webhook. 사용자 결제 또는 scheduler renewal. | 인입 매출 경로이지만 PSP 수수료·빌링 비용은 앱 크레딧과 무관. 요율은 코드에서 미확인. PSP 조회 API 호출이 사용량 과금인지도 코드로 판단 불가. |
| 외부 웹훅 `application/webhook/WebhookDispatcher.kt`, `WebhookUseCase.kt` | 사용자 설정 endpoint 이벤트 전송; dispatcher 고정 간격 retry. payload/event 수와 endpoint 응답실패에 비례. | 크레딧 없음. 상대 API 요금은 상대 부담이나 우리 서버 outbound/재시도 비용은 가능. 등록 endpoint 수·재시도 보존/최대 시도 정책 확인 필요. |

## 5. 크레딧 없이 반복 가능한 무거운 서버 작업 및 제한

| 작업 | 실행 경로/코드상 보호 | 확인 결과 |
|---|---|---|
| FFmpeg Shorts 렌더 | `ShortsRenderUseCase.kt:142` → `FfmpegVideoRenderer`; user-started render endpoint/auto-schedule worker. `ShortsRenderResourceManager.kt` 기본 동시성 1, 설정 가능 범위 1–32 및 queue. | 렌더 요청은 `AiFeature` 차감 없음. RENDER_SPEC pipeline stage도 `ShortsPipelineCreditRequirements.kt:16-20`에서 무과금. CPU·디스크/입력 영상 길이에 따라 서버 원가가 발생. 무료 사용자별 횟수·분량 quota는 이번 경로에서 확인되지 않음. |
| 텍스트 슬라이드 FFmpeg generation | `GeneratedVideoUseCase.kt:69` → `FfmpegVideoGenerationAdapter.kt:33-70`; public endpoint. | 크레딧 차감 없음. 렌더 제한은 timeout 기본 180초(설정 가능), prompt 2,000자; 동시성 전역/사용자별 제한은 호출 흐름에서 확인 못함. 선택 voice이면 앞의 무과금 TTS도 호출. |
| 외부 영상 다운로드/import | `YtDlpVideoDownloader.kt`; URL import/download는 video import API/user request. `VideoImportJobService.kt`는 `videodownload.max-concurrent-jobs` 기본 2, 설정 최소 1로 동시 실행 제한. `VideoDownloadUseCase.kt:28-36`은 시작/실행 전 월 업로드 quota 확인; 최대 파일 크기는 공통 직접 업로드 제한(10GiB). | 크레딧 없음. 네트워크/디스크/CPU 원가는 가능. 월별 quota 존재는 확인했지만 플랜별 숫자 및 무료 사용자 quota가 월 원가를 얼마나 제한하는지는 entitlement 설정과 함께 추가 확인 필요. 단건 10GiB × 기본 2 동시 다운로드는 큰 임시 디스크 사용을 허용함. |
| STT/LLM worker | batch recovery scheduler는 15초 polling, AI pipeline recovery scheduler 5분; polling 자체는 DB 조회지만 실제 처리 job은 사용자 사전 차감이 있어 무료 반복은 아님. | retry가 공급자 중복 청구 위험을 만들 수 있는 구간은 제공자 요청이 성공했으나 앱 타임아웃/결과 저장 실패한 경우. 앱 환불/재시도는 공급자 비용을 되돌리지 않음. |
| 배치 작업 | `AiBatchProcessingUseCase.kt:104-110` recovery는 15초마다 최대 50 active batch 재기동; 처리 worker의 `ExecutorConfig.aiBatchSemaphore`로 동시 item 수 제한. | 시작 요청에서 operation별 예상 크레딧 검사/차감 여부는 해당 usecase 상단 구현에 있음; item 실패·재시도와 실제 차감 정합성을 별도 회계 검증 권장. 각 video item 수 및 batch 요청 최대치 확인 필요. |
| 댓글 sync + 감정 분석 | `CommentSyncScheduler.kt` 5분 주기, 신규 댓글마다 배치 분석. | 모델 비용은 무과금, 입력은 댓글 길이당 200자 truncate, batch 댓글 수 상한은 확인되지 않음. 주기 × 연결 채널 × 신규 댓글 유입으로 무료 원가 증가. |
| 일반 사용자 설정 / AI provider | 대화형 AI에 `AiRateLimiter`가 사용됨. | per-user rate limiter가 있어도 비용은 rate limit window × token 상한 × users. per-call `maxTokens` 없는 경로는 최대 출력량 보장이 약함. |

## 6. 무료 사용자의 월 최대 원가 요소

정확한 월 금액은 공급자 단가, 실제 요금제/API 조건, 저장·보존·전송 정책, 무료 크레딧 수량, per-feature 실행 횟수/입력·출력 상한을 확인하지 못해 계산할 수 없다. 코드 근거만으로도 현재 **유한한 월 최대 원가가 보장된다고 결론낼 수 없다**.

| 요인 | 코드에서 확인된 노출 | 월 상한 계산을 막는 미확인/위험 |
|---|---|---|
| 무료 크레딧으로 유료 LLM/STT | AI 호출 기능은 크레딧을 선차감하며 `AiFeature`별 단가 존재. `AiFeature.SENTIMENT_ANALYSIS`는 0. | 무료 플랜의 월 크레딧 수량·크레딧 팩/플랜 연결 데이터가 코드 외 운영 설정에 있거나 별도 조회되지 않아 총 사용량 환산 불가. 한 크레딧이 원가를 보장하는 가격은 없음. |
| 0크레딧 댓글 감정 | 5분 자동 댓글 동기화, 신규 댓글은 개별 길이 200자 제한 후 LLM 분석. | channel count/comment volume과 prompt batch count에 상한이 보이지 않아 API 비용은 월 무상한 가능. |
| 무과금 TTS | Public video endpoint, prompt 최대 2,000자 (adapter 최대 4,000), voice 선택 시 TTS 호출. | 월 요청 rate/quota/사용자 저장/플랜 entitlement 또는 크레딧 차감 확인 안 됨. 반복 호출이 가능하면 외부 usage charge 누적. |
| 저장공간·전송량 | 사용자 영상, 생성물 S3/MinIO 업로드 및 장기 보관. | 무료 계정별 bytes/object/retention cap 및 다운로드 egress 제한 확인 안 됨. 단건 업로드 한도만으로 월 저장 원가는 제한되지 않음. |
| FFmpeg 렌더/다운로드 | 사용자 요청 및 pipeline에서 영상 처리, import/download 작업 존재. concurrency semaphore 일부 확인. | 무료 사용자별 월 횟수, 총 영상 길이, 큐 backlog, 동시 렌더/다운로드의 모든 경로 공통 제한 확인 안 됨. 서버 고정비·디스크는 크레딧으로 직접 제한되지 않음. |
| 플랫폼 publish/API | 직접 API 호출은 별도 크레딧 차감 없음. 업로드 파일 크기·전송량에 따라 서버 네트워크 부담. | provider quota와 우리 bandwidth/egress 비용, 게시 자동 retry 최대 횟수 확인 필요. |

### 우선 위험 결론 (코드 사실과 분리)

1. **확인된 무과금 유료 추론은 댓글 감정 분석(0 credit)과 선택형 TTS(credit path 없음)**이다. 감정 분석은 5분 스케줄러에 의해 자동 발생하므로 사용자가 직접 버튼을 누르지 않아도 비용이 날 수 있다.
2. 크레딧을 받는 대부분의 LLM은 호출 전에 고정 크레딧을 확보하지만, token 수/사용량 기반 과금과 매칭된 **원가 상한**은 아니다. 여러 입력은 목록·기간·문자열에 비례하며 endpoint 한도 확인이 남아 있다.
3. 앱 크레딧 환불은 실패 사용자에 대한 내부 잔액 복원이다. 공급자 요청이 이미 처리된 뒤 timeout/저장 실패로 환불되면 외부 원가는 그대로 남을 수 있다.
4. 무료 계정 월 원가 상한은 무과금 감정 분석, TTS, storage/egress, FFmpeg/import 경로 때문에 소스만으로 보장된다고 말할 수 없다.
5. `maxTokens`는 각 ChatClient 호출별 설정이 아니다. Claude와 DashScope만 구성상 4096이 확인됐고, Gemini/OpenAI defaults 및 OpenAI chat 모델 미지정 기본값은 의존성/실행 설정을 더 확인해야 한다.

## 확인 미완료 범위

- API/controller request DTO, `@Valid` 제약, DB paging, rate limiter 실제 숫자, storage quota/retention, render/import/download 전체 동시성 설정을 모든 기능별로 대조하지 않았다. 위 표에서는 확인하지 못한 제한을 “미확인”으로 명시했으며 제한이 있다고 추정하지 않는다.
- `AiFeature` 단가와 플랜별 무료 크레딧 배정은 코드 enum 자체만으로 월 원가 한도를 만들지 않는다.
- 실제 공급자 청구 단가, 공급자 이용약관/무료 quota, PSP 수수료는 외부 자료/운영 계정 확인이 필요하다. 이번 단계에서는 요청 범위대로 외부 호출을 하지 않았다.
