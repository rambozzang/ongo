import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CalendarInsight,
  OptimalTimeSlot,
  UploadPattern,
  CalendarInsightsSummary,
} from '@/types/calendarInsights'

export const calendarInsightsApi = {
  getInsights(year: number, month: number) {
    return apiClient
      .get<ResData<CalendarInsight[]>>('/calendar-insights', { params: { year, month } })
      .then(unwrapResponse)
  },

  getOptimalTimeSlots(platform?: string) {
    return apiClient
      .get<ResData<OptimalTimeSlot[]>>('/calendar-insights/optimal-times', {
        params: { platform },
      })
      .then(unwrapResponse)
  },

  getUploadPatterns() {
    return apiClient
      .get<ResData<UploadPattern[]>>('/calendar-insights/patterns')
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<CalendarInsightsSummary>>('/calendar-insights/summary')
      .then(unwrapResponse)
  },
}
