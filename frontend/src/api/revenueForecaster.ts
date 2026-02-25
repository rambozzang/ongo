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
      .get<ResData<RevenueForecast>>('/ai/revenue-forecast', { params })
      .then(unwrapResponse)
  },

  generateForecast(request: ForecastRequest) {
    return apiClient
      .post<ResData<ForecastResponse>>('/ai/revenue-forecast/generate', request)
      .then(unwrapResponse)
  },

  getHistory() {
    return apiClient
      .get<ResData<{ forecasts: RevenueForecast[] }>>('/ai/revenue-forecast/history')
      .then(unwrapResponse)
      .then((res) => res.forecasts)
  },
}
