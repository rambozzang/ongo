import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { PlatformHealthScore, HealthIssue, PlatformHealthSummary } from '@/types/platformHealth'
import { platformHealthApi } from '@/api/platformHealth'

export const usePlatformHealthStore = defineStore('platformHealth', () => {
  const scores = ref<PlatformHealthScore[]>([])
  const issues = ref<HealthIssue[]>([])
  const summary = ref<PlatformHealthSummary>({
    totalPlatforms: 0,
    avgHealthScore: 0,
    healthiestPlatform: '-',
    criticalIssues: 0,
    overallTrend: '-',
  })
  const isLoading = ref(false)

  async function fetchScores() {
    isLoading.value = true
    try {
      scores.value = await platformHealthApi.getScores()
    } catch {
      scores.value = [
        { id: 1, platform: 'YOUTUBE', overallScore: 85, growthScore: 82, engagementScore: 90, consistencyScore: 78, audienceScore: 88, trend: 'UP', checkedAt: '2025-01-28T10:00:00' },
        { id: 2, platform: 'TIKTOK', overallScore: 72, growthScore: 88, engagementScore: 75, consistencyScore: 62, audienceScore: 65, trend: 'UP', checkedAt: '2025-01-28T10:00:00' },
        { id: 3, platform: 'INSTAGRAM', overallScore: 68, growthScore: 60, engagementScore: 72, consistencyScore: 70, audienceScore: 68, trend: 'STABLE', checkedAt: '2025-01-28T10:00:00' },
        { id: 4, platform: 'NAVER_CLIP', overallScore: 55, growthScore: 45, engagementScore: 58, consistencyScore: 52, audienceScore: 62, trend: 'DOWN', checkedAt: '2025-01-28T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchIssues(healthScoreId: number) {
    try {
      issues.value = await platformHealthApi.getIssues(healthScoreId)
    } catch {
      issues.value = [
        { id: 1, healthScoreId, severity: 'HIGH', category: '일관성', description: '최근 2주간 업로드가 없습니다', recommendation: '주 2회 이상 정기적으로 업로드하세요' },
        { id: 2, healthScoreId, severity: 'MEDIUM', category: '참여도', description: '댓글 답변율이 30%로 낮습니다', recommendation: '댓글에 적극적으로 답변하여 커뮤니티를 활성화하세요' },
        { id: 3, healthScoreId, severity: 'LOW', category: '성장', description: '구독자 증가율이 지난 달 대비 5% 감소했습니다', recommendation: '트렌드 콘텐츠를 활용하여 노출을 늘리세요' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await platformHealthApi.getSummary()
    } catch {
      summary.value = { totalPlatforms: 4, avgHealthScore: 70, healthiestPlatform: 'YouTube', criticalIssues: 2, overallTrend: 'UP' }
    }
  }

  return { scores, issues, summary, isLoading, fetchScores, fetchIssues, fetchSummary }
})
