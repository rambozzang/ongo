import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  PredictionResult,
  HeatmapCell,
  OptimalTimeRecommendation,
  TitleSuggestion,
  TagSuggestion,
  CompetitorComparison,
  PredictionHistoryItem,
  PredictionRequest,
  PredictionSummary,
} from '@/types/prediction'
import { predictionApi } from '@/api/prediction'

// ── Mock data ────────────────────────────────────────────────────────

function generateMockHeatmap(): HeatmapCell[] {
  const cells: HeatmapCell[] = []
  for (let day = 0; day < 7; day++) {
    for (let hour = 0; hour < 24; hour++) {
      let score = Math.random() * 40 + 10
      // Peak hours: weekdays 18-21, weekends 10-14
      if (day >= 1 && day <= 5 && hour >= 18 && hour <= 21) score += 40
      if ((day === 0 || day === 6) && hour >= 10 && hour <= 14) score += 35
      if (hour >= 12 && hour <= 13) score += 15
      cells.push({ dayOfWeek: day, hour, reachScore: Math.min(100, Math.round(score)) })
    }
  }
  return cells
}

function generateMockHistory(): PredictionHistoryItem[] {
  const platforms = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'] as const
  const titles = [
    '제주도 브이로그 | 3박4일 여행기',
    '초간단 파스타 레시피 5분 완성',
    '서울 핫플 카페 TOP 10',
    '운동 루틴 공개 | 홈트레이닝',
    '신상 화장품 리뷰 하울',
    '일상 브이로그 | 직장인의 하루',
  ]
  return titles.map((title, i) => {
    const predicted = Math.round(Math.random() * 50000 + 5000)
    const variance = (Math.random() - 0.3) * 0.4
    const actual = Math.round(predicted * (1 + variance))
    const predictedLikes = Math.round(predicted * 0.04)
    const actualLikes = Math.round(actual * 0.04 * (1 + (Math.random() - 0.5) * 0.3))
    const predictedEng = +(Math.random() * 5 + 2).toFixed(1)
    const actualEng = +(predictedEng * (1 + (Math.random() - 0.5) * 0.3)).toFixed(1)
    const accuracy = Math.round(100 - Math.abs((actual - predicted) / predicted) * 100)
    return {
      id: i + 1,
      videoId: 100 + i,
      videoTitle: title,
      platform: platforms[i % platforms.length],
      predictedViews: predicted,
      actualViews: actual,
      predictedLikes,
      actualLikes,
      predictedEngagement: predictedEng,
      actualEngagement: actualEng,
      accuracyScore: Math.max(50, Math.min(99, accuracy)),
      createdAt: new Date(Date.now() - i * 86400000 * 3).toISOString(),
    }
  })
}

function generateMockCompetitors(): CompetitorComparison[] {
  return [
    {
      competitorTitle: '여행 브이로그 | 오사카 3일',
      competitorViews: 45000,
      competitorLikes: 2100,
      competitorEngagement: 4.7,
      yourPredictedViews: 52000,
      yourPredictedLikes: 2800,
      yourPredictedEngagement: 5.4,
      advantage: 'ahead',
    },
    {
      competitorTitle: '일본 여행 꿀팁 모음',
      competitorViews: 78000,
      competitorLikes: 5200,
      competitorEngagement: 6.7,
      yourPredictedViews: 52000,
      yourPredictedLikes: 2800,
      yourPredictedEngagement: 5.4,
      advantage: 'behind',
    },
    {
      competitorTitle: '도쿄 맛집 투어',
      competitorViews: 51000,
      competitorLikes: 2900,
      competitorEngagement: 5.7,
      yourPredictedViews: 52000,
      yourPredictedLikes: 2800,
      yourPredictedEngagement: 5.4,
      advantage: 'similar',
    },
  ]
}

export const usePredictionStore = defineStore('prediction', () => {
  const predictionResult = ref<PredictionResult | null>(null)
  const heatmapData = ref<HeatmapCell[]>([])
  const optimalTimes = ref<OptimalTimeRecommendation[]>([])
  const titleSuggestions = ref<TitleSuggestion[]>([])
  const tagSuggestions = ref<TagSuggestion[]>([])
  const competitors = ref<CompetitorComparison[]>([])
  const history = ref<PredictionHistoryItem[]>([])
  const summary = ref<PredictionSummary | null>(null)

  const loading = ref(false)
  const heatmapLoading = ref(false)
  const historyLoading = ref(false)
  const predicting = ref(false)

  async function fetchHeatmap(platform?: string) {
    heatmapLoading.value = true
    try {
      heatmapData.value = await predictionApi.getReachHeatmap(platform)
    } catch {
      // Use mock data on failure
      heatmapData.value = generateMockHeatmap()
    } finally {
      heatmapLoading.value = false
    }
  }

  async function fetchOptimalTimes(platform?: string) {
    try {
      optimalTimes.value = await predictionApi.getOptimalTimes(platform)
    } catch {
      // Mock optimal times
      optimalTimes.value = [
        { dayOfWeek: 3, hour: 19, expectedReach: 92, reason: '수요일 저녁 시청자 활성도 최고' },
        { dayOfWeek: 5, hour: 20, expectedReach: 88, reason: '금요일 저녁 주말 앞둔 시청 증가' },
        { dayOfWeek: 0, hour: 11, expectedReach: 85, reason: '일요일 오전 여유 시간대' },
      ]
    }
  }

  async function predict(request: PredictionRequest) {
    predicting.value = true
    try {
      predictionResult.value = await predictionApi.predict(request)
    } catch {
      // Mock prediction
      predictionResult.value = {
        id: Date.now(),
        videoId: request.videoId,
        title: request.title,
        platform: request.platform,
        metrics: {
          expectedViews: Math.round(Math.random() * 80000 + 10000),
          expectedLikes: Math.round(Math.random() * 4000 + 500),
          expectedComments: Math.round(Math.random() * 300 + 50),
          expectedShares: Math.round(Math.random() * 200 + 30),
          engagementRate: +(Math.random() * 6 + 2).toFixed(1),
          confidenceScore: +(Math.random() * 20 + 75).toFixed(0),
        },
        optimalUploadTime: '수요일 19:00',
        tags: request.tags,
        createdAt: new Date().toISOString(),
      }
    } finally {
      predicting.value = false
    }
  }

  async function fetchTitleSuggestions(title: string, platform?: string) {
    try {
      titleSuggestions.value = await predictionApi.getTitleSuggestions(title, platform)
    } catch {
      titleSuggestions.value = [
        { original: title, suggested: `${title} | 꿀팁 대방출`, expectedImprovement: 23, reason: '구체적 키워드 추가로 CTR 향상' },
        { original: title, suggested: `${title} (2025 최신)`, expectedImprovement: 18, reason: '연도 표기로 최신성 강조' },
        { original: title, suggested: `${title} 완벽 가이드`, expectedImprovement: 15, reason: '포괄적 콘텐츠 기대감 유발' },
      ]
    }
  }

  async function fetchTagSuggestions(tags: string[], platform?: string) {
    try {
      tagSuggestions.value = await predictionApi.getTagSuggestions(tags, platform)
    } catch {
      tagSuggestions.value = [
        { tag: '#브이로그', trendScore: 92, relevanceScore: 88, competitionLevel: 'high' },
        { tag: '#일상', trendScore: 85, relevanceScore: 82, competitionLevel: 'high' },
        { tag: '#여행꿀팁', trendScore: 78, relevanceScore: 90, competitionLevel: 'medium' },
        { tag: '#맛집투어', trendScore: 75, relevanceScore: 72, competitionLevel: 'medium' },
        { tag: '#숏폼', trendScore: 95, relevanceScore: 65, competitionLevel: 'low' },
        { tag: '#데일리', trendScore: 70, relevanceScore: 80, competitionLevel: 'low' },
      ]
    }
  }

  async function fetchCompetitors(videoId?: number) {
    try {
      if (videoId) {
        competitors.value = await predictionApi.getCompetitorComparison(videoId)
      } else {
        competitors.value = generateMockCompetitors()
      }
    } catch {
      competitors.value = generateMockCompetitors()
    }
  }

  async function fetchHistory() {
    historyLoading.value = true
    try {
      history.value = await predictionApi.getHistory()
    } catch {
      history.value = generateMockHistory()
    } finally {
      historyLoading.value = false
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await predictionApi.getSummary()
    } catch {
      const mockHistory = generateMockHistory()
      const avgAcc = mockHistory.reduce((sum, h) => sum + h.accuracyScore, 0) / mockHistory.length
      summary.value = {
        totalPredictions: 24,
        avgAccuracy: Math.round(avgAcc),
        bestAccuracy: Math.max(...mockHistory.map((h) => h.accuracyScore)),
        recentPredictions: mockHistory.slice(0, 3),
      }
    }
  }

  async function fetchAll() {
    loading.value = true
    try {
      await Promise.allSettled([
        fetchHeatmap(),
        fetchOptimalTimes(),
        fetchHistory(),
        fetchSummary(),
        fetchCompetitors(),
        fetchTagSuggestions([]),
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    predictionResult,
    heatmapData,
    optimalTimes,
    titleSuggestions,
    tagSuggestions,
    competitors,
    history,
    summary,
    loading,
    heatmapLoading,
    historyLoading,
    predicting,
    fetchHeatmap,
    fetchOptimalTimes,
    predict,
    fetchTitleSuggestions,
    fetchTagSuggestions,
    fetchCompetitors,
    fetchHistory,
    fetchSummary,
    fetchAll,
  }
})
