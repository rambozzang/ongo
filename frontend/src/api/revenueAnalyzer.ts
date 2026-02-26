import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RevenueStream, RevenueProjection, RevenueAnalyzerSummary } from '@/types/revenueAnalyzer'

export const revenueAnalyzerApi = {
  getStreams: () =>
    apiClient.get<ResData<RevenueStream[]>>('/api/v1/revenue-analyzer').then(unwrapResponse),

  getProjections: (channelId: number) =>
    apiClient.get<ResData<RevenueProjection[]>>(`/api/v1/revenue-analyzer/${channelId}/projections`).then(unwrapResponse),

  analyze: (channelId: number) =>
    apiClient.post<ResData<RevenueStream[]>>(`/api/v1/revenue-analyzer/${channelId}/analyze`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<RevenueAnalyzerSummary>>('/api/v1/revenue-analyzer/summary').then(unwrapResponse),
}
