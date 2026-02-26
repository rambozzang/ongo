import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CommentSummaryResult,
  TopComment,
  CommentSummarySummary,
} from '@/types/commentSummary'

export const commentSummaryApi = {
  getResults(platform?: string) {
    return apiClient
      .get<ResData<CommentSummaryResult[]>>('/comment-summary', { params: { platform } })
      .then(unwrapResponse)
  },

  analyze(videoId: number) {
    return apiClient
      .post<ResData<CommentSummaryResult>>('/comment-summary/analyze', { videoId })
      .then(unwrapResponse)
  },

  getTopComments(summaryId: number) {
    return apiClient
      .get<ResData<TopComment[]>>(`/comment-summary/${summaryId}/top-comments`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<CommentSummarySummary>>('/comment-summary/summary')
      .then(unwrapResponse)
  },
}
