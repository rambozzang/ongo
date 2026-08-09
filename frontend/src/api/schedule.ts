import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Schedule, ScheduleCreateRequest, ScheduleUpdateRequest } from '@/types/schedule'

export function toScheduleRangeParams(params?: { startDate?: string; endDate?: string; status?: string }) {
  const { startDate, endDate, status } = params ?? {}
  // The backend treats `to` as an exclusive LocalDateTime bound. Advance the
  // inclusive UI end date to the next local midnight so the final second is
  // not silently omitted.
  const endExclusive = endDate
    ? (() => {
        const date = new Date(`${endDate}T00:00:00`)
        date.setDate(date.getDate() + 1)
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T00:00:00`
      })()
    : undefined
  return {
    from: startDate ? `${startDate}T00:00:00` : undefined,
    to: endExclusive,
    status,
  }
}

export const scheduleApi = {
  list(params?: { startDate?: string; endDate?: string; status?: string }) {
    // The API owns LocalDateTime `from`/`to` parameters. Keep the UI's
    // date-only range here and expand it to an inclusive local-day range so
    // calendar/today queries cannot silently fall back to the backend default.
    return apiClient
      .get<ResData<{ schedules: Schedule[] }>>('/schedules', { params: toScheduleRangeParams(params) })
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
