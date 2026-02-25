import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  CoachSession,
  GrowthGoal,
  WeeklyReport,
  ActionItem,
  GrowthInsight,
} from '@/types/growthCoach'
import { growthCoachApi } from '@/api/growthCoach'

// ── Mock data ────────────────────────────────────────────────────────

function generateMockSession(): CoachSession {
  const goals: GrowthGoal[] = [
    { id: 1, type: 'SUBSCRIBERS', targetValue: 10000, currentValue: 7800, deadline: '2024-06-30', progress: 78 },
    { id: 2, type: 'VIEWS', targetValue: 500000, currentValue: 320000, deadline: '2024-03-31', progress: 64 },
    { id: 3, type: 'ENGAGEMENT', targetValue: 12, currentValue: 8.5, deadline: '2024-04-30', progress: 71 },
    { id: 4, type: 'UPLOADS', targetValue: 50, currentValue: 38, deadline: '2024-06-30', progress: 76 },
  ]

  const actionItems: ActionItem[] = [
    { id: 1, title: '숏츠 콘텐츠 주 3회 업로드', description: '숏폼 콘텐츠를 통해 신규 구독자 유입을 늘리세요.', priority: 'HIGH', status: 'IN_PROGRESS', category: '콘텐츠', estimatedImpact: '구독자 +500/월', dueDate: '2024-02-15' },
    { id: 2, title: '댓글 답변율 90% 이상 유지', description: '시청자와의 소통을 강화하여 참여도를 높이세요.', priority: 'HIGH', status: 'TODO', category: '소통', estimatedImpact: '참여율 +2%' },
    { id: 3, title: '썸네일 A/B 테스트 실행', description: '상위 5개 영상의 썸네일을 변경하여 CTR을 개선하세요.', priority: 'MEDIUM', status: 'TODO', category: '최적화', estimatedImpact: 'CTR +1.5%' },
    { id: 4, title: '콜라보 영상 기획', description: '비슷한 규모의 크리에이터와 콜라보를 통해 채널을 확장하세요.', priority: 'MEDIUM', status: 'DONE', category: '성장', estimatedImpact: '구독자 +300' },
    { id: 5, title: '업로드 시간 최적화', description: '분석 결과 화요일/금요일 오후 6시가 최적 시간대입니다.', priority: 'LOW', status: 'TODO', category: '최적화', estimatedImpact: '조회수 +10%' },
  ]

  const currentReport: WeeklyReport = {
    id: 1,
    weekStart: '2024-02-05',
    weekEnd: '2024-02-11',
    summary: '이번 주는 구독자 성장률이 전주 대비 15% 증가했습니다. 숏츠 콘텐츠가 신규 유입에 크게 기여했으며, 전체 참여율도 소폭 상승했습니다.',
    highlights: [
      '구독자 +320명 (전주 대비 +15%)',
      '숏츠 평균 조회수 45,000회 달성',
      '댓글 답변율 85% 기록',
    ],
    concerns: [
      '긴 영상(10분 이상) 평균 시청 시간 감소',
      'Instagram Reels 참여율 하락 추세',
    ],
    subscriberGrowth: 320,
    viewsGrowth: 48000,
    engagementChange: 1.2,
    topContent: [
      { title: '서울 맛집 TOP 5', views: 89000, platform: 'youtube' },
      { title: '카페 브이로그', views: 128000, platform: 'tiktok' },
      { title: '주말 루틴', views: 32000, platform: 'instagram' },
    ],
    actionItems,
    overallScore: 78,
    generatedAt: new Date().toISOString(),
  }

  const pastReports: WeeklyReport[] = [
    {
      id: 2,
      weekStart: '2024-01-29',
      weekEnd: '2024-02-04',
      summary: '안정적인 성장세를 유지했습니다. 콜라보 영상이 좋은 성과를 보였습니다.',
      highlights: ['구독자 +280명', '콜라보 영상 조회수 120,000회'],
      concerns: ['업로드 빈도가 목표 대비 부족'],
      subscriberGrowth: 280,
      viewsGrowth: 42000,
      engagementChange: 0.5,
      topContent: [{ title: '콜라보 영상', views: 120000, platform: 'youtube' }],
      actionItems: [],
      overallScore: 72,
      generatedAt: new Date(Date.now() - 86400000 * 7).toISOString(),
    },
  ]

  const insights: GrowthInsight[] = [
    { id: 1, title: '숏폼 콘텐츠 성장 가속', description: '최근 3주간 숏폼 콘텐츠의 신규 구독자 기여도가 60%를 차지하고 있습니다.', impact: 'HIGH', category: '콘텐츠 전략', actionable: true, suggestedAction: '숏폼 업로드 빈도를 주 2회에서 4회로 늘리세요.', createdAt: new Date().toISOString() },
    { id: 2, title: '화요일/금요일 최적 업로드', description: '채널 분석 결과 화요일과 금요일 18:00-20:00에 업로드한 영상이 평균 35% 더 높은 조회수를 기록합니다.', impact: 'MEDIUM', category: '업로드 전략', actionable: true, suggestedAction: '예약 발행을 화요일/금요일로 설정하세요.', createdAt: new Date(Date.now() - 86400000 * 2).toISOString() },
    { id: 3, title: '오디언스 연령층 변화', description: '18-24세 시청자 비율이 10%p 증가하여 현재 채널의 주요 타겟층이 되었습니다.', impact: 'MEDIUM', category: '오디언스', actionable: false, createdAt: new Date(Date.now() - 86400000 * 5).toISOString() },
    { id: 4, title: '매출 성장 기회', description: '구독자 10,000명 달성 시 YouTube 파트너 프로그램 수익이 예상보다 30% 높을 수 있습니다.', impact: 'LOW', category: '수익화', actionable: true, suggestedAction: '구독자 10K 달성에 집중하세요.', createdAt: new Date(Date.now() - 86400000 * 3).toISOString() },
  ]

  return {
    goals,
    currentReport,
    pastReports,
    insights,
    overallGrowthRate: 12.5,
  }
}

export const useGrowthCoachStore = defineStore('growthCoach', () => {
  const session = ref<CoachSession | null>(null)
  const loading = ref(false)
  const generatingReport = ref(false)
  const activeTab = ref<'overview' | 'goals' | 'reports' | 'insights'>('overview')

  const goals = computed(() => session.value?.goals ?? [])
  const currentReport = computed(() => session.value?.currentReport ?? null)
  const insights = computed(() => session.value?.insights ?? [])
  const highImpactInsights = computed(() =>
    insights.value.filter((i) => i.impact === 'HIGH'),
  )
  const actionableInsights = computed(() =>
    insights.value.filter((i) => i.actionable),
  )
  const overallProgress = computed(() => {
    if (goals.value.length === 0) return 0
    return Math.round(goals.value.reduce((sum, g) => sum + g.progress, 0) / goals.value.length)
  })

  async function fetchSession() {
    loading.value = true
    try {
      session.value = await growthCoachApi.getSession()
    } catch {
      session.value = generateMockSession()
    } finally {
      loading.value = false
    }
  }

  async function generateReport() {
    generatingReport.value = true
    try {
      const report = await growthCoachApi.generateReport()
      if (session.value) {
        if (session.value.currentReport) {
          session.value.pastReports.unshift(session.value.currentReport)
        }
        session.value.currentReport = report
      }
    } catch {
      // Mock: 기존 리포트 유지
    } finally {
      generatingReport.value = false
    }
  }

  async function updateActionStatus(reportId: number, actionId: number, status: ActionItem['status']) {
    try {
      await growthCoachApi.updateActionItem(reportId, actionId, { status })
    } catch {
      // 로컬 업데이트
    }
    if (session.value?.currentReport) {
      const item = session.value.currentReport.actionItems.find((a) => a.id === actionId)
      if (item) item.status = status
    }
  }

  return {
    session,
    loading,
    generatingReport,
    activeTab,
    goals,
    currentReport,
    insights,
    highImpactInsights,
    actionableInsights,
    overallProgress,
    fetchSession,
    generateReport,
    updateActionStatus,
  }
})
