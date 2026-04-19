import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface ViewsPredictionFactor {
  factor: string
  impact: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL'
  description: string
}

export interface ViewsPredictionResponse {
  videoId: number
  predicted24h: number
  predicted7d: number
  predicted30d: number
  confidenceScore: number
  comparisonToAverage: number
  influencingFactors: ViewsPredictionFactor[]
  creditsUsed: number
}

export const viewsPredictionApi = {
  predict(videoId: number) {
    return apiClient
      .post<ResData<ViewsPredictionResponse>>(`/videos/${videoId}/predict-views`)
      .then(unwrapResponse)
  },
}
