import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  LiveDashboardState,
  LiveAlertConfig,
  LiveAlert,
} from '@/types/liveDashboard'

export const liveDashboardApi = {
  getState() {
    return apiClient
      .get<ResData<LiveDashboardState>>('/analytics/live')
      .then(unwrapResponse)
  },

  getAlerts() {
    return apiClient
      .get<ResData<{ alerts: LiveAlert[] }>>('/analytics/live/alerts')
      .then(unwrapResponse)
      .then((res) => res.alerts)
  },

  markAlertRead(alertId: number) {
    return apiClient
      .put<ResData<void>>(`/analytics/live/alerts/${alertId}/read`)
      .then(unwrapResponse)
  },

  getAlertConfigs() {
    return apiClient
      .get<ResData<{ configs: LiveAlertConfig[] }>>('/analytics/live/alert-configs')
      .then(unwrapResponse)
      .then((res) => res.configs)
  },

  updateAlertConfig(config: LiveAlertConfig) {
    return apiClient
      .put<ResData<LiveAlertConfig>>('/analytics/live/alert-configs', config)
      .then(unwrapResponse)
  },
}
