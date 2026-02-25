import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
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

export const predictionApi = {
  predict(request: PredictionRequest) {
    return apiClient
      .post<ResData<PredictionResult>>('/ai/prediction', request)
      .then(unwrapResponse)
  },

  getReachHeatmap(platform?: string) {
    const params = platform ? { platform } : {}
    return apiClient
      .get<ResData<{ data: HeatmapCell[] }>>('/ai/prediction/heatmap', { params })
      .then(unwrapResponse)
      .then((res) => res.data)
  },

  getOptimalTimes(platform?: string) {
    const params = platform ? { platform } : {}
    return apiClient
      .get<ResData<{ slots: OptimalTimeRecommendation[] }>>('/ai/prediction/optimal-times', { params })
      .then(unwrapResponse)
      .then((res) => res.slots)
  },

  getTitleSuggestions(title: string, platform?: string) {
    const params: Record<string, string> = { title }
    if (platform) params.platform = platform
    return apiClient
      .get<ResData<{ suggestions: TitleSuggestion[] }>>('/ai/prediction/title-suggestions', { params })
      .then(unwrapResponse)
      .then((res) => res.suggestions)
  },

  getTagSuggestions(tags: string[], platform?: string) {
    const params: Record<string, string> = { tags: tags.join(',') }
    if (platform) params.platform = platform
    return apiClient
      .get<ResData<{ suggestions: TagSuggestion[] }>>('/ai/prediction/tag-suggestions', { params })
      .then(unwrapResponse)
      .then((res) => res.suggestions)
  },

  getCompetitorComparison(videoId: number) {
    return apiClient
      .get<ResData<{ comparisons: CompetitorComparison[] }>>(`/ai/prediction/competitors/${videoId}`)
      .then(unwrapResponse)
      .then((res) => res.comparisons)
  },

  getHistory(limit = 20) {
    return apiClient
      .get<ResData<{ items: PredictionHistoryItem[] }>>('/ai/prediction/history', { params: { limit } })
      .then(unwrapResponse)
      .then((res) => res.items)
  },

  getSummary() {
    return apiClient
      .get<ResData<PredictionSummary>>('/ai/prediction/summary')
      .then(unwrapResponse)
  },
}
