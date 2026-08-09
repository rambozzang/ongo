import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface OptimalSlot {
  id: number
  platform: string
  dayOfWeek: string
  hour: number
  score: number
  audienceOnline: number
  competitionLevel: string
  reason: string
  createdAt: string | null
}

export interface ScheduleRecommendation {
  id: number
  videoId: number
  channelId?: number | null
  videoTitle: string
  currentSchedule: string | null
  recommendedSchedule: string
  platform: string
  expectedImprovement: number
  confidence: number
  status: string
  createdAt: string | null
}

export interface ScheduleOptimizerSummary {
  totalRecommendations: number
  appliedCount: number
  avgImprovement: number
  bestDay: string
  bestHour: number
}

export const scheduleOptimizerApi = {
  generateSlots: (platform: string) =>
    apiClient
      .post<ResData<OptimalSlot[]>>('/schedule-optimizer/generate', null, { params: { platform } })
      .then(unwrapResponse),

  getSlots: (platform: string) =>
    apiClient
      .get<ResData<OptimalSlot[]>>(`/schedule-optimizer/slots?platform=${platform}`)
      .then(unwrapResponse),

  getRecommendations: () =>
    apiClient
      .get<ResData<ScheduleRecommendation[]>>('/schedule-optimizer/recommendations')
      .then(unwrapResponse),

  applyRecommendation: (id: number) =>
    apiClient
      .post<ResData<ScheduleRecommendation>>(`/schedule-optimizer/recommendations/${id}/apply`)
      .then(unwrapResponse),

  getSummary: () =>
    apiClient
      .get<ResData<ScheduleOptimizerSummary>>('/schedule-optimizer/summary')
      .then(unwrapResponse),
}
