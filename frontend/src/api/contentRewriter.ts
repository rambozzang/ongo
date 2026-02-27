import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RewriteRequest, RewriteResponse, RewriteResult } from '@/types/contentRewriter'

export const contentRewriterApi = {
  rewrite(request: RewriteRequest) {
    return apiClient
      .post<ResData<RewriteResponse>>('/content-rewriter/rewrite', request)
      .then(unwrapResponse)
  },

  getHistory() {
    return apiClient
      .get<ResData<{ results: RewriteResult[] }>>('/content-rewriter/history')
      .then(unwrapResponse)
      .then((res) => res.results)
  },

  deleteResult(id: number) {
    return apiClient
      .delete<ResData<void>>(`/content-rewriter/results/${id}`)
      .then(unwrapResponse)
  },
}
