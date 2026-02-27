import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { TrendPrediction, TrendTopic, TrendPredictorSummary } from '@/types/trendPredictor'

export const trendPredictorApi = {
  getPredictions: (category?: string) =>
    apiClient.get<ResData<TrendPrediction[]>>('/trend-predictor', { params: { category } }).then(unwrapResponse),
  getPrediction: (id: number) =>
    apiClient.get<ResData<TrendPrediction>>(`/trend-predictor/${id}`).then(unwrapResponse),
  predict: (keyword: string, platform: string) =>
    apiClient.post<ResData<TrendPrediction>>('/trend-predictor', { keyword, platform }).then(unwrapResponse),
  getTopics: (predictionId: number) =>
    apiClient.get<ResData<TrendTopic[]>>(`/trend-predictor/${predictionId}/topics`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<TrendPredictorSummary>>('/trend-predictor/summary').then(unwrapResponse),
}

export default trendPredictorApi
