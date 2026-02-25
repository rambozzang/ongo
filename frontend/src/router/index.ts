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
