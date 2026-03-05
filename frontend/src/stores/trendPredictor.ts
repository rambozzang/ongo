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
      predictions.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchTopics(predictionId: number) {
    try {
      topics.value = await trendPredictorApi.getTopics(predictionId)
    } catch {
      topics.value = []
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await trendPredictorApi.getSummary()
    } catch {
      summary.value = null
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
