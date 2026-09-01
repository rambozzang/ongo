import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface MetaRewriteResponse {
  originalTitle: string
  originalDescription: string | null
  suggestedTitle: string
  suggestedDescription: string
  suggestedTags: string[]
  reasoning: string
  expectedImpactPercent: number
  createdAt: string
}

export const metaRewriteApi = {
  rewrite(videoId: number) {
    return apiClient
      .post<ResData<MetaRewriteResponse>>(`/videos/${videoId}/rewrite-meta`)
      .then(unwrapResponse)
  },
}
