import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface RecurringScheduleResponse {
  id: number
  name: string
  frequency: string
  dayOfWeek: number | null
  dayOfMonth: number | null
  timeOfDay: string
  timezone: string
  platforms: string[]
  titleTemplate: string | null
  descriptionTemplate: string | null
  tags: string[]
  isActive: boolean
  nextRunAt: string | null
  lastRunAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface CreateRecurringScheduleRequest {
  name: string
  frequency: string
  dayOfWeek?: number
  dayOfMonth?: number
  timeOfDay: string
  timezone?: string
  platforms?: string[]
  titleTemplate?: string
  descriptionTemplate?: string
  tags?: string[]
  isActive?: boolean
}

export interface UpdateRecurringScheduleRequest {
  name?: string
  frequency?: string
  dayOfWeek?: number
  dayOfMonth?: number
  timeOfDay?: string
  timezone?: string
  platforms?: string[]
  titleTemplate?: string
  descriptionTemplate?: string
  tags?: string[]
}

export const recurringScheduleApi = {
  list() {
    return apiClient
      .get<ResData<RecurringScheduleResponse[]>>('/schedules/recurring')
      .then(unwrapResponse)
  },

  create(request: CreateRecurringScheduleRequest) {
    return apiClient
      .post<ResData<RecurringScheduleResponse>>('/schedules/recurring', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateRecurringScheduleRequest) {
    return apiClient
      .put<ResData<RecurringScheduleResponse>>(`/schedules/recurring/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient
      .delete<ResData<void>>(`/schedules/recurring/${id}`)
      .then(unwrapResponse)
  },

  toggle(id: number) {
    return apiClient
      .put<ResData<RecurringScheduleResponse>>(`/schedules/recurring/${id}/toggle`)
      .then(unwrapResponse)
  },
}
