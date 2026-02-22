import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CommentListResponse,
  CommentResponse,
  CommentSyncResult,
  CommentCapabilities,
  SentimentTrendResponse,
  FaqClusterResponse,
  BatchAiDraftResponse,
} from '@/types/comment'

export const commentsApi = {
  list(params?: {
    videoId?: number
    platform?: string
    sentiment?: string
    searchText?: string
    startDate?: string
    endDate?: string
    page?: number
    size?: number
  }) {
    return apiClient
      .get<ResData<CommentListResponse>>('/comments', { params })
      .then(unwrapResponse)
  },

  syncAll() {
    return apiClient
      .post<ResData<CommentSyncResult>>('/comments/sync')
      .then(unwrapResponse)
  },

  syncVideo(videoId: number, platform: string, platformVideoId: string) {
    return apiClient
      .post<ResData<CommentSyncResult>>(`/comments/sync/video/${videoId}`, null, {
        params: { platform, platformVideoId },
      })
      .then(unwrapResponse)
  },

  reply(id: number, content: string) {
    return apiClient
      .post<ResData<CommentResponse>>(`/comments/${id}/reply`, { content })
      .then(unwrapResponse)
  },

  pin(id: number) {
    return apiClient
      .put<ResData<CommentResponse>>(`/comments/${id}/pin`)
      .then(unwrapResponse)
  },

  hide(id: number) {
    return apiClient
      .put<ResData<CommentResponse>>(`/comments/${id}/hide`)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/comments/${id}`).then(unwrapResponse)
  },

  getCapabilities() {
    return apiClient
      .get<ResData<Record<string, CommentCapabilities>>>('/comments/capabilities')
      .then(unwrapResponse)
  },

  sentimentTrend(days = 30) {
    return apiClient
      .get<ResData<SentimentTrendResponse>>('/comments/sentiment-trend', { params: { days } })
      .then(unwrapResponse)
  },

  faqClusters() {
    return apiClient
      .get<ResData<FaqClusterResponse>>('/comments/faq')
      .then(unwrapResponse)
  },

  batchAiDraft(commentIds: number[], tone = 'FRIENDLY') {
    return apiClient
      .post<ResData<BatchAiDraftResponse>>('/comments/ai-draft', { commentIds, tone })
      .then(unwrapResponse)
  },
}
