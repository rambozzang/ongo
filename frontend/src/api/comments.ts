import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { CommentListResponse, CommentResponse } from '@/types/comment'

export const commentsApi = {
  list(params?: { videoId?: number; platform?: string; sentiment?: string; page?: number; size?: number }) {
    return apiClient
      .get<ResData<CommentListResponse>>('/comments', { params })
      .then(unwrapResponse)
  },

  reply(id: number, content: string) {
    return apiClient
      .post<ResData<CommentResponse>>(`/comments/${id}/reply`, { content })
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/comments/${id}`).then(unwrapResponse)
  },
}
