import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RewriteRequest, RewriteResponse, RewriteResult } from '@/types/contentRewriter'

export const contentRewriterApi = {
  rewrite(request: RewriteRequest) {
    return apiClient
      .post<ResData<RewriteResponse>>('/ai/content-rewriter/rewrite', request)
      .then(unwrapResponse)
  },

  getHistory() {
    return apiClient
      .get<ResData<{ results: RewriteResult[] }>>('/ai/content-rewriter/history')
      .then(unwrapResponse)
      .then((res) => res.results)
  },

  deleteResult(id: number) {
    return apiClient
      .delete<ResData<void>>(`/ai/content-rewriter/results/${id}`)
      .then(unwrapResponse)
  },
}
