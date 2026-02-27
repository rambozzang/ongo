import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ForecastRequest,
  ForecastResponse,
  RevenueForecast,
} from '@/types/revenueForecaster'

export const revenueForecasterApi = {
  getForecast(period?: string) {
    const params = period ? { period } : {}
    return apiClient
      .get<ResData<RevenueForecast>>('/revenue-forecaster', { params })
      .then(unwrapResponse)
  },

  generateForecast(request: ForecastRequest) {
    return apiClient
      .post<ResData<ForecastResponse>>('/revenue-forecaster/generate', request)
      .then(unwrapResponse)
  },

  getHistory() {
    return apiClient
      .get<ResData<{ forecasts: RevenueForecast[] }>>('/revenue-forecaster/history')
      .then(unwrapResponse)
      .then((res) => res.forecasts)
  },
}
