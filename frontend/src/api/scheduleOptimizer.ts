import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { AiEstimate } from '@/types/aiEstimate'

/**
 * 추천 시간대. 백엔드 `OptimalSlotResponse` 와 같다.
 * - measured=true: score 는 이번 결과 최고 시간대 대비 %, audienceOnline 은 그 시간대 게시물의 **조회수 중앙값**(실측, 동시 접속자 수 아님)
 * - measured=false: 예전에 AI 가 매긴 값. audienceOnline 은 null, score 는 aiEstimate 로 표시해야 한다
 * - competitionLevel 은 잴 데이터가 없어 항상 null
 */
export interface OptimalSlot {
  id: number
  platform: string
  dayOfWeek: string
  hour: number
  score: number
  audienceOnline: number | null
  competitionLevel: string | null
  measured: boolean
  aiEstimate: AiEstimate | null
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
