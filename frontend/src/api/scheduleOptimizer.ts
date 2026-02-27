import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { OptimalSlot, ScheduleRecommendation, ScheduleOptimizerSummary } from '@/types/scheduleOptimizer'

export const scheduleOptimizerApi = {
  getSlots: (platform: string) =>
    apiClient.get<ResData<OptimalSlot[]>>(`/schedule-optimizer/slots?platform=${platform}`).then(unwrapResponse),

  getRecommendations: () =>
    apiClient.get<ResData<ScheduleRecommendation[]>>('/schedule-optimizer/recommendations').then(unwrapResponse),

  applyRecommendation: (id: number) =>
    apiClient.post<ResData<ScheduleRecommendation>>(`/schedule-optimizer/recommendations/${id}/apply`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<ScheduleOptimizerSummary>>('/schedule-optimizer/summary').then(unwrapResponse),
}
