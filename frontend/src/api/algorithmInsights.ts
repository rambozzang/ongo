import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AlgorithmInsight,
  AlgorithmScore,
  AlgorithmChange,
  AlgorithmInsightsSummary,
} from '@/types/algorithmInsights'

export const algorithmInsightsApi = {
  getInsights(platform?: string) {
    return apiClient
      .get<ResData<AlgorithmInsight[]>>('/algorithm-insights', { params: { platform } })
      .then(unwrapResponse)
  },

  getScores() {
    return apiClient
      .get<ResData<AlgorithmScore[]>>('/algorithm-insights/scores')
      .then(unwrapResponse)
  },

  getScore(platform: string) {
    return apiClient
      .get<ResData<AlgorithmScore>>(`/algorithm-insights/scores/${platform}`)
      .then(unwrapResponse)
  },

  getChanges() {
    return apiClient
      .get<ResData<AlgorithmChange[]>>('/algorithm-insights/changes')
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<AlgorithmInsightsSummary>>('/algorithm-insights/summary')
      .then(unwrapResponse)
  },
}
