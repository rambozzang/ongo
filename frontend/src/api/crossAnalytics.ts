import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CrossPlatformReport,
  ContentCompareRequest,
  CrossPlatformContent,
} from '@/types/crossAnalytics'

export const crossAnalyticsApi = {
  getReport(period?: string) {
    const params = period ? { period } : {}
    return apiClient
      .get<ResData<CrossPlatformReport>>('/analytics/cross-platform', { params })
      .then(unwrapResponse)
  },

  compareContents(request: ContentCompareRequest) {
    return apiClient
      .post<ResData<{ contents: CrossPlatformContent[] }>>('/analytics/cross-platform/compare', request)
      .then(unwrapResponse)
      .then((res) => res.contents)
  },

  exportReport(reportId: number, format: string) {
    return apiClient
      .get<Blob>(`/analytics/cross-platform/${reportId}/export`, {
        params: { format },
        responseType: 'blob',
      })
  },
}
