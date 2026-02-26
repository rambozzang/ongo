import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { VideoFunnel, FunnelComparison, FunnelSummary } from '@/types/contentFunnel'

export const contentFunnelApi = {
  getSummary: () =>
    apiClient.get<ResData<FunnelSummary>>('/content-funnels/summary').then(unwrapResponse),

  getFunnels: () =>
    apiClient.get<ResData<VideoFunnel[]>>('/content-funnels').then(unwrapResponse),

  getFunnel: (id: number) =>
    apiClient.get<ResData<VideoFunnel>>(`/content-funnels/${id}`).then(unwrapResponse),

  compare: (videoAId: number, videoBId: number) =>
    apiClient.get<ResData<FunnelComparison>>('/content-funnels/compare', { params: { videoAId, videoBId } }).then(unwrapResponse),
}
