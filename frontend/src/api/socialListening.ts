import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { SocialListeningReport, KeywordAlert } from '@/types/socialListening'

export const socialListeningApi = {
  getReport(period?: string) {
    const params = period ? { period } : {}
    return apiClient
      .get<ResData<SocialListeningReport>>('/social-listening/report', { params })
      .then(unwrapResponse)
  },

  addKeywordAlert(keyword: string) {
    return apiClient
      .post<ResData<KeywordAlert>>('/social-listening/alerts', { keyword })
      .then(unwrapResponse)
  },

  toggleAlert(id: number, enabled: boolean) {
    return apiClient
      .put<ResData<KeywordAlert>>(`/social-listening/alerts/${id}/toggle`, { enabled })
      .then(unwrapResponse)
  },

  deleteAlert(id: number) {
    return apiClient
      .delete<ResData<void>>(`/social-listening/alerts/${id}`)
      .then(unwrapResponse)
  },
}
