import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface MetaRewriteByPlatformRequest {
  platform: string
  platformVideoId: string
  title: string
  description?: string | null
}

export interface MetaRewriteVariant {
  title: string
  description: string
  tags: string[]
}

export interface MetaRewriteResponse {
  originalTitle: string
  originalDescription: string
  originalTags: string[]
  suggestions: MetaRewriteVariant[]
  creditsUsed: number
  creditsRemaining: number
}

export const metaRewriteApi = {
  rewriteByPlatform(request: MetaRewriteByPlatformRequest) {
    return apiClient
      .post<ResData<MetaRewriteResponse>>('/videos/rewrite-meta', request)
      .then(unwrapResponse)
  },
}
