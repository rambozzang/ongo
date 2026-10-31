import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { scheduleApi } from '@/api/schedule'
import { scheduleOptimizerApi, type ScheduleRecommendation } from '@/api/scheduleOptimizer'
import type {
  CalendarView,
  Schedule,
  ScheduleStatus,
  ScheduleUpdateRequest,
} from '@/types/schedule'
import { toDateStr, toDateTimeLocal } from '@/utils/schedule'
import { kstDateString } from '@/utils/kst'

/**
 * 리디자인 캘린더 — 주간 예약 그리드.
 * 기존 schedule 스토어는 월/주/리스트 뷰 상태를 포함한 레거시라,
 * 주간 전용으로 얇게 분리했다.
 */

/** date 가 속한 주의 월요일 00:00 (시안은 월요일 시작이다) */
function getMonday(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay() // 0=일
  d.setDate(d.getDate() - ((day + 6) % 7))
  d.setHours(0, 0, 0, 0)
  return d
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

export const useRedesignCalendarStore = defineStore('redesignCalendar', () => {
  const today = () => new Date(`${kstDateString()}T00:00:00`)
  const weekStart = ref<Date>(getMonday(today()))
  const schedules = ref<Schedule[]>([])
  const loading = ref(false)
  const loadError = ref(false)
  const moving = ref(false)
  const recommendations = ref<ScheduleRecommendation[]>([])
  const recommendationsError = ref(false)
  const applyingRecommendationId = ref<number | null>(null)
  const view = ref<CalendarView>('week')
  const search = ref('')
  const platformFilter = ref('')
  const statusFilter = ref<ScheduleStatus | ''>('')
  const batchFailures = ref<{ id: number; title: string; message: string }[]>([])
  let requestSequence = 0

  /** 주 단위 7일 (월~일) */
  const days = computed(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart.value, i)))
  const monthDays = computed(() => {
    const first = new Date(weekStart.value.getFullYear(), weekStart.value.getMonth(), 1)
    const start = getMonday(first)
    return Array.from({ length: 42 }, (_, i) => addDays(start, i))
  })
  const visibleDays = computed(() => (view.value === 'month' ? monthDays.value : days.value))
  const filteredSchedules = computed(() =>
    schedules.value
      .filter((schedule) => {
        const date = schedule.scheduledAt.slice(0, 10)
        const query = search.value.trim().toLocaleLowerCase()
        return (
          date >= toDateStr(visibleDays.value[0]) &&
          date <= toDateStr(visibleDays.value[visibleDays.value.length - 1]) &&
          (!query || (schedule.videoTitle ?? '').toLocaleLowerCase().includes(query)) &&
          (!statusFilter.value || schedule.status === statusFilter.value) &&
          (!platformFilter.value ||
            schedule.platforms.some((target) => target.platform === platformFilter.value))
        )
      })
      .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt)),
  )

  async function fetchWeek() {
    const sequence = ++requestSequence
    loading.value = true
    loadError.value = false
    try {
      const loaded = await scheduleApi.list({
        startDate: toDateStr(visibleDays.value[0]),
        endDate: toDateStr(visibleDays.value[visibleDays.value.length - 1]),
      })
      if (sequence === requestSequence) schedules.value = loaded
    } catch {
      // Keep the last confirmed week visible. Replacing it with an empty
      // calendar makes a transport failure look like deleted reservations.
      if (sequence === requestSequence) loadError.value = true
    } finally {
      if (sequence === requestSequence) loading.value = false
    }
  }

  function shiftWeek(weeks: number) {
    weekStart.value =
      view.value === 'month'
        ? new Date(weekStart.value.getFullYear(), weekStart.value.getMonth() + weeks, 1)
        : addDays(weekStart.value, weeks * 7)
    return fetchWeek()
  }

  function goToday() {
    const current = today()
    weekStart.value =
      view.value === 'month'
        ? new Date(current.getFullYear(), current.getMonth(), 1)
        : getMonday(current)
    return fetchWeek()
  }

  function setView(next: CalendarView) {
    const current = today()
    const reference = days.value.some((day) => toDateStr(day) === toDateStr(current))
      ? current
      : weekStart.value
    view.value = next
    weekStart.value =
      next === 'month'
        ? new Date(reference.getFullYear(), reference.getMonth(), 1)
        : getMonday(reference)
    return fetchWeek()
  }

  async function editSchedule(id: number, request: ScheduleUpdateRequest) {
    if (moving.value || loading.value || loadError.value)
      throw new Error('예약을 새로고침한 뒤 다시 시도해주세요.')
    moving.value = true
    try {
      const updated = await scheduleApi.update(id, request)
      schedules.value = schedules.value.map((schedule) => (schedule.id === id ? updated : schedule))
      return updated
    } finally {
      moving.value = false
    }
  }

  /** Execute once per reservation; retain failures so only those need retrying. */
  async function batchSchedules(ids: number[], action: 'cancel' | 'shift', offsetDays = 0) {
    if (moving.value || loading.value || loadError.value) return { succeeded: [], failed: [] }
    moving.value = true
    const succeeded: number[] = []
    const failed: { id: number; title: string; message: string }[] = []
    try {
      for (const id of [...new Set(ids)]) {
        const schedule = schedules.value.find((item) => item.id === id)
        if (!schedule) continue
        try {
          if (schedule.status !== 'SCHEDULED')
            throw new Error('이미 실행된 예약은 변경할 수 없습니다.')
          if (action === 'cancel') {
            await scheduleApi.cancel(id)
            schedules.value = schedules.value.filter((item) => item.id !== id)
          } else {
            if (!Number.isInteger(offsetDays) || offsetDays === 0 || Math.abs(offsetDays) > 365) {
              throw new Error('이동할 일수를 확인해주세요.')
            }
            // Manipulate the server wall-clock in UTC-shaped calendar fields.
            // Browser DST must never shift a Korea-based publishing time.
            const next = new Date(`${schedule.scheduledAt}Z`)
            next.setUTCDate(next.getUTCDate() + offsetDays)
            const updated = await scheduleApi.update(id, {
              scheduledAt: next.toISOString().slice(0, 16),
            })
            schedules.value = schedules.value.map((item) => (item.id === id ? updated : item))
          }
          succeeded.push(id)
        } catch (error) {
          failed.push({
            id,
            title: schedule.videoTitle,
            message: error instanceof Error ? error.message : '예약 변경 실패',
          })
        }
      }
      batchFailures.value = failed
      return { succeeded, failed }
    } finally {
      moving.value = false
    }
  }

  /**
   * 드래그 이동 — 예약 시각을 newTime(그 주 안의 새 일시)으로 바꾼다.
   * 낙관적 갱신은 하지 않고 서버 응답으로 교체한다(실패를 숨기지 않기 위해).
   */
  async function moveSchedule(id: number, newTime: Date) {
    moving.value = true
    try {
      // The backend deliberately uses LocalDateTime (KST), not an instant.
      // Sending Date#toISOString() adds `Z`, which Jackson cannot bind to
      // LocalDateTime and also shifts the creator's intended wall-clock time.
      const updated = await scheduleApi.update(id, { scheduledAt: toDateTimeLocal(newTime) })
      schedules.value = schedules.value.map((s) => (s.id === id ? updated : s))
    } finally {
      moving.value = false
    }
  }

  /** 아직 실행되지 않은 예약을 서버에서 취소하고 캘린더에서도 제거한다. */
  async function cancelSchedule(id: number) {
    moving.value = true
    try {
      await scheduleApi.cancel(id)
      schedules.value = schedules.value.filter((schedule) => schedule.id !== id)
    } finally {
      moving.value = false
    }
  }

  /** 서버 추천을 캘린더에 표시한다. 추천 상태는 서버가 최종 권위다. */
  async function fetchOptimalRecommendations() {
    recommendationsError.value = false
    try {
      recommendations.value = await scheduleOptimizerApi.getRecommendations()
      return recommendations.value.length
    } catch (error) {
      recommendationsError.value = true
      throw error
    }
  }

  /** 추천 적용은 durable 예약 큐까지 갱신한 서버 응답으로 캘린더를 교체한다. */
  async function applyRecommendation(id: number) {
    applyingRecommendationId.value = id
    try {
      const applied = await scheduleOptimizerApi.applyRecommendation(id)
      recommendations.value = recommendations.value.map((recommendation) =>
        recommendation.id === id ? applied : recommendation,
      )
      await fetchWeek()
      return applied
    } finally {
      applyingRecommendationId.value = null
    }
  }

  return {
    weekStart,
    schedules,
    loading,
    loadError,
    moving,
    recommendations,
    recommendationsError,
    applyingRecommendationId,
    view,
    search,
    platformFilter,
    statusFilter,
    monthDays,
    visibleDays,
    filteredSchedules,
    batchFailures,
    setView,
    editSchedule,
    batchSchedules,
    days,
    fetchWeek,
    shiftWeek,
    goToday,
    moveSchedule,
    cancelSchedule,
    fetchOptimalRecommendations,
    applyRecommendation,
  }
})
