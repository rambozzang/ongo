import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface SeoScoreCategory {
  name: string
  score: number
  maxScore: number
}

export interface SeoScoreResponse {
  videoId: number
  overallScore: number
  categories: SeoScoreCategory[]
  suggestions: string[]
  creditsUsed: number
}

export const videoSeoApi = {
  analyze(videoId: number) {
    return apiClient
      .post<ResData<SeoScoreResponse>>(`/videos/${videoId}/seo-score`)
      .then(unwrapResponse)
  },
}
