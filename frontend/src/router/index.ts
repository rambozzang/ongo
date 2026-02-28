import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/onboarding',
    name: 'onboarding',
    component: () => import('@/views/OnboardingView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/auth/channel-callback',
    name: 'channel-callback',
    component: () => import('@/views/ChannelCallbackView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { breadcrumb: '대시보드' },
      },
      {
        path: 'upload',
        name: 'upload',
        component: () => import('@/views/UploadView.vue'),
        meta: { breadcrumb: '영상 업로드' },
      },
      {
        path: 'schedule',
        name: 'schedule',
        component: () => import('@/views/ScheduleView.vue'),
        meta: { breadcrumb: '예약 관리' },
      },
      {
        path: 'calendar',
        name: 'calendar',
        component: () => import('@/views/CalendarView.vue'),
        meta: { requiresAuth: true, breadcrumb: '캘린더' },
      },
      {
        path: 'revenue',
        name: 'revenue',
        component: () => import('@/views/RevenueView.vue'),
        meta: { requiresAuth: true, breadcrumb: '수익 분석' },
      },
      {
        path: 'videos',
        name: 'videos',
        component: () => import('@/views/VideosView.vue'),
        meta: { breadcrumb: '영상 관리' },
      },
      {
        path: 'videos/:id',
        name: 'video-detail',
        component: () => import('@/views/VideoDetailView.vue'),
        props: true,
        meta: { breadcrumb: '영상 상세' },
      },
      {
        path: 'analytics',
        name: 'analytics',
        component: () => import('@/views/AnalyticsView.vue'),
        meta: { breadcrumb: '통합 분석' },
      },
      {
        path: 'analytics/compare',
        name: 'video-compare',
        component: () => import('@/views/VideoCompareView.vue'),
        meta: { breadcrumb: '영상 비교' },
      },
      {
        path: 'comments',
        name: 'comments',
        component: () => import('@/views/CommentsView.vue'),
        meta: { requiresAuth: true, breadcrumb: '댓글 관리' },
      },
      {
        path: 'ai',
        name: 'ai',
        component: () => import('@/views/AiView.vue'),
        meta: { breadcrumb: 'AI 도구' },
      },
      {
        path: 'channels',
        name: 'channels',
        component: () => import('@/views/ChannelsView.vue'),
        meta: { breadcrumb: '채널 관리' },
      },
      {
        path: 'subscription',
        name: 'subscription',
        component: () => import('@/views/SubscriptionView.vue'),
        meta: { breadcrumb: '구독 관리' },
      },
      {
        path: 'ideas',
        name: 'ideas',
        component: () => import('@/views/IdeasView.vue'),
        meta: { breadcrumb: '아이디어 보드' },
      },
      {
        path: 'team',
        name: 'team',
        component: () => import('@/views/TeamView.vue'),
        meta: { breadcrumb: '팀 관리' },
      },
      {
        path: 'automation',
        name: 'automation',
        component: () => import('@/views/AutomationView.vue'),
        meta: { breadcrumb: '자동화 규칙' },
      },
      {
        path: 'inbox',
        name: 'inbox',
        component: () => import('@/views/InboxView.vue'),
        meta: { breadcrumb: '소셜 인박스' },
      },
      {
        path: 'linkbio',
        name: 'linkbio',
        component: () => import('@/views/LinkBioView.vue'),
        meta: { breadcrumb: '링크인바이오' },
      },
      {
        path: 'templates',
        name: 'templates',
        component: () => import('@/views/TemplatesView.vue'),
        meta: { breadcrumb: '템플릿 라이브러리' },
      },
      {
        path: 'abtest',
        name: 'abtest',
        component: () => import('@/views/AbTestView.vue'),
        meta: { breadcrumb: 'A/B 테스트' },
      },
      {
        path: 'competitor',
        name: 'competitor',
        component: () => import('@/views/CompetitorView.vue'),
        meta: { breadcrumb: '경쟁사 분석' },
      },
      {
        path: 'brandkit',
        name: 'brandkit',
        component: () => import('@/views/BrandKitView.vue'),
        meta: { breadcrumb: '브랜드 키트' },
      },
      {
        path: 'goals',
        name: 'goals',
        component: () => import('@/views/GoalsView.vue'),
        meta: { breadcrumb: '목표 관리' },
      },
      {
        path: 'recycling',
        name: 'recycling',
        component: () => import('@/views/RecyclingView.vue'),
        meta: { breadcrumb: '콘텐츠 재활용' },
      },
      {
        path: 'assets',
        name: 'assets',
        component: () => import('@/views/AssetsView.vue'),
        meta: { breadcrumb: '에셋 라이브러리' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/NotificationsView.vue'),
        meta: { breadcrumb: '알림 센터' },
      },
      {
        path: 'webhooks',
        name: 'webhooks',
        component: () => import('@/views/WebhooksView.vue'),
        meta: { breadcrumb: '웹훅 관리' },
      },
      {
        path: 'trends',
        name: 'trends',
        component: () => import('@/views/TrendView.vue'),
        meta: { breadcrumb: '트렌드 모니터링' },
      },
      {
        path: 'audience',
        name: 'audience',
        component: () => import('@/views/AudienceView.vue'),
        meta: { breadcrumb: '오디언스 CRM' },
      },
      {
        path: 'brand-deals',
        name: 'brand-deals',
        component: () => import('@/views/BrandDealView.vue'),
        meta: { breadcrumb: '브랜드 딜' },
      },
      {
        path: 'activity-log',
        name: 'activity-log',
        component: () => import('@/views/ActivityLogView.vue'),
        meta: { breadcrumb: '활동 로그' },
      },
      {
        path: 'manual',
        name: 'manual',
        component: () => import('@/views/UserManualView.vue'),
        meta: { breadcrumb: '사용자 매뉴얼' },
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/SettingsView.vue'),
        meta: { breadcrumb: '설정' },
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/AdminView.vue'),
        meta: { breadcrumb: '관리자', requiresAdmin: true },
      },
      {
        path: 'content-studio',
        name: 'content-studio',
        component: () => import('@/views/ContentStudioView.vue'),
        meta: { breadcrumb: 'AI 콘텐츠 스튜디오' },
      },
      {
        path: 'prediction',
        name: 'prediction',
        component: () => import('@/views/PredictionView.vue'),
        meta: { breadcrumb: 'AI 성과 예측' },
      },
      {
        path: 'portfolio',
        name: 'portfolio',
        component: () => import('@/views/PortfolioView.vue'),
        meta: { breadcrumb: '크리에이터 포트폴리오' },
      },
      {
        path: 'commerce',
        name: 'commerce',
        component: () => import('@/views/CommerceView.vue'),
        meta: { breadcrumb: '소셜 커머스' },
      },
      {
        path: 'agency',
        name: 'agency',
        component: () => import('@/views/AgencyView.vue'),
        meta: { breadcrumb: '에이전시 대시보드' },
      },
      {
        path: 'ai-calendar',
        name: 'ai-calendar',
        component: () => import('@/views/AiCalendarView.vue'),
        meta: { breadcrumb: 'AI 콘텐츠 캘린더' },
      },
      {
        path: 'workflow-builder',
        name: 'workflow-builder',
        component: () => import('@/views/WorkflowBuilderView.vue'),
        meta: { breadcrumb: '워크플로우 빌더' },
      },
      {
        path: 'client-portal',
        name: 'client-portal',
        component: () => import('@/views/ClientPortalView.vue'),
        meta: { breadcrumb: '클라이언트 포탈' },
      },
      {
        path: 'video-resizer',
        name: 'video-resizer',
        component: () => import('@/views/VideoResizerView.vue'),
        meta: { breadcrumb: 'AI 비디오 리사이저' },
      },
      {
        path: 'subtitle-editor',
        name: 'subtitle-editor',
        component: () => import('@/views/SubtitleEditorView.vue'),
        meta: { breadcrumb: '자막 에디터' },
      },
      {
        path: 'competitor-analysis',
        name: 'competitor-analysis',
        component: () => import('@/views/CompetitorAnalysisView.vue'),
        meta: { breadcrumb: '경쟁사 심층 분석' },
      },
      {
        path: 'hashtag-strategy',
        name: 'hashtag-strategy',
        component: () => import('@/views/HashtagStrategyView.vue'),
        meta: { breadcrumb: '해시태그 전략' },
      },
      {
        path: 'growth-coach',
        name: 'growth-coach',
        component: () => import('@/views/GrowthCoachView.vue'),
        meta: { breadcrumb: '크리에이터 성장 코치' },
      },
      {
        path: 'brand-voice',
        name: 'brand-voice',
        component: () => import('@/views/BrandVoiceView.vue'),
        meta: { breadcrumb: 'AI 브랜드 보이스' },
      },
      {
        path: 'cross-analytics',
        name: 'cross-analytics',
        component: () => import('@/views/CrossAnalyticsView.vue'),
        meta: { breadcrumb: '크로스플랫폼 분석' },
      },
      {
        path: 'copyright-check',
        name: 'copyright-check',
        component: () => import('@/views/CopyrightCheckView.vue'),
        meta: { breadcrumb: '저작권 사전검증' },
      },
      {
        path: 'thumbnail-generator',
        name: 'thumbnail-generator',
        component: () => import('@/views/ThumbnailGeneratorView.vue'),
        meta: { breadcrumb: 'AI 썸네일 생성기' },
      },
      {
        path: 'performance-heatmap',
        name: 'performance-heatmap',
        component: () => import('@/views/PerformanceHeatmapView.vue'),
        meta: { breadcrumb: '성과 히트맵' },
      },
      {
        path: 'live-dashboard',
        name: 'live-dashboard',
        component: () => import('@/views/LiveDashboardView.vue'),
        meta: { breadcrumb: '실시간 대시보드' },
      },
      {
        path: 'revenue-forecaster',
        name: 'revenue-forecaster',
        component: () => import('@/views/RevenueForecasterView.vue'),
        meta: { breadcrumb: '수익 예측기' },
      },
      {
        path: 'content-rewriter',
        name: 'content-rewriter',
        component: () => import('@/views/ContentRewriterView.vue'),
        meta: { breadcrumb: 'AI 콘텐츠 리라이터' },
      },
      {
        path: 'social-listening',
        name: 'social-listening',
        component: () => import('@/views/SocialListeningView.vue'),
        meta: { breadcrumb: '소셜 리스닝' },
      },
      {
        path: 'influencer-match',
        name: 'influencer-match',
        component: () => import('@/views/InfluencerMatchView.vue'),
        meta: { breadcrumb: '인플루언서 매칭' },
      },
      {
        path: 'quality-score',
        name: 'quality-score',
        component: () => import('@/views/QualityScoreView.vue'),
        meta: { breadcrumb: '퀄리티 스코어' },
      },
      {
        path: 'media-kit',
        name: 'media-kit',
        component: () => import('@/views/MediaKitView.vue'),
        meta: { breadcrumb: '미디어킷 생성기' },
      },
      {
        path: 'fan-funding',
        name: 'fan-funding',
        component: () => import('@/views/FanFundingView.vue'),
        meta: { breadcrumb: '팬 펀딩 트래커' },
      },
      {
        path: 'content-series',
        name: 'content-series',
        component: () => import('@/views/ContentSeriesView.vue'),
        meta: { breadcrumb: '콘텐츠 시리즈' },
      },
      {
        path: 'smart-reply',
        name: 'smart-reply',
        component: () => import('@/views/SmartReplyView.vue'),
        meta: { breadcrumb: '스마트 리플라이' },
      },
      {
        path: 'audience-segments',
        name: 'audience-segments',
        component: () => import('@/views/AudienceSegmentView.vue'),
        meta: { breadcrumb: '오디언스 세분화' },
      },
      {
        path: 'content-rights',
        name: 'content-rights',
        component: () => import('@/views/ContentRightsView.vue'),
        meta: { breadcrumb: '콘텐츠 저작권 관리' },
      },
      {
        path: 'creator-academy',
        name: 'creator-academy',
        component: () => import('@/views/CreatorAcademyView.vue'),
        meta: { breadcrumb: '크리에이터 아카데미' },
      },
      {
        path: 'multi-brand-calendar',
        name: 'multi-brand-calendar',
        component: () => import('@/views/MultiBrandCalendarView.vue'),
        meta: { breadcrumb: '다중 브랜드 캘린더' },
      },
      {
        path: 'script-writer',
        name: 'script-writer',
        component: () => import('@/views/ScriptWriterView.vue'),
        meta: { breadcrumb: 'AI 스크립트 작성기' },
      },
      {
        path: 'collaboration-board',
        name: 'collaboration-board',
        component: () => import('@/views/CollaborationBoardView.vue'),
        meta: { breadcrumb: '협업 보드' },
      },
      {
        path: 'sponsorship-tracker',
        name: 'sponsorship-tracker',
        component: () => import('@/views/SponsorshipTrackerView.vue'),
        meta: { breadcrumb: '스폰서십 트래커' },
      },
      {
        path: 'playlist-manager',
        name: 'playlist-manager',
        component: () => import('@/views/PlaylistManagerView.vue'),
        meta: { breadcrumb: '재생목록 관리' },
      },
      {
        path: 'ab-test-results',
        name: 'ab-test-results',
        component: () => import('@/views/ABTestResultView.vue'),
        meta: { breadcrumb: 'A/B 테스트 결과' },
      },
      {
        path: 'fan-community',
        name: 'fan-community',
        component: () => import('@/views/FanCommunityView.vue'),
        meta: { breadcrumb: '팬 커뮤니티' },
      },
      {
        path: 'hashtag-analytics',
        name: 'hashtag-analytics',
        component: () => import('@/views/HashtagAnalyticsView.vue'),
        meta: { breadcrumb: 'AI 해시태그 분석기' },
      },
      {
        path: 'revenue-split',
        name: 'revenue-split',
        component: () => import('@/views/RevenueSplitView.vue'),
        meta: { breadcrumb: '수익 분배 관리' },
      },
      {
        path: 'calendar-insights',
        name: 'calendar-insights',
        component: () => import('@/views/CalendarInsightsView.vue'),
        meta: { breadcrumb: '콘텐츠 캘린더 인사이트' },
      },
      {
        path: 'team-performance',
        name: 'team-performance',
        component: () => import('@/views/TeamPerformanceView.vue'),
        meta: { breadcrumb: '팀 성과 대시보드' },
      },
      {
        path: 'brand-safety',
        name: 'brand-safety',
        component: () => import('@/views/BrandSafetyView.vue'),
        meta: { breadcrumb: '브랜드 안전성 점검' },
      },
      {
        path: 'subtitle-translation',
        name: 'subtitle-translation',
        component: () => import('@/views/SubtitleTranslationView.vue'),
        meta: { breadcrumb: '자동 자막 번역' },
      },
      {
        path: 'sentiment-analyzer',
        name: 'sentiment-analyzer',
        component: () => import('@/views/SentimentAnalyzerView.vue'),
        meta: { breadcrumb: 'AI 감정 분석기' },
      },
      {
        path: 'content-versioning',
        name: 'content-versioning',
        component: () => import('@/views/ContentVersioningView.vue'),
        meta: { breadcrumb: '콘텐츠 버전 관리' },
      },
      {
        path: 'creator-milestone',
        name: 'creator-milestone',
        component: () => import('@/views/CreatorMilestoneView.vue'),
        meta: { breadcrumb: '크리에이터 마일스톤' },
      },
      {
        path: 'algorithm-insights',
        name: 'algorithm-insights',
        component: () => import('@/views/AlgorithmInsightsView.vue'),
        meta: { breadcrumb: '알고리즘 인사이트' },
      },
      {
        path: 'content-calendar-ai',
        name: 'content-calendar-ai',
        component: () => import('@/views/ContentCalendarAiView.vue'),
        meta: { breadcrumb: 'AI 콘텐츠 캘린더' },
      },
      {
        path: 'creator-benchmark',
        name: 'creator-benchmark',
        component: () => import('@/views/CreatorBenchmarkView.vue'),
        meta: { breadcrumb: '크리에이터 벤치마크' },
      },
      {
        path: 'content-cluster',
        name: 'content-cluster',
        component: () => import('@/views/ContentClusterView.vue'),
        meta: { breadcrumb: '콘텐츠 클러스터' },
      },
      {
        path: 'fan-insights',
        name: 'fan-insights',
        component: () => import('@/views/FanInsightsView.vue'),
        meta: { breadcrumb: '팬 인사이트' },
      },
      {
        path: 'content-repurposer',
        name: 'content-repurposer',
        component: () => import('@/views/ContentRepurposerView.vue'),
        meta: { breadcrumb: '콘텐츠 리퍼포징' },
      },
      {
        path: 'audience-overlap',
        name: 'audience-overlap',
        component: () => import('@/views/AudienceOverlapView.vue'),
        meta: { breadcrumb: '오디언스 오버랩' },
      },
      {
        path: 'video-seo',
        name: 'video-seo',
        component: () => import('@/views/VideoSeoView.vue'),
        meta: { breadcrumb: '비디오 SEO 최적화' },
      },
      {
        path: 'mood-board',
        name: 'mood-board',
        component: () => import('@/views/MoodBoardView.vue'),
        meta: { breadcrumb: '크리에이터 무드보드' },
      },
      {
        path: 'thumbnail-ab-test',
        name: 'thumbnail-ab-test',
        component: () => import('@/views/ThumbnailAbTestView.vue'),
        meta: { breadcrumb: '썸네일 A/B 테스트' },
      },
      {
        path: 'revenue-goal',
        name: 'revenue-goal',
        component: () => import('@/views/RevenueGoalView.vue'),
        meta: { breadcrumb: '수익 목표' },
      },
      {
        path: 'comment-summary',
        name: 'comment-summary',
        component: () => import('@/views/CommentSummaryView.vue'),
        meta: { breadcrumb: 'AI 댓글 요약' },
      },
      {
        path: 'platform-health',
        name: 'platform-health',
        component: () => import('@/views/PlatformHealthView.vue'),
        meta: { breadcrumb: '플랫폼 건강 점수' },
      },
      {
        path: 'subtitle-generator',
        name: 'subtitle-generator',
        component: () => import('@/views/SubtitleGeneratorView.vue'),
        meta: { breadcrumb: 'AI 자막 생성기' },
      },
      {
        path: 'content-library',
        name: 'content-library',
        component: () => import('@/views/ContentLibraryView.vue'),
        meta: { breadcrumb: '콘텐츠 라이브러리' },
      },
      {
        path: 'fan-poll',
        name: 'fan-poll',
        component: () => import('@/views/FanPollView.vue'),
        meta: { breadcrumb: '팬 투표' },
      },
      {
        path: 'creator-network',
        name: 'creator-network',
        component: () => import('@/views/CreatorNetworkView.vue'),
        meta: { breadcrumb: '크리에이터 네트워크' },
      },
      {
        path: 'trend-predictor',
        name: 'trend-predictor',
        component: () => import('@/views/TrendPredictorView.vue'),
        meta: { breadcrumb: 'AI 트렌드 예측기' },
      },
      {
        path: 'performance-report',
        name: 'performance-report',
        component: () => import('@/views/PerformanceReportView.vue'),
        meta: { breadcrumb: '콘텐츠 성과 보고서' },
      },
      {
        path: 'live-stream',
        name: 'live-stream',
        component: () => import('@/views/LiveStreamView.vue'),
        meta: { breadcrumb: '라이브 스트림 관리' },
      },
      {
        path: 'creator-marketplace',
        name: 'creator-marketplace',
        component: () => import('@/views/CreatorMarketplaceView.vue'),
        meta: { breadcrumb: '크리에이터 마켓플레이스' },
      },
      {
        path: 'music-recommender',
        name: 'music-recommender',
        component: () => import('@/views/MusicRecommenderView.vue'),
        meta: { breadcrumb: 'AI 음악 추천' },
      },
      {
        path: 'content-ab-analyzer',
        name: 'content-ab-analyzer',
        component: () => import('@/views/ContentAbAnalyzerView.vue'),
        meta: { breadcrumb: '콘텐츠 A/B 분석기' },
      },
      {
        path: 'fan-reward',
        name: 'fan-reward',
        component: () => import('@/views/FanRewardView.vue'),
        meta: { breadcrumb: '팬 리워드' },
      },
      {
        path: 'platform-automation-pro',
        name: 'platform-automation-pro',
        component: () => import('@/views/PlatformAutomationView.vue'),
        meta: { breadcrumb: '플랫폼 자동화' },
      },
      {
        path: 'video-script-assistant',
        name: 'video-script-assistant',
        component: () => import('@/views/VideoScriptAssistantView.vue'),
        meta: { breadcrumb: 'AI 스크립트 어시스턴트' },
      },
      {
        path: 'channel-health',
        name: 'channel-health',
        component: () => import('@/views/ChannelHealthView.vue'),
        meta: { breadcrumb: '채널 건강도' },
      },
      {
        path: 'content-translator',
        name: 'content-translator',
        component: () => import('@/views/ContentTranslatorView.vue'),
        meta: { breadcrumb: '콘텐츠 번역기' },
      },
      {
        path: 'revenue-analyzer',
        name: 'revenue-analyzer',
        component: () => import('@/views/RevenueAnalyzerView.vue'),
        meta: { breadcrumb: '수익 분석기' },
      },
      {
        path: 'schedule-optimizer',
        name: 'schedule-optimizer',
        component: () => import('@/views/ScheduleOptimizerView.vue'),
        meta: { breadcrumb: 'AI 일정 최적화' },
      },
      {
        path: 'portfolio-builder',
        name: 'portfolio-builder',
        component: () => import('@/views/PortfolioBuilderView.vue'),
        meta: { breadcrumb: '포트폴리오 빌더' },
      },
      {
        path: 'fan-segment-campaign',
        name: 'fan-segment-campaign',
        component: () => import('@/views/FanSegmentCampaignView.vue'),
        meta: { breadcrumb: '팬 세그먼트 캠페인' },
      },
      {
        path: 'content-funnel',
        name: 'content-funnel',
        component: () => import('@/views/ContentFunnelView.vue'),
        meta: { breadcrumb: '콘텐츠 퍼널 분석' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 배포 후 이전 JS 청크가 삭제되어 동적 import 실패 시 자동 새로고침
router.onError((error, to) => {
  if (
    error.message.includes('Failed to fetch dynamically imported module') ||
    error.message.includes('Importing a module script failed')
  ) {
    // 무한 리로드 방지: 세션당 1회만 시도
    const reloadKey = `chunk-reload:${to.fullPath}`
    if (!sessionStorage.getItem(reloadKey)) {
      sessionStorage.setItem(reloadKey, '1')
      window.location.assign(to.fullPath)
    }
  }
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // 새로고침 시 accessToken이 있지만 user가 없으면 프로필 복원
  if (authStore.accessToken && !authStore.user) {
    await authStore.initialize()
  }

  if (to.meta.requiresAuth === false) {
    if (authStore.isAuthenticated) {
      return next('/dashboard')
    }
    return next()
  }

  if ((to.meta.requiresAuth ?? true) && !authStore.isAuthenticated) {
    return next('/login')
  }

  if (
    authStore.user &&
    !authStore.user.onboardingCompleted &&
    to.name !== 'onboarding' &&
    to.name !== 'channel-callback'
  ) {
    return next('/onboarding')
  }

  if (to.meta.requiresAdmin && authStore.user?.role !== 'ADMIN') {
    return next('/dashboard')
  }

  next()
})

export default router
