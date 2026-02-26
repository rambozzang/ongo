import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CalendarSuggestion,
  CalendarSlot,
  ContentCalendarAiSummary,
  GenerateCalendarRequest,
} from '@/types/contentCalendarAi'

export const contentCalendarAiApi = {
  getSuggestions(status?: string) {
    return apiClient
      .get<ResData<CalendarSuggestion[]>>('/content-calendar-ai', { params: { status } })
      .then(unwrapResponse)
  },

  generate(request: GenerateCalendarRequest) {
    return apiClient
      .post<ResData<CalendarSuggestion[]>>('/content-calendar-ai/generate', request)
      .then(unwrapResponse)
  },

  getSlots(startDate: string, endDate: string) {
    return apiClient
      .get<ResData<CalendarSlot[]>>('/content-calendar-ai/slots', {
        params: { startDate, endDate },
      })
      .then(unwrapResponse)
  },

  acceptSuggestion(id: number) {
    return apiClient
      .post<ResData<CalendarSuggestion>>(`/content-calendar-ai/${id}/accept`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<ContentCalendarAiSummary>>('/content-calendar-ai/summary')
      .then(unwrapResponse)
  },
}
