import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface OptimalSlot {
  id: number
  platform: string
  dayOfWeek: string
  hour: number
  score: number
  estimatedAudience: number
  competition: string
  reason: string
}

export interface ScheduleRecommendation {
  id: number
  platform: string
  recommendedAt: string
  score: number
  reason: string
}

export interface ScheduleOptimizerSummary {
  totalSlots: number
  platforms: string[]
  lastGeneratedAt: string | null
}

export const scheduleOptimizerApi = {
  generateSlots: (platform: string) =>
    apiClient
      .post<ResData<OptimalSlot[]>>(`/schedule-optimizer/generate?platform=${platform}`)
      .then(unwrapResponse),

  getSlots: (platform: string) =>
    apiClient
      .get<ResData<OptimalSlot[]>>(`/schedule-optimizer/slots?platform=${platform}`)
      .then(unwrapResponse),

  getRecommendations: () =>
    apiClient
      .get<ResData<ScheduleRecommendation[]>>('/schedule-optimizer/recommendations')
      .then(unwrapResponse),

  getSummary: () =>
    apiClient
      .get<ResData<ScheduleOptimizerSummary>>('/schedule-optimizer/summary')
      .then(unwrapResponse),
}
