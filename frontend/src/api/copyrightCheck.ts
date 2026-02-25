import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CopyrightCheckResult,
  RunCheckRequest,
  RunCheckResponse,
  VideoForCheck,
} from '@/types/copyrightCheck'

export const copyrightCheckApi = {
  getVideos() {
    return apiClient
      .get<ResData<{ videos: VideoForCheck[] }>>('/ai/copyright-check/videos')
      .then(unwrapResponse)
      .then((res) => res.videos)
  },

  getResults() {
    return apiClient
      .get<ResData<{ results: CopyrightCheckResult[] }>>('/ai/copyright-check/results')
      .then(unwrapResponse)
      .then((res) => res.results)
  },

  getResult(id: number) {
    return apiClient
      .get<ResData<CopyrightCheckResult>>(`/ai/copyright-check/results/${id}`)
      .then(unwrapResponse)
  },

  runCheck(request: RunCheckRequest) {
    return apiClient
      .post<ResData<RunCheckResponse>>('/ai/copyright-check/run', request)
      .then(unwrapResponse)
  },

  autoFix(resultId: number, issueId: string) {
    return apiClient
      .post<ResData<CopyrightCheckResult>>(`/ai/copyright-check/results/${resultId}/fix/${issueId}`)
      .then(unwrapResponse)
  },
}
