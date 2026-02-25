import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AiCalendarGenerateRequest,
  AiCalendarResult,
  AiCalendarApplyRequest,
} from '@/types/aiCalendar'

export const aiCalendarApi = {
  generate(request: AiCalendarGenerateRequest) {
    return apiClient
      .post<ResData<AiCalendarResult>>('/ai/calendar/generate', request)
      .then(unwrapResponse)
  },

  regenerateSlots(calendarId: string, slotIds: string[]) {
    return apiClient
      .post<ResData<AiCalendarResult>>(`/ai/calendar/${calendarId}/regenerate`, { slotIds })
      .then(unwrapResponse)
  },

  applyToSchedule(request: AiCalendarApplyRequest) {
    return apiClient
      .post<ResData<{ appliedCount: number }>>('/ai/calendar/apply', request)
      .then(unwrapResponse)
  },
}
