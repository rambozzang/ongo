import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/bio/:slug',
    name: 'public-linkbio',
    component: () => import('@/views/PublicLinkBioView.vue'),
    meta: { requiresAuth: false, allowAuthenticated: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/terms',
    name: 'terms',
    component: () => import('@/views/LegalView.vue'),
    meta: { requiresAuth: false, allowAuthenticated: true, document: 'terms' },
  },
  {
    path: '/privacy',
    name: 'privacy',
    component: () => import('@/views/LegalView.vue'),
    meta: { requiresAuth: false, allowAuthenticated: true, document: 'privacy' },
  },
  {
    path: '/refund',
    name: 'refund',
    component: () => import('@/views/LegalView.vue'),
    meta: { requiresAuth: false, allowAuthenticated: true, document: 'refund' },
  },
  {
    path: '/data-deletion',
    name: 'data-deletion',
    component: () => import('@/views/LegalView.vue'),
    meta: { requiresAuth: false, allowAuthenticated: true, document: 'data-deletion' },
  },
  {
    path: '/support',
    name: 'support',
    component: () => import('@/views/LegalView.vue'),
    meta: { requiresAuth: false, allowAuthenticated: true, document: 'support' },
  },
  {
    // 소셜 로그인 콜백. LoginView 가 pathname 에서 provider 를 판별해 처리한다.
    // 이 라우트가 없으면 catch-all 이 /dashboard 로 보내면서 code·state 쿼리가 사라져
    // 로그인이 조용히 실패한다.
    path: '/auth/callback/:provider',
    name: 'oauth-callback',
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
  /*
   * 2026-08 리디자인 셸. 하루 작업 순서(오늘 → 만들기 → 응답 → 확인)로 재편한 7개 화면.
   * 기존 URL은 필요한 경우 리디자인 화면으로 연결하고, 중복 레거시 화면은 유지하지 않는다.
   */
  {
    path: '/',
    component: () => import('@/components/layout/RedesignLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'today',
        name: 'redesign-today',
        component: () => import('@/views/redesign/TodayView.vue'),
        meta: { breadcrumb: '오늘' },
      },
      {
        path: 'compose',
        name: 'redesign-compose',
        component: () => import('@/views/redesign/ComposeView.vue'),
        meta: { breadcrumb: '새 업로드' },
      },
      {
        path: 'inbox-v2',
        name: 'redesign-inbox',
        component: () => import('@/views/redesign/InboxView.vue'),
        meta: { breadcrumb: '인박스' },
      },
      {
        path: 'calendar-v2',
        name: 'redesign-calendar',
        component: () => import('@/views/redesign/CalendarView.vue'),
        meta: { breadcrumb: '캘린더' },
      },
      {
        path: 'performance',
        name: 'redesign-performance',
        component: () => import('@/views/redesign/PerformanceView.vue'),
        meta: { breadcrumb: '성과' },
      },
      {
        path: 'channels-v2',
        name: 'redesign-channels',
        component: () => import('@/views/redesign/ChannelsView.vue'),
        meta: { breadcrumb: '채널' },
      },
      {
        path: 'settings-v2',
        name: 'redesign-settings',
        component: () => import('@/views/redesign/SettingsView.vue'),
        meta: { breadcrumb: '설정' },
      },
    ],
  },
  {
    path: '/',
    component: () => import('@/components/layout/RedesignLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        // 루트 착지점은 리디자인의 '오늘' 이다. /dashboard 는 URL 로 계속 접근 가능하다.
        path: '',
        redirect: '/today',
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        redirect: { name: 'redesign-today' },
      },
      {
        path: 'upload',
        name: 'upload',
        redirect: { name: 'redesign-compose' },
      },
      {
        // 예약 관리는 캘린더의 '리스트' 탭으로 통합됨 — 기존 북마크 URL 유지
        path: 'schedule',
        name: 'schedule',
        redirect: { name: 'redesign-calendar', query: { view: 'list' } },
      },
      {
        path: 'calendar',
        name: 'calendar',
        redirect: { name: 'redesign-calendar' },
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
        redirect: { name: 'redesign-performance' },
      },
      {
        path: 'analytics/compare',
        name: 'video-compare',
        component: () => import('@/views/VideoCompareView.vue'),
        meta: { breadcrumb: '영상 비교' },
      },
      {
        // 댓글 관리는 소통 허브(인박스)의 '댓글' 탭으로 통합됨 — 기존 북마크 URL 유지
        path: 'comments',
        name: 'comments',
        redirect: { name: 'redesign-inbox', query: { tab: 'comments' } },
      },
      {
        path: 'ai',
        name: 'ai',
        component: () => import('@/views/AiView.vue'),
        meta: { breadcrumb: 'AI 도구' },
      },
      {
        path: 'subtitle-editor',
        name: 'subtitle-editor',
        component: () => import('@/views/SubtitleEditorView.vue'),
        meta: { breadcrumb: '자막 에디터' },
      },
      {
        path: 'ab-tests',
        name: 'ab-tests',
        component: () => import('@/views/AbTestView.vue'),
        meta: { breadcrumb: 'A/B 테스트' },
      },
      {
        path: 'channels',
        name: 'channels',
        redirect: { name: 'redesign-channels' },
      },
      {
        path: 'ugc/campaigns',
        name: 'ugc-campaigns',
        component: () => import('@/views/ugc/CampaignListView.vue'),
        meta: { breadcrumb: 'UGC 캠페인' },
      },
      {
        path: 'ugc/campaigns/new',
        name: 'ugc-campaign-new',
        component: () => import('@/views/ugc/CampaignBuilderView.vue'),
        meta: { breadcrumb: 'UGC 캠페인 생성' },
      },
      {
        path: 'ugc/campaigns/:id',
        name: 'ugc-campaign-detail',
        component: () => import('@/views/ugc/CampaignDetailView.vue'),
        meta: { breadcrumb: 'UGC 캠페인 상세' },
      },
      {
        path: 'ugc/campaigns/:id/edit',
        name: 'ugc-campaign-edit',
        component: () => import('@/views/ugc/CampaignBuilderView.vue'),
        meta: { breadcrumb: 'UGC 캠페인 수정' },
      },
      {
        path: 'ugc/campaigns/:id/applications',
        name: 'ugc-campaign-applications',
        component: () => import('@/views/ugc/CampaignApplicationsView.vue'),
        meta: { breadcrumb: 'UGC 지원자 관리' },
      },
      {
        path: 'ugc/invite/:token',
        name: 'ugc-invite',
        component: () => import('@/views/ugc/CampaignInviteView.vue'),
        meta: { breadcrumb: 'UGC 캠페인 초대' },
      },
      {
        path: 'creator/campaigns',
        name: 'creator-campaigns',
        component: () => import('@/views/ugc/CreatorCampaignsView.vue'),
        meta: { breadcrumb: '내 캠페인' },
      },
      {
        path: 'ugc/campaigns/:id/submissions',
        name: 'ugc-campaign-submissions',
        component: () => import('@/views/ugc/CampaignSubmissionsView.vue'),
        meta: { breadcrumb: 'UGC 제출물 검수' },
      },
      {
        path: 'ugc/campaigns/:id/rewards',
        name: 'ugc-campaign-rewards',
        component: () => import('@/views/ugc/CampaignRewardsView.vue'),
        meta: { breadcrumb: 'UGC 성과·정산' },
      },
      {
        path: 'ugc/shorts/prompts',
        name: 'ugc-shorts-prompts',
        component: () => import('@/views/ugc/ShortsPromptsView.vue'),
        meta: { breadcrumb: '쇼츠 프롬프트' },
      },
      {
        path: 'ugc/shorts/templates',
        name: 'ugc-shorts-templates',
        component: () => import('@/views/ugc/ShortsTemplatesView.vue'),
        meta: { breadcrumb: '쇼츠 템플릿' },
      },
      {
        path: 'ugc/shorts/runs',
        name: 'ugc-shorts-runs',
        component: () => import('@/views/ugc/ShortsPipelineView.vue'),
        meta: { breadcrumb: '쇼츠 실행' },
      },
      {
        path: 'ugc/shorts/runs/:id',
        name: 'ugc-shorts-run-detail',
        component: () => import('@/views/ugc/ShortsPipelineDetailView.vue'),
        meta: { breadcrumb: '쇼츠 실행 상세' },
      },
      {
        path: 'creator/campaigns/:id/submit',
        name: 'creator-submit',
        component: () => import('@/views/ugc/CreatorSubmitView.vue'),
        meta: { breadcrumb: '콘텐츠 제출' },
      },
      {
        path: 'subscription',
        name: 'subscription',
        component: () => import('@/views/SubscriptionView.vue'),
        meta: { breadcrumb: '구독 관리' },
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
        redirect: { name: 'redesign-inbox' },
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
        path: 'channel-audit',
        name: 'channel-audit',
        component: () => import('@/views/ChannelAuditView.vue'),
        meta: { breadcrumb: '채널 오디트' },
      },
      {
        path: 'keyword-research',
        name: 'keyword-research',
        component: () => import('@/views/KeywordResearchView.vue'),
        meta: { breadcrumb: '키워드 리서치' },
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
        redirect: { name: 'redesign-settings' },
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/views/AdminView.vue'),
        meta: { breadcrumb: '관리자', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/today',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
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

router.afterEach((to) => {
  // Hook for future page-view analytics
  if (import.meta.env.DEV) {
     
    console.log('[router] navigated to', to.fullPath)
  }
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // 새로고침 시 accessToken이 있지만 user가 없으면 프로필 복원
  if (authStore.accessToken && !authStore.user) {
    await authStore.initialize()
  }

  if (to.meta.requiresAuth === false) {
    if (authStore.isAuthenticated && !to.meta.allowAuthenticated) {
      return next('/today')
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
    return next('/today')
  }

  next()
})

export default router
