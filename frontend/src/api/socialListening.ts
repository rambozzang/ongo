import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { SocialListeningReport, KeywordAlert } from '@/types/socialListening'

export const socialListeningApi = {
  getReport(period?: string) {
    const params = period ? { period } : {}
    return apiClient
      .get<ResData<SocialListeningReport>>('/analytics/social-listening', { params })
      .then(unwrapResponse)
  },

  addKeywordAlert(keyword: string) {
    return apiClient
      .post<ResData<KeywordAlert>>('/analytics/social-listening/alerts', { keyword })
      .then(unwrapResponse)
  },

  toggleAlert(id: number, enabled: boolean) {
    return apiClient
      .put<ResData<KeywordAlert>>(`/analytics/social-listening/alerts/${id}`, { enabled })
      .then(unwrapResponse)
  },

  deleteAlert(id: number) {
    return apiClient
      .delete<ResData<void>>(`/analytics/social-listening/alerts/${id}`)
      .then(unwrapResponse)
  },
}
