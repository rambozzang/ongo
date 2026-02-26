import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { ChannelHealthMetric, HealthTrend, ChannelHealthSummary } from '@/types/channelHealth'

export const channelHealthApi = {
  getMetrics: () =>
    apiClient.get<ResData<ChannelHealthMetric[]>>('/api/v1/channel-health').then(unwrapResponse),

  measure: (channelId: number) =>
    apiClient.post<ResData<ChannelHealthMetric>>(`/api/v1/channel-health/${channelId}/measure`).then(unwrapResponse),

  getTrends: (metricId: number) =>
    apiClient.get<ResData<HealthTrend[]>>(`/api/v1/channel-health/${metricId}/trends`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<ChannelHealthSummary>>('/api/v1/channel-health/summary').then(unwrapResponse),
}
