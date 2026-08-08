import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export type RecurringFrequency = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY'

export interface RecurringSchedule {
  id: number
  videoId: number | null
  name: string
  frequency: RecurringFrequency
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
  videoId: number
  name: string
  frequency: RecurringFrequency
  dayOfWeek?: number
  dayOfMonth?: number
  timeOfDay: string
  timezone: string
  platforms: string[]
  titleTemplate?: string
  descriptionTemplate?: string
  tags: string[]
  isActive: boolean
}

export const recurringApi = {
  list() {
    return apiClient.get<ResData<RecurringSchedule[]>>('/schedules/recurring').then(unwrapResponse)
  },
  create(request: CreateRecurringScheduleRequest) {
    return apiClient.post<ResData<RecurringSchedule>>('/schedules/recurring', request).then(unwrapResponse)
  },
  update(id: number, request: Partial<CreateRecurringScheduleRequest>) {
    return apiClient.put<ResData<RecurringSchedule>>(`/schedules/recurring/${id}`, request).then(unwrapResponse)
  },
  remove(id: number) {
    return apiClient.delete<ResData<void>>(`/schedules/recurring/${id}`).then(unwrapResponse)
  },
  toggle(id: number) {
    return apiClient.put<ResData<RecurringSchedule>>(`/schedules/recurring/${id}/toggle`).then(unwrapResponse)
  },
}
