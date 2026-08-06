# WIP 컨트롤러 백엔드 인벤토리

조사일: 2026-08-07

## 판정 기준

이 문서는 백엔드 소스만으로 내린 **1차 분류**다. 프론트 route/nav/view/API 참조는 Kimi의 `docs/plans/wip-frontend-refs.md`와 합친 뒤 최종 결정한다.

- **keep-enable 후보**: 기능 전용 마이그레이션/테이블이 있고, UseCase가 실제 저장·조회 또는 AI 처리를 하며, 생성자 의존성의 인프라 어댑터가 확인되는 경우. 단, 현재 테스트가 없으므로 테스트를 먼저 추가해야 한다.
- **keep-disabled**: 테이블은 있거나 도메인 계약은 있으나 어댑터 누락, 외부 자격증명 미확인, AI/계산 스텁, 또는 의존성 일부 미완성인 경우. 삭제하지 않고 활성화 게이트 뒤에 둔다.
- **remove 후보**: `feature-specific migration 없음 + infrastructure adapter 없음 + UseCase가 명백한 placeholder`를 모두 만족하고, 백엔드의 다른 흐름에서도 사용되지 않는 경우에만 임시 후보로 둔다. 프론트 참조를 아직 확인하지 않았으므로 이 문서만으로 삭제를 확정하지 않는다.

`V24__stub_features_tables.sql:1-2`가 이 테이블들을 “52개 스텁 엔드포인트”용으로 만들었다고 명시한다. 따라서 **마이그레이션 존재만으로 구현 완료로 판정하지 않았다.**

## 실측 범위와 공통 결과

- 55개는 모두 `backend/onGo-api/src/main/kotlin/com/ongo/api/**/**/*Controller.kt`의 `@Profile("wip")`에 해당한다. 각 행의 `Controller.kt:<line>`은 프로필 선언 줄이다.
- UseCase 소스와 생성자 의존성은 `backend/onGo-application/src/main/kotlin/com/ongo/application/**`에서 확인했다. 각 행의 `UseCase.kt:<line>`은 클래스/생성자 시작 줄이다.
- 인프라 어댑터는 `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/**`에서 feature repository 인터페이스명과 대응하는 `*JooqRepository`를 검색했다.
- 2026-08-07 현재 55개 UseCase를 이름으로 참조하는 `*Test.kt`는 **0개**였다. 따라서 기존 백엔드 전체 테스트 통과는 WIP 컨트롤러의 기본 프로필 노출을 증명하지 않는다.
- “미확인”은 런타임 기본 프로필 기동/실제 외부 API 호출을 하지 않았다는 뜻이다. 코드 수정은 하지 않았다.

## 분류표

| 기능 | 컨트롤러 / UseCase 근거 | 저장소·마이그레이션 실측 | 외부 의존성·완성도 | 1차 판정 |
|---|---|---|---|---|
| ABTest | `api/abtest/ABTestController.kt:23`; `application/abtest/ABTestUseCase.kt:18-22` | `V4__analytics_tables.sql:21-46` (`ab_tests`, `ab_test_variants`); `ABTestJooqRepository.kt` 확인 | CRUD, 시작/중지·승자 계산이 실제 구현(UseCase:24-170); 테스트 0 | **keep-enable 후보** (테스트 필요) |
| Agency | `api/agency/AgencyController.kt:15`; `application/agency/AgencyUseCase.kt:14-16` | `V31__collab_agency_tables.sql:175-193`; feature 전용 어댑터 미발견 | 워크스페이스/크리에이터 계약은 있으나 Spring repository bean 미확인; 테스트 0 | **keep-disabled** |
| AiCalendar | `api/aicalendar/AiCalendarController.kt:15`; `application/aicalendar/AiCalendarUseCase.kt:12-14` | `V32__ai_automation_tables.sql:31-75`, `V45__content_calendar_tables.sql:2-56`; `AiCalendarJooqRepository.kt` 확인 | 생성 데이터가 `"[]"`로 고정(`AiCalendarUseCase.kt:34`, “AI will populate later”); 테스트 0 | **keep-disabled** (명백한 부분 스텁) |
| AudienceSegment | `api/audiencesegment/AudienceSegmentController.kt:15`; `application/audiencesegment/AudienceSegmentUseCase.kt:14-16` | `V58__create_audience_crm_tables.sql:5-57`; feature 전용 segment 어댑터 미발견 | CRM 테이블은 있으나 UseCase 의존성의 인프라 연결 미확인; 테스트 0 | **keep-disabled** |
| BrandVoice | `api/brandvoice/BrandVoiceController.kt:15`; `application/brandvoice/BrandVoiceUseCase.kt:14-16` | `V24__stub_features_tables.sql:65-83` (`brand_voice_profiles`); 어댑터 미발견 | `generateText`가 `"[${profile.tone} 톤] ..."` 하드코딩(`BrandVoiceUseCase.kt:87-113`); AI credential 미확인; 테스트 0 | **keep-disabled** (프론트 미참조 확인 후 remove 후보 검토) |
| CalendarInsights | `api/calendarinsights/CalendarInsightsController.kt:15`; `application/calendarinsights/CalendarInsightsUseCase.kt:11` | `V27__analytics_insights_a_tables.sql:119-160`; 어댑터 미발견 | 계산 UseCase는 있으나 저장소 bean 미확인; 테스트 0 | **keep-disabled** |
| ChannelHealth | `api/channelhealth/ChannelHealthController.kt:15`; `application/channelhealth/ChannelHealthUseCase.kt:20-26` | `V27__analytics_insights_a_tables.sql:163-190`, `V46__channel_health_tables.sql:2-35`; `ChannelHealthMetricJooqRepository.kt`, `HealthTrendJooqRepository.kt` 확인 | 실제 Analytics/Video 데이터를 읽어 점수 계산·저장(`ChannelHealthUseCase.kt:28-116`); 외부 credential 없음; 테스트 0 | **keep-enable 후보** (테스트 필요) |
| CollaborationBoard | `api/collaborationboard/CollaborationBoardController.kt:15`; `application/collaborationboard/CollaborationBoardUseCase.kt:15` | `V31__collab_agency_tables.sql:5-35`; 어댑터 미발견 | DB 계약은 있으나 persistence bean 미확인; 테스트 0 | **keep-disabled** |
| CommentSummary | `api/commentsummary/CommentSummaryController.kt:15`; `application/commentsummary/CommentSummaryUseCase.kt:10` | `V29__fan_management_tables.sql:89-114`; 어댑터 미발견 | 결과 테이블은 있으나 저장소 연결 미확인; 테스트 0 | **keep-disabled** |
| CompetitorAnalysis | `api/competitoranalysis/CompetitorAnalysisController.kt:15`; `application/competitoranalysis/CompetitorAnalysisUseCase.kt:15` | `V27__analytics_insights_a_tables.sql:5-28`; `CompetitorJooqRepository.kt`는 기존 competitor 계약용으로 확인되나 WIP report 경로 매핑은 미확인 | 외부 경쟁자 데이터 수집 credential/실행 경로 미확인; 테스트 0 | **keep-disabled** |
| ContentAbAnalyzer | `api/contentabanalyzer/ContentAbAnalyzerController.kt:15`; `application/contentabanalyzer/ContentAbAnalyzerUseCase.kt:18-22` | `V24__stub_features_tables.sql:329-359` (`content_ab_tests`, `content_variants`); 어댑터 미발견 | 테이블은 있으나 persistence bean 미확인; 테스트 0 | **keep-disabled** |
| ContentCalendarAi | `api/contentcalendarai/ContentCalendarAiController.kt:15`; `application/contentcalendarai/ContentCalendarAiUseCase.kt:19-25` | `V32__ai_automation_tables.sql:31-75`, `V45__content_calendar_tables.sql:2-56`; `CalendarSuggestionJooqRepository.kt`, `CalendarAiSlotJooqRepository.kt` 확인 | ChatClientResolver로 구조화 AI 호출, 크레딧 차감/환불, 일괄 저장(`ContentCalendarAiUseCase.kt:35-82`); AI provider credential 필요; 테스트 0 | **keep-enable 후보** (AI 설정·테스트 필요) |
| ContentLibrary | `api/contentlibrary/ContentLibraryController.kt:15`; `application/contentlibrary/ContentLibraryUseCase.kt:16` | `V24__stub_features_tables.sql:225-256`; 어댑터 미발견 | 라이브러리/폴더 테이블은 있으나 저장소 bean 미확인; 테스트 0 | **keep-disabled** |
| ContentRepurposer | `api/contentrepurposer/ContentRepurposerController.kt:15`; `application/contentrepurposer/ContentRepurposerUseCase.kt:10` | `V26__content_creation_tables.sql:51-78`; 어댑터 미발견 | repurpose job/template 계약만 있고 실행 어댑터 미확인; 테스트 0 | **keep-disabled** |
| ContentRewriter | `api/contentrewriter/ContentRewriterController.kt:15`; `application/contentrewriter/ContentRewriterUseCase.kt:14` | `V26__content_creation_tables.sql:80-91`; 어댑터 미발견 | 재작성 결과 저장 연결·AI credential 미확인; 테스트 0 | **keep-disabled** |
| ContentRights | `api/contentrights/ContentRightsController.kt:15`; `application/contentrights/ContentRightsUseCase.kt:16` | `V24__stub_features_tables.sql:397-436`; 어댑터 미발견 | 권리/알림 테이블은 있으나 감지·알림 실행 경로 미확인; 테스트 0 | **keep-disabled** |
| ContentSeries | `api/contentseries/ContentSeriesController.kt:15`; `application/contentseries/ContentSeriesUseCase.kt:16` | `V26__content_creation_tables.sql:128-162`; 어댑터 미발견 | 시리즈 CRUD persistence 미확인; 테스트 0 | **keep-disabled** |
| ContentTranslator | `api/contenttranslator/ContentTranslatorController.kt:15`; `application/contenttranslator/ContentTranslatorUseCase.kt:14` | `V33__misc_tables.sql:260-288`; 어댑터 미발견 | 번역 job/glossary 테이블은 있으나 번역 provider·어댑터 미확인; 테스트 0 | **keep-disabled** |
| ContentVersioning | `api/contentversioning/ContentVersioningController.kt:15`; `application/contentversioning/ContentVersioningUseCase.kt:10` | `V26__content_creation_tables.sql:93-126`; 어댑터 미발견 | 버전 테이블은 있으나 저장소 bean 미확인; 테스트 0 | **keep-disabled** |
| CopyrightCheck | `api/copyrightcheck/CopyrightCheckController.kt:15`; `application/copyrightcheck/CopyrightCheckUseCase.kt:14` | `V33__misc_tables.sql:246-258`; 어댑터 미발견 | 외부 저작권 판정 provider/credential 미확인; 테스트 0 | **keep-disabled** |
| CreatorAcademy | `api/creatoracademy/CreatorAcademyController.kt:15`; `application/creatoracademy/CreatorAcademyUseCase.kt:14` | `V33__misc_tables.sql:174-217`; 어댑터 미발견 | 강좌/수강 테이블은 있으나 persistence 연결 미확인; 테스트 0 | **keep-disabled** |
| CreatorBenchmark | `api/creatorbenchmark/CreatorBenchmarkController.kt:15`; `application/creatorbenchmark/CreatorBenchmarkUseCase.kt:10` | `V31__collab_agency_tables.sql:106-133`; 어댑터 미발견 | 비교 데이터 계산·저장 연결 미확인; 테스트 0 | **keep-disabled** |
| CreatorMilestone | `api/creatormilestone/CreatorMilestoneController.kt:15`; `application/creatormilestone/CreatorMilestoneUseCase.kt:12` | `V31__collab_agency_tables.sql:135-153`; 어댑터 미발견 | 마일스톤 저장소 bean 미확인; 테스트 0 | **keep-disabled** |
| CreatorNetwork | `api/creatornetwork/CreatorNetworkController.kt:15`; `application/creatornetwork/CreatorNetworkUseCase.kt:14-17` | `V24__stub_features_tables.sql:85-117`, `V31__collab_agency_tables.sql:39-75`; 어댑터 미발견 | 프로필/협업 요청 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| CrossAnalytics | `api/crossanalytics/CrossAnalyticsController.kt:15`; `application/crossanalytics/CrossAnalyticsUseCase.kt:21-26` | `V24__stub_features_tables.sql:210-223`; 어댑터 미발견 | 교차 플랫폼 보고서 persistence 미확인; 테스트 0 | **keep-disabled** |
| FanCommunity | `api/fancommunity/FanCommunityController.kt:15`; `application/fancommunity/FanCommunityUseCase.kt:11` | `V29__fan_management_tables.sql:5-35`; 어댑터 미발견 | 게시글/댓글 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| FanFunding | `api/fanfunding/FanFundingController.kt:15`; `application/fanfunding/FanFundingUseCase.kt:17-20` | `V30__revenue_commerce_tables.sql:39-88`; 어댑터 미발견 | 결제/펀딩 외부 연동과 저장소 미확인; 테스트 0 | **keep-disabled** |
| FanInsights | `api/faninsights/FanInsightsController.kt:15`; `application/faninsights/FanInsightsUseCase.kt:10-14` | `V28__analytics_insights_b_tables.sql:109-160`; 어댑터 미발견 | 분석 결과 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| FanPoll | `api/fanpoll/FanPollController.kt:15`; `application/fanpoll/FanPollUseCase.kt:13-16` | `V29__fan_management_tables.sql:37-87`; 어댑터 미발견 | 투표/옵션 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| FanSegmentCampaign | `api/fansegmentcampaign/FanSegmentCampaignController.kt:15`; `application/fansegmentcampaign/FanSegmentCampaignUseCase.kt:18-21` | `V24__stub_features_tables.sql:362-395`; `fan_campaigns`, `campaign_segments`; 어댑터 미발견 | 캠페인·세그먼트 실행 경로 미확인; 테스트 0 | **keep-disabled** |
| HashtagAnalytics | `api/hashtaganalytics/HashtagAnalyticsController.kt:15`; `application/hashtaganalytics/HashtagAnalyticsUseCase.kt:11-14` | `V27__analytics_insights_a_tables.sql:30-117`; 어댑터 미발견 | 외부 플랫폼 수집 credential/저장소 미확인; 테스트 0 | **keep-disabled** |
| InfluencerMatch | `api/influencermatch/InfluencerMatchController.kt:15`; `application/influencermatch/InfluencerMatchUseCase.kt:17-20` | `V31__collab_agency_tables.sql:39-75`; 어댑터 미발견 | 매칭 데이터/외부 검색 provider 미확인; 테스트 0 | **keep-disabled** |
| LiveStream | `api/livestream/LiveStreamController.kt:15`; `application/livestream/LiveStreamUseCase.kt:16-19` | `V24__stub_features_tables.sql:292-327`; 어댑터 미발견 | 스트림/채팅 테이블은 있으나 플랫폼 스트림 연동 미확인; 테스트 0 | **keep-disabled** |
| MediaKit | `api/mediakit/MediaKitController.kt:15`; `application/mediakit/MediaKitUseCase.kt:14-16` | `V31__collab_agency_tables.sql:154-173`; 어댑터 미발견 | 미디어킷 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| MoodBoard | `api/moodboard/MoodBoardController.kt:15`; `application/moodboard/MoodBoardUseCase.kt:10-13` | `V33__misc_tables.sql:46-72`; 어댑터 미발견 | 보드/아이템 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| MusicRecommender | `api/musicrecommender/MusicRecommenderController.kt:15`; `application/musicrecommender/MusicRecommenderUseCase.kt:12-15` | `V26__content_creation_tables.sql:274-312`; 어댑터 미발견 | 음악 catalog/provider credential 미확인; 테스트 0 | **keep-disabled** |
| PerformanceReport | `api/performancereport/PerformanceReportController.kt:15`; `application/performancereport/PerformanceReportUseCase.kt:15-18` | `V28__analytics_insights_b_tables.sql:31-70`; 어댑터 미발견 | 보고서/섹션 persistence 미확인; 테스트 0 | **keep-disabled** |
| PlatformAutomation | `api/platformautomation/PlatformAutomationController.kt:15`; `application/platformautomation/PlatformAutomationUseCase.kt:15-18` | `V32__ai_automation_tables.sql:2-29`; 어댑터 미발견 | 자동화 실행·플랫폼 credential 미확인; 테스트 0 | **keep-disabled** |
| PlatformHealth | `api/platformhealth/PlatformHealthController.kt:15`; `application/platformhealth/PlatformHealthUseCase.kt:10-13` | `V28__analytics_insights_b_tables.sql:5-29`; 어댑터 미발견 | 플랫폼 상태 수집/저장 연결 미확인; 테스트 0 | **keep-disabled** |
| Portfolio | `api/portfolio/PortfolioController.kt:15`; `application/portfolio/PortfolioUseCase.kt:15-17` | `V24__stub_features_tables.sql:7-26`; 어댑터 미발견 | 포트폴리오 테이블은 있으나 persistence bean 미확인; 테스트 0 | **keep-disabled** |
| PortfolioBuilder | `api/portfoliobuilder/PortfolioBuilderController.kt:15`; `application/portfoliobuilder/PortfolioBuilderUseCase.kt:15-18` | `V33__misc_tables.sql:219-244`; 어댑터 미발견 | 섹션/빌더 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| Prediction | `api/prediction/PredictionController.kt:15`; `application/prediction/PredictionUseCase.kt:16-18` | `V24__stub_features_tables.sql:28-46`; 어댑터 미발견 | 예측 계산·저장 연결 및 모델 credential 미확인; 테스트 0 | **keep-disabled** |
| RevenueAnalyzer | `api/revenueanalyzer/RevenueAnalyzerController.kt:15`; `application/revenueanalyzer/RevenueAnalyzerUseCase.kt:14-17` | `V30__revenue_commerce_tables.sql:97-124`; feature 전용 어댑터 미발견 | 분석 persistence/계산 경로 미확인; 테스트 0 | **keep-disabled** |
| RevenueForecaster | `api/revenueforecaster/RevenueForecasterController.kt:15`; `application/revenueforecaster/RevenueForecasterUseCase.kt:25-31` | `V30__revenue_commerce_tables.sql:97-124`; feature 전용 어댑터 미발견 | 예측 모델 및 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| ScheduleOptimizer | `api/scheduleoptimizer/ScheduleOptimizerController.kt:15`; `application/scheduleoptimizer/ScheduleOptimizerUseCase.kt:23-29` | `V32__ai_automation_tables.sql:106-133`; `OptimalSlotJooqRepository.kt`만 확인, `ScheduleRecommendationJooqRepository` 미발견 | AI/크레딧·환불 로직은 실제지만 필수 recommendation adapter가 없음; AI credential 필요; 테스트 0 | **keep-disabled** |
| ScriptWriter | `api/scriptwriter/ScriptWriterController.kt:15`; `application/scriptwriter/ScriptWriterUseCase.kt:14-17` | `V26__content_creation_tables.sql:5-49`; 어댑터 미발견 | 스크립트 저장/생성 provider 미확인; 테스트 0 | **keep-disabled** |
| SentimentAnalyzer | `api/sentimentanalyzer/SentimentAnalyzerController.kt:15`; `application/sentimentanalyzer/SentimentAnalyzerUseCase.kt:13-16` | `V24__stub_features_tables.sql:119-153`; 어댑터 미발견 | 감성 분석 결과 저장·모델 provider 미확인; 테스트 0 | **keep-disabled** |
| SmartReply | `api/smartreply/SmartReplyController.kt:15`; `application/smartreply/SmartReplyUseCase.kt:14-18` | `V29__fan_management_tables.sql:117-169`; 어댑터 미발견 | AI 답변 생성/규칙 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| SocialListening | `api/sociallistening/SocialListeningController.kt:15`; `application/sociallistening/SocialListeningUseCase.kt:16-19` | `V33__misc_tables.sql:291-325`; 어댑터 미발견 | 외부 mention 수집 credential/저장 연결 미확인; 테스트 0 | **keep-disabled** |
| Sponsorship | `api/sponsorship/SponsorshipController.kt:15`; `application/sponsorship/SponsorshipUseCase.kt:13-16` | `V30__revenue_commerce_tables.sql:126-170`; 어댑터 미발견 | 계약/딜 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| SubtitleEditor | `api/subtitleeditor/SubtitleEditorController.kt:15`; `application/subtitleeditor/SubtitleEditorUseCase.kt:14-16` | `V26__content_creation_tables.sql:164-208`; 어댑터 미발견 | UI는 1차 라운드에서 추가됐지만 백엔드 adapter·WIP 테스트 0; 활성화하면 404→context 실패 가능 | **keep-disabled** (테스트·어댑터 우선) |
| SubtitleTranslation | `api/subtitletranslation/SubtitleTranslationController.kt:15`; `application/subtitletranslation/SubtitleTranslationUseCase.kt:11-15` | `V26__content_creation_tables.sql:210-250`; 어댑터 미발견 | 번역 provider/저장 연결 미확인; 테스트 0 | **keep-disabled** |
| TrendPredictor | `api/trendpredictor/TrendPredictorController.kt:15`; `application/trendpredictor/TrendPredictorUseCase.kt:13-16` | `V32__ai_automation_tables.sql:136-161`; 어댑터 미발견 | 외부 trend 수집·예측 모델/저장 연결 미확인; 테스트 0 | **keep-disabled** |
| VideoScriptAssistant | `api/videoscriptassistant/VideoScriptAssistantController.kt:15`; `application/videoscriptassistant/VideoScriptAssistantUseCase.kt:14-17` | `V32__ai_automation_tables.sql:163-193`; 어댑터 미발견 | AI 생성 provider/스크립트 저장 연결 미확인; 테스트 0 | **keep-disabled** |
| VisualWorkflow | `api/workflow/VisualWorkflowController.kt:15`; `application/workflow/VisualWorkflowUseCase.kt:14-16` | `V24__stub_features_tables.sql:48-63`; 어댑터 미발견 | 워크플로 테이블은 있으나 실행/저장 연결 미확인; 테스트 0 | **keep-disabled** |

## 결론과 다음 게이트

백엔드 근거만으로 **keep-enable 후보는 ABTest, ChannelHealth, ContentCalendarAi 세 개**다. 이들도 `@Profile("wip")` 제거 전에 컨트롤러 통합 테스트, 기본 프로필 기동 확인, 저장소 실제 DB 테스트를 추가해야 한다. ContentCalendarAi와 같이 외부 AI를 쓰는 기능은 credential/config 검증을 별도 테스트로 고정해야 한다.

나머지 52개는 삭제가 아니라 **keep-disabled**다. 테이블이 없어서가 아니라, V24가 스텁 테이블을 대량으로 만든 반면 대응 인프라 어댑터와 실행 계약이 대부분 없기 때문이다. `AiCalendar`와 `BrandVoice`처럼 코드 안에 명시적 placeholder가 있어도, 마이그레이션과 도메인 계약이 존재하므로 프론트 참조를 보기 전 remove로 확정하지 않는다.

따라서 이 백엔드 조사에서 확정 가능한 **remove 후보는 0개**다. 사용자가 제시한 세 조건을 만족하는 기능이 프론트에서도 참조되지 않는다는 Kimi 결과까지 확인된 경우에만 remove 후보로 올린다. 컨트롤러를 일괄 활성화하거나, 테스트 없이 삭제하는 것은 금지한다.

### 분류를 뒤집을 수 있는 증거

1. Kimi 문서에서 실제 route/nav/view/API 참조가 확인되면 keep-disabled 기능은 우선순위를 올리되, 어댑터·credential·테스트 게이트는 그대로 적용한다.
2. 프론트 참조가 0이고, 후속 `rg`에서 feature repository adapter와 다른 백엔드 호출자도 0이며, UseCase가 placeholder임이 확인되면 remove 후보로 전환한다.
3. Spring 기본 프로필 기동에서 빈 생성 실패가 확인되면 해당 기능은 테스트가 생길 때까지 keep-disabled로 유지한다. 이 문서의 소스 분석만으로 기동 성공을 주장하지 않는다.
