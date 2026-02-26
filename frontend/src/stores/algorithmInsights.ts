import { defineStore } from 'pinia'
import { ref } from 'vue'
import { algorithmInsightsApi } from '@/api/algorithmInsights'
import type {
  AlgorithmInsight,
  AlgorithmScore,
  AlgorithmChange,
  AlgorithmInsightsSummary,
} from '@/types/algorithmInsights'

export const useAlgorithmInsightsStore = defineStore('algorithmInsights', () => {
  const insights = ref<AlgorithmInsight[]>([])
  const scores = ref<AlgorithmScore[]>([])
  const changes = ref<AlgorithmChange[]>([])
  const summary = ref<AlgorithmInsightsSummary>({
    avgOverallScore: 0,
    bestPlatform: '',
    worstFactor: '',
    recentChanges: 0,
    improvementSuggestions: 0,
  })
  const isLoading = ref(false)

  const mockInsights: AlgorithmInsight[] = [
    { id: 1, platform: 'YOUTUBE', factor: '시청 지속율', importance: 95, currentScore: 78, recommendation: '영상 초반 15초 내에 핵심 내용을 미리 보여주세요', category: 'CONTENT', trend: 'UP', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 2, platform: 'YOUTUBE', factor: 'CTR (클릭률)', importance: 90, currentScore: 65, recommendation: '썸네일에 얼굴 표정과 큰 텍스트를 포함하세요', category: 'METADATA', trend: 'STABLE', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 3, platform: 'YOUTUBE', factor: '댓글 참여도', importance: 75, currentScore: 82, recommendation: '영상 중간에 질문을 던져 댓글 참여를 유도하세요', category: 'ENGAGEMENT', trend: 'UP', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 4, platform: 'YOUTUBE', factor: '업로드 일관성', importance: 80, currentScore: 55, recommendation: '주 3회 이상 일정한 시간에 업로드하세요', category: 'CONSISTENCY', trend: 'DOWN', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 5, platform: 'TIKTOK', factor: '영상 완주율', importance: 98, currentScore: 85, recommendation: '15-30초 이내로 콘텐츠를 구성하세요', category: 'CONTENT', trend: 'UP', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 6, platform: 'TIKTOK', factor: '트렌딩 사운드 사용', importance: 85, currentScore: 70, recommendation: '인기 사운드를 활용하면 노출이 크게 증가합니다', category: 'METADATA', trend: 'STABLE', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 7, platform: 'INSTAGRAM', factor: '릴스 공유 수', importance: 92, currentScore: 60, recommendation: '공유하고 싶은 정보성/유머 콘텐츠를 만드세요', category: 'ENGAGEMENT', trend: 'DOWN', updatedAt: '2025-03-10T10:00:00Z' },
    { id: 8, platform: 'INSTAGRAM', factor: '팔로워 상호작용', importance: 78, currentScore: 72, recommendation: '스토리 투표/퀴즈로 팔로워 참여를 높이세요', category: 'AUDIENCE', trend: 'STABLE', updatedAt: '2025-03-10T10:00:00Z' },
  ]

  const mockScores: AlgorithmScore[] = [
    { platform: 'YOUTUBE', overallScore: 72, contentScore: 78, engagementScore: 82, metadataScore: 65, consistencyScore: 55, audienceScore: 75, updatedAt: '2025-03-10T10:00:00Z' },
    { platform: 'TIKTOK', overallScore: 79, contentScore: 85, engagementScore: 76, metadataScore: 70, consistencyScore: 82, audienceScore: 80, updatedAt: '2025-03-10T10:00:00Z' },
    { platform: 'INSTAGRAM', overallScore: 68, contentScore: 72, engagementScore: 60, metadataScore: 68, consistencyScore: 70, audienceScore: 72, updatedAt: '2025-03-10T10:00:00Z' },
  ]

  const mockChanges: AlgorithmChange[] = [
    { id: 1, platform: 'YOUTUBE', title: '쇼츠 알고리즘 강화', description: '유튜브가 쇼츠 콘텐츠의 노출을 크게 늘렸습니다. 기존 긴 영상 대비 쇼츠의 도달률이 2배 이상 증가했습니다.', impact: 'HIGH', affectedAreas: ['쇼츠', '도달률', '노출'], detectedAt: '2025-03-08T10:00:00Z', recommendation: '주 2-3개의 쇼츠를 추가로 업로드하세요' },
    { id: 2, platform: 'TIKTOK', title: '검색 기반 노출 확대', description: '틱톡이 키워드 검색 기반 콘텐츠 추천을 강화했습니다. SEO 최적화가 중요해졌습니다.', impact: 'MEDIUM', affectedAreas: ['검색', 'SEO', '해시태그'], detectedAt: '2025-03-05T10:00:00Z', recommendation: '영상 설명에 주요 키워드를 자연스럽게 포함하세요' },
    { id: 3, platform: 'INSTAGRAM', title: '릴스 추천 알고리즘 변경', description: '인스타그램 릴스에서 원본 콘텐츠를 재게시물보다 우선 추천하기 시작했습니다.', impact: 'LOW', affectedAreas: ['릴스', '원본 콘텐츠', '추천'], detectedAt: '2025-03-01T10:00:00Z', recommendation: '다른 플랫폼 콘텐츠를 그대로 올리지 말고 인스타그램에 맞게 편집하세요' },
  ]

  const mockSummary: AlgorithmInsightsSummary = {
    avgOverallScore: 73,
    bestPlatform: 'TIKTOK',
    worstFactor: '업로드 일관성',
    recentChanges: 3,
    improvementSuggestions: 8,
  }

  async function fetchInsights(platform?: string) {
    isLoading.value = true
    try {
      insights.value = await algorithmInsightsApi.getInsights(platform)
    } catch {
      insights.value = platform
        ? mockInsights.filter((i) => i.platform === platform)
        : mockInsights
    } finally {
      isLoading.value = false
    }
  }

  async function fetchScores() {
    try {
      scores.value = await algorithmInsightsApi.getScores()
    } catch {
      scores.value = mockScores
    }
  }

  async function fetchChanges() {
    try {
      changes.value = await algorithmInsightsApi.getChanges()
    } catch {
      changes.value = mockChanges
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await algorithmInsightsApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    insights, scores, changes, summary, isLoading,
    fetchInsights, fetchScores, fetchChanges, fetchSummary,
  }
})
