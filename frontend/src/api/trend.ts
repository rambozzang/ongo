import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Trend,
  TrendAnalysis,
  TrendAlert,
  CreateTrendAlertRequest,
  UpdateTrendAlertRequest,
} from '@/types/trend'

export const trendApi = {
  list(params?: { date?: string; category?: string; source?: string }) {
    return apiClient
      .get<ResData<Trend[]>>('/trends', { params })
      .then(unwrapResponse)
  },

  search(keyword: string) {
    return apiClient
      .get<ResData<Trend[]>>('/trends/search', { params: { keyword } })
      .then(unwrapResponse)
  },

  analyze(category?: string) {
    return apiClient
      .get<ResData<TrendAnalysis>>('/trends/analysis', { params: { category } })
      .then(unwrapResponse)
  },

  getAlerts() {
    return apiClient
      .get<ResData<TrendAlert[]>>('/trends/alerts')
      .then(unwrapResponse)
  },

  createAlert(request: CreateTrendAlertRequest) {
    return apiClient
      .post<ResData<TrendAlert>>('/trends/alerts', request)
      .then(unwrapResponse)
  },

  updateAlert(alertId: number, request: UpdateTrendAlertRequest) {
    return apiClient
      .put<ResData<TrendAlert>>(`/trends/alerts/${alertId}`, request)
      .then(unwrapResponse)
  },

  deleteAlert(alertId: number) {
    return apiClient
      .delete<ResData<void>>(`/trends/alerts/${alertId}`)
      .then(unwrapResponse)
  },
}
