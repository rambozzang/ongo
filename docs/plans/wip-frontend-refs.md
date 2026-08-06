# WIP 컨트롤러 55개 — 프론트 대조 문서

작성: Kimi · 2026-08-07 · `.omc/VIBLO_CONTRACT.md` §13 Kimi 행
짝 문서: `docs/plans/wip-inventory.md` (Codex — 백엔드 근거)
방법: `grep -rln '@Profile("wip")' backend/onGo-api/src/main/kotlin` → 55개. 각 컨트롤러의
`@RequestMapping` 경로를 프론트 `frontend/src` 전수 grep으로 대조. 코드 수정 없음, 문서만.

**분류 (프론트 관점)**

| 분류 | 의미 | 개수 |
|---|---|---|
| F1 | UI 완성 + 라우트 연결, 백엔드만 막힘 | 1 |
| F2 | 뷰/모달 있으나 도달 경로 없음 (라우트 없음 또는 리다이렉트 고아) | 2 |
| F3 | api 클라이언트 파일만 존재 (뷰 없음) | 3 |
| F4 | 죽은 i18n 키만 존재 (locales 외 참조 0건) | 28 |
| F5 | i18n 키는 살아있지만 **다른(비-wip) 기능 소유**, 이 컨트롤러 전용 UI 없음 | 18 |
| F6 | 프론트 흔적 전무 | 3 |

공통 사실: `useNavigation.ts:89-181` 의 내비게이션 항목에 55개 기능 링크는 **0건** —
"눌러도 안 되는 메뉴"는 없다. 라우트는 `frontend/src/router/index.ts` (449줄) 전수 확인.

---

## F1 — UI 완성 + 라우트 연결, 백엔드만 막힘 (1)

| 컨트롤러 | base path | 프론트 근거 |
|---|---|---|
| SubtitleEditorController | `/api/v1/subtitle-editor` | 뷰 `frontend/src/views/SubtitleEditorView.vue`, api `frontend/src/api/subtitleEditor.ts`, 라우트 `frontend/src/router/index.ts:159` (`/subtitle-editor`), vitest 14개 `frontend/src/api/subtitleEditor.test.ts`. nav 미연결(의도적). 1순위 활성화 후보(계약 §13) |

## F2 — 뷰는 있으나 도달 경로 없음 (2)

| 컨트롤러 | base path | 프론트 근거 |
|---|---|---|
| ABTestController | `/api/v1/ab-tests` | api `frontend/src/api/abtest.ts:14-26`, 스토어 `frontend/src/stores/abtest.ts`, 뷰 `frontend/src/views/AbTestView.vue` + `components/abtest/*` 까지 완비. **그러나 라우트 0건**(router 에 abtest 없음), nav 항목 0건, 다른 뷰의 import 0건 — 완전 고아. 라우트 1개만 달면 살아남 |
| ContentCalendarAiController | `/api/v1/content-calendar-ai` | api `frontend/src/api/contentCalendarAi.ts:32-42`, 모달 `frontend/src/components/schedule/AiCalendarGenerateModal.vue`. 사용처가 레거시 `frontend/src/views/CalendarView.vue` 뿐인데 `/calendar` 는 `redesign-calendar` 로 redirect (`router/index.ts:112-116`) → 사실상 도달 불가. 리디자인 캘린더에 모달을 옮기면 살아남 |

## F3 — api 클라이언트만 존재 (3)

| 컨트롤러 | base path | 프론트 근거 |
|---|---|---|
| AgencyController | `/api/v1/agency` | `frontend/src/api/agency.ts:15` (`/agency/kpi`), `frontend/src/types/agency.ts`. 임포트하는 뷰·스토어 0건. 뷰 없음, 라우트 없음 |
| PredictionController | `/api/v1/predictions` | `frontend/src/api/prediction.ts:18-33`. 임포트 0건. 주의: `prediction.*` i18n 키는 살아있지만 `PerformanceScoreCard.vue` 가 쓰고, 그 데이터는 `/videos/{id}/predict-views` (`frontend/src/api/viewsPrediction.ts:24`, **비-wip**) — 이 컨트롤러와 무관 |
| ScheduleOptimizerController | `/api/v1/schedule-optimizer` | `frontend/src/api/scheduleOptimizer.ts:32-48`. 사용처 2곳: `AiOptimalTimeModal.vue`(레거시 CalendarView 전용 → F2와 같은 고아), `frontend/src/stores/redesignCalendar.ts`(리디자인 캘린더, `getRecommendations` 조회만). 부분적으로 살아있음 |

## F4 — 죽은 i18n 키만 존재 (28)

locales 섹션이 있으나 views/components/stores/composables 참조 0건.
근거 키는 `frontend/src/locales/ko/common.json` 최상위 섹션.

| 컨트롤러 | base path | 죽은 키 |
|---|---|---|
| BrandVoiceController | `/api/v1/brand-voice` | `brandVoice.*` |
| CalendarInsightsController | `/api/v1/calendar-insights` | `calendarInsights.*` |
| ChannelHealthController | `/api/v1/channel-health` | `channelHealth.*` |
| CompetitorAnalysisController | `/api/v1/competitor-analysis` | `competitorAnalysis.*` |
| ContentRepurposerController | `/api/v1/content-repurposer` | `contentRepurposer.*` |
| ContentRewriterController | `/api/v1/content-rewriter` | `contentRewriter.*` |
| ContentSeriesController | `/api/v1/content-series` | `contentSeries.*` |
| ContentTranslatorController | `/api/v1/content-translator` | `contentTranslator.*` |
| ContentVersioningController | `/api/v1/content-versioning` | `contentVersioning.*` |
| CopyrightCheckController | `/api/v1/copyright-check` | `copyrightCheck.*` |
| CreatorBenchmarkController | `/api/v1/creator-benchmark` | `creatorBenchmark.*` |
| CreatorNetworkController | `/api/v1/creator-network` | `creatorMarketplace.*` (추정 — title "크리에이터 마켓플레이스") |
| CrossAnalyticsController | `/api/v1/cross-analytics` | `crossAnalytics.*` (주의: `crossPlatform.*` 은 `AnalyticsView.vue:54-84` 가 사용, 비-wip analytics API) |
| FanFundingController | `/api/v1/fan-funding` | `fanFunding.*` |
| FanInsightsController | `/api/v1/fan-insights` | `fanInsights.*` |
| FanSegmentCampaignController | `/api/v1/fan-segment-campaigns` | `fanSegmentCampaign.*` |
| HashtagAnalyticsController | `/api/v1/hashtag-analytics` | `hashtagStrategy.*` (추정 — title 기반) |
| InfluencerMatchController | `/api/v1/influencer-match` | `influencerMatch.*` |
| LiveStreamController | `/api/v1/live-streams` | `liveStream.*`, `liveDashboard.*` |
| MoodBoardController | `/api/v1/mood-boards` | `moodBoard.*` |
| PerformanceReportController | `/api/v1/performance-reports` | `performanceHeatmap.*` (추정) |
| PlatformAutomationController | `/api/v1/platform-automation` | `platformAutomation.*` |
| PortfolioBuilderController | `/api/v1/portfolio-builder` | `portfolioBuilder.*` |
| RevenueAnalyzerController | `/api/v1/revenue-analyzer` | `revenueEnhanced.*` (추정) |
| RevenueForecasterController | `/api/v1/revenue-forecaster` | `revenueForecaster.*` |
| SmartReplyController | `/api/v1/smart-reply` | `smartReply.*` |
| SocialListeningController | `/api/v1/social-listening` | `socialListening.*` |
| SubtitleTranslationController | `/api/v1/subtitle-translations` | `subtitleTranslation.*` |

## F5 — 키는 살아있으나 다른 기능 소유, 전용 UI 없음 (18)

i18n 섹션이 비-wip 기능의 UI에서 실사용 중이라 "죽은 키"가 아니지만,
이 wip 컨트롤러를 위한 화면은 없다. 키 공유로 오판하면 안 되는 케이스.

| 컨트롤러 | base path | 키 실사용처 (비-wip) |
|---|---|---|
| AudienceSegmentController | `/api/v1/audience-segments` | `audience.*` → `AudienceView.vue` (오디언스 CRM) |
| CollaborationBoardController | `/api/v1/board` | `team.*` → `TeamView.vue` |
| CommentSummaryController | `/api/v1/comment-summary` | `comments.*` → 댓글 기능 (`api/comments.ts`, 비-wip) |
| ContentAbAnalyzerController | `/api/v1/content-ab-analyzer` | `abTest.*` → ABTestView (F2 참고) |
| ContentLibraryController | `/api/v1/content-library` | `assets.*` → `AssetsView.vue` |
| ContentRightsController | `/api/v1/content-rights` | `brandSafety.*` (추정 — title "브랜드 안전성 점검") |
| CreatorMilestoneController | `/api/v1/creator-milestones` | `goals.*` → `GoalsView.vue` |
| FanCommunityController | `/api/v1/fan-community` | `audience.*` → AudienceView |
| MediaKitController | `/api/v1/media-kit` | `mediaKit.*` → `BrandDealView.vue`, 단 호출 경로는 `/brand-deals/media-kit` (`api/branddeal.ts:38-50`, **비-wip BrandDealController**). wip `/api/v1/media-kit` 호출 0건 |
| PlatformHealthController | `/api/v1/platform-health` | `channelHealth.*` 공유 추정 |
| PortfolioController | `/api/v1/portfolios` | `portfolio.*` → UGC 캠페인 지원자 포트폴리오 (`CampaignApplicationsView.vue`) |
| ScriptWriterController | `/api/v1/scripts` | `videoSeo.*` → `VideosView.vue` SEO 도구 |
| SentimentAnalyzerController | `/api/v1/sentiment-analyzer` | `commentAdvanced.*` → 댓글 고급 기능 (`/comments/sentiment-trend` 등 비-wip) |
| SponsorshipController | `/api/v1/sponsorships` | `brandDeal.*` → `BrandDealView.vue` (비-wip) |
| TrendPredictorController | `/api/v1/trend-predictor` | `trend.*` → `TrendView.vue` (비-wip) |
| VideoScriptAssistantController | `/api/v1/video-script-assistant` | `videoSeo.*` → VideosView |
| VisualWorkflowController | `/api/v1/workflows` | `workflow.*`/`automation.*` → `AutomationView.vue`, 단 호출 경로는 `/automation/workflows` (`api/automation.ts:79-91`, **비-wip**). wip `/api/v1/workflows` 호출 0건 |
| AiCalendarController | `/api/v1/ai-calendars` | `aiCalendar.*` → `AiCalendarGenerateModal.vue`, 단 모달이 호출하는 것은 `/content-calendar-ai` (F2의 ContentCalendarAiController). `/ai-calendars` 호출 0건 |

## F6 — 프론트 흔적 전무 (3)

| 컨트롤러 | base path | 비고 |
|---|---|---|
| MusicRecommenderController | `/api/v1/music-recommender` | locale 섹션조차 없음 |
| CreatorAcademyController | `/api/v1/academy` | locale 섹션 없음 (`creatorMarketplace.*` 는 Network 쪽으로 배정) |
| FanPollController | `/api/v1/fan-polls` | `fanPoll` 키 없음. `fanReward.*`("팬 리워드")는 별개 기능으로 판단 |

---

## 프론트 관점 요약

- **지금 활성화필 프론트 준비 완료**: SubtitleEditor (F1) 1개뿐.
- **라우트/뷰 연결만 하면 되는 것**: ABTest(라우트 추가), ContentCalendarAi(모달을 리디자인 캘린더로 이식) — 각 반나절 이내 작업.
- **api 만 있는 것**: Agency, Prediction, ScheduleOptimizer — 뷰를 새로 만들어야 한다.
- **나머지 49개(F4+F5+F6)**: 프론트는 사실상 백지. 활성화하려면 뷰부터 새로 만들어야 하므로
  keep-disabled/remove 판정의 프론트 근거로 사용하면 된다.

## 검증 방법 (재현 가능)

```bash
# 55개 목록
grep -rln '@Profile("wip")' backend/onGo-api/src/main/kotlin
# 경로별 프론트 참조 (예: brand-voice)
grep -rn "brand-voice" frontend/src --include='*.ts' --include='*.vue' | grep -v locales
# 라우트 전수
grep -n "path: '" frontend/src/router/index.ts
# 내비게이션 항목
# frontend/src/composables/useNavigation.ts:89-181
```
