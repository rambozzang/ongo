import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Schedule, ScheduleCreateRequest, ScheduleUpdateRequest, CalendarView } from '@/types/schedule'
import { scheduleApi } from '@/api/schedule'

export const useScheduleStore = defineStore('schedule', () => {
  const schedules = ref<Schedule[]>([])
  const loading = ref(false)
  const loadError = ref<string | null>(null)
  const calendarView = ref<CalendarView>('month')

  async function fetchSchedules(startDate?: string, endDate?: string) {
    loading.value = true
    loadError.value = null
    try {
      schedules.value = await scheduleApi.list({ startDate, endDate })
    } catch (error) {
      // Preserve the last confirmed data. An empty calendar after a network
      // failure looks like deleted reservations and invites duplicate posts.
      loadError.value = error instanceof Error ? error.message : '예약을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  async function createSchedule(request: ScheduleCreateRequest) {
    const schedule = await scheduleApi.create(request)
    schedules.value.push(schedule)
    return schedule
  }

  async function updateSchedule(id: number, request: ScheduleUpdateRequest) {
    const updated = await scheduleApi.update(id, request)
    const index = schedules.value.findIndex((s) => s.id === id)
    if (index !== -1) {
      schedules.value[index] = updated
    }
    return updated
  }

  async function cancelSchedule(id: number) {
    await scheduleApi.cancel(id)
    schedules.value = schedules.value.map((s) =>
      s.id === id ? { ...s, status: 'CANCELLED' as const } : s
    )
  }

  function setCalendarView(view: CalendarView) {
    calendarView.value = view
  }

  return {
    schedules,
    loading,
    loadError,
    calendarView,
    fetchSchedules,
    createSchedule,
    updateSchedule,
    cancelSchedule,
    setCalendarView,
  }
})
