import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RecyclingSuggestion } from '@/types/recycling'

export const recyclingApi = {
  getSuggestions(status?: string) {
    const params = status ? { status } : undefined
    return apiClient
      .get<ResData<RecyclingSuggestion[]>>('/recycling/suggestions', { params })
      .then(unwrapResponse)
  },

  generateSuggestions() {
    return apiClient
      .post<ResData<RecyclingSuggestion[]>>('/recycling/suggestions/generate')
      .then(unwrapResponse)
  },

  acceptSuggestion(id: number) {
    return apiClient
      .put<ResData<RecyclingSuggestion>>(`/recycling/suggestions/${id}/accept`)
      .then(unwrapResponse)
  },

  dismissSuggestion(id: number) {
    return apiClient
      .put<ResData<RecyclingSuggestion>>(`/recycling/suggestions/${id}/dismiss`)
      .then(unwrapResponse)
  },
}
