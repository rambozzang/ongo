import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Schedule, ScheduleCreateRequest, ScheduleUpdateRequest } from '@/types/schedule'

export const scheduleApi = {
  list(params?: { startDate?: string; endDate?: string; status?: string }) {
    return apiClient
      .get<ResData<{ schedules: Schedule[] }>>('/schedules', { params })
      .then(unwrapResponse)
      .then((data) => (Array.isArray(data) ? data : data.schedules ?? []))
  },

  get(id: number) {
    return apiClient.get<ResData<Schedule>>(`/schedules/${id}`).then(unwrapResponse)
  },

  create(request: ScheduleCreateRequest) {
    return apiClient.post<ResData<Schedule>>('/schedules', request).then(unwrapResponse)
  },

  update(id: number, request: ScheduleUpdateRequest) {
    return apiClient.put<ResData<Schedule>>(`/schedules/${id}`, request).then(unwrapResponse)
  },

  cancel(id: number) {
    return apiClient.delete<ResData<void>>(`/schedules/${id}`).then(unwrapResponse)
  },
}
