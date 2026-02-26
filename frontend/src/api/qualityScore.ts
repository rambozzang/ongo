import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { QualityCheckRequest, QualityCheckResponse, QualityReport } from '@/types/qualityScore'

export const qualityScoreApi = {
  checkQuality(request: QualityCheckRequest) {
    return apiClient
      .post<ResData<QualityCheckResponse>>('/ai/quality-score/check', request)
      .then(unwrapResponse)
  },

  getHistory() {
    return apiClient
      .get<ResData<{ reports: QualityReport[] }>>('/ai/quality-score/history')
      .then(unwrapResponse)
      .then((res) => res.reports)
  },

  getReport(id: number) {
    return apiClient
      .get<ResData<QualityReport>>(`/ai/quality-score/reports/${id}`)
      .then(unwrapResponse)
  },
}
