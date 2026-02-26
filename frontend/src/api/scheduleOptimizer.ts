import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { OptimalSlot, ScheduleRecommendation, ScheduleOptimizerSummary } from '@/types/scheduleOptimizer'

export const scheduleOptimizerApi = {
  getSlots: (platform: string) =>
    apiClient.get<ResData<OptimalSlot[]>>(`/api/v1/schedule-optimizer/slots?platform=${platform}`).then(unwrapResponse),

  getRecommendations: () =>
    apiClient.get<ResData<ScheduleRecommendation[]>>('/api/v1/schedule-optimizer/recommendations').then(unwrapResponse),

  applyRecommendation: (id: number) =>
    apiClient.post<ResData<ScheduleRecommendation>>(`/api/v1/schedule-optimizer/recommendations/${id}/apply`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<ScheduleOptimizerSummary>>('/api/v1/schedule-optimizer/summary').then(unwrapResponse),
}
