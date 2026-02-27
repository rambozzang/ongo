import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RevenueStream, RevenueProjection, RevenueAnalyzerSummary } from '@/types/revenueAnalyzer'

export const revenueAnalyzerApi = {
  getStreams: () =>
    apiClient.get<ResData<RevenueStream[]>>('/revenue-analyzer').then(unwrapResponse),

  getProjections: (channelId: number) =>
    apiClient.get<ResData<RevenueProjection[]>>(`/revenue-analyzer/${channelId}/projections`).then(unwrapResponse),

  analyze: (channelId: number) =>
    apiClient.post<ResData<RevenueStream[]>>(`/revenue-analyzer/${channelId}/analyze`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<RevenueAnalyzerSummary>>('/revenue-analyzer/summary').then(unwrapResponse),
}
