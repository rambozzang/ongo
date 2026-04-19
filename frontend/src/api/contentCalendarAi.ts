import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface ContentCalendarSuggestion {
  id: number
  platform: string
  suggestedDate: string
  title: string
  description: string | null
  category: string | null
  status: string
  reason: string | null
  createdAt: string
}

export interface ContentCalendarSummary {
  totalSuggestions: number
  accepted: number
  pending: number
}

export interface GenerateCalendarRequest {
  startDate: string
  endDate: string
  platforms: string[]
  frequency: string
}

export const contentCalendarAiApi = {
  generate: (data: GenerateCalendarRequest) =>
    apiClient
      .post<ResData<ContentCalendarSuggestion[]>>('/content-calendar-ai/generate', data)
      .then(unwrapResponse),

  getSuggestions: (status?: string) =>
    apiClient
      .get<ResData<ContentCalendarSuggestion[]>>('/content-calendar-ai', { params: { status } })
      .then(unwrapResponse),

  acceptSuggestion: (id: number) =>
    apiClient
      .post<ResData<ContentCalendarSuggestion>>(`/content-calendar-ai/${id}/accept`)
      .then(unwrapResponse),

  getSummary: () =>
    apiClient
      .get<ResData<ContentCalendarSummary>>('/content-calendar-ai/summary')
      .then(unwrapResponse),
}
