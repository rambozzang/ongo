import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { trendPredictorApi } from '@/api/trendPredictor'
import type { TrendPrediction, TrendTopic, TrendPredictorSummary } from '@/types/trendPredictor'

export const useTrendPredictorStore = defineStore('trendPredictor', () => {
  const predictions = ref<TrendPrediction[]>([])
  const topics = ref<TrendTopic[]>([])
  const summary = ref<TrendPredictorSummary | null>(null)
  const loading = ref(false)

  const risingTrends = computed(() => predictions.value.filter(p => p.direction === 'RISING'))
  const decliningTrends = computed(() => predictions.value.filter(p => p.direction === 'DECLINING'))

  async function fetchPredictions(category?: string) {
    loading.value = true
    try {
      predictions.value = await trendPredictorApi.getPredictions(category)
    } catch {
      predictions.value = [
        { id: 1, keyword: 'AI 활용법', category: '테크', platform: 'YOUTUBE', currentScore: 78, predictedScore: 92, confidence: 0.87, direction: 'RISING', peakDate: '2025-04-15', createdAt: '2025-03-10T09:00:00Z' },
        { id: 2, keyword: '봄 패션', category: '뷰티/패션', platform: 'INSTAGRAM', currentScore: 65, predictedScore: 88, confidence: 0.82, direction: 'RISING', peakDate: '2025-04-01', createdAt: '2025-03-11T10:00:00Z' },
        { id: 3, keyword: '홈트레이닝', category: '건강', platform: 'YOUTUBE', currentScore: 72, predictedScore: 68, confidence: 0.75, direction: 'DECLINING', peakDate: null, createdAt: '2025-03-12T14:00:00Z' },
        { id: 4, keyword: '간단 레시피', category: '요리', platform: 'TIKTOK', currentScore: 85, predictedScore: 85, confidence: 0.90, direction: 'STABLE', peakDate: null, createdAt: '2025-03-12T15:00:00Z' },
        { id: 5, keyword: '여행 브이로그', category: '여행', platform: 'YOUTUBE', currentScore: 60, predictedScore: 78, confidence: 0.80, direction: 'RISING', peakDate: '2025-05-01', createdAt: '2025-03-13T08:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchTopics(predictionId: number) {
    try {
      topics.value = await trendPredictorApi.getTopics(predictionId)
    } catch {
      topics.value = [
        { id: 1, predictionId, topic: 'ChatGPT 활용', relatedKeywords: ['AI', 'ChatGPT', '생산성'], videoCount: 340, avgViews: 52000, growthRate: 23.5 },
        { id: 2, predictionId, topic: 'AI 이미지 생성', relatedKeywords: ['미드저니', 'DALL-E', 'AI아트'], videoCount: 180, avgViews: 38000, growthRate: 18.2 },
        { id: 3, predictionId, topic: 'AI 코딩', relatedKeywords: ['코파일럿', 'AI개발', '자동화'], videoCount: 120, avgViews: 45000, growthRate: 31.0 },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await trendPredictorApi.getSummary()
    } catch {
      summary.value = { totalPredictions: 156, risingTrends: 42, accuracy: 84.5, topCategory: '테크', lastUpdated: '2025-03-12T16:00:00Z' }
    }
  }

  async function predict(keyword: string, platform: string) {
    try {
      const prediction = await trendPredictorApi.predict(keyword, platform)
      predictions.value.unshift(prediction)
    } catch {
      // fallback
    }
  }

  return { predictions, topics, summary, loading, risingTrends, decliningTrends, fetchPredictions, fetchTopics, fetchSummary, predict }
})
