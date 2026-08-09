import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { scheduleApi } from '@/api/schedule'
import { scheduleOptimizerApi, type ScheduleRecommendation } from '@/api/scheduleOptimizer'
import type { Schedule } from '@/types/schedule'
import { toDateStr, toDateTimeLocal } from '@/utils/schedule'

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
  const weekStart = ref<Date>(getMonday(new Date()))
  const schedules = ref<Schedule[]>([])
  const loading = ref(false)
  const loadError = ref(false)
  const moving = ref(false)
  const recommendations = ref<ScheduleRecommendation[]>([])
  const recommendationsError = ref(false)
  const applyingRecommendationId = ref<number | null>(null)

  /** 주 단위 7일 (월~일) */
  const days = computed(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart.value, i)))

  async function fetchWeek() {
    loading.value = true
    loadError.value = false
    try {
      schedules.value = await scheduleApi.list({
        startDate: toDateStr(weekStart.value),
        endDate: toDateStr(addDays(weekStart.value, 6)),
      })
    } catch {
      // Keep the last confirmed week visible. Replacing it with an empty
      // calendar makes a transport failure look like deleted reservations.
      loadError.value = true
    } finally {
      loading.value = false
    }
  }

  function shiftWeek(weeks: number) {
    weekStart.value = addDays(weekStart.value, weeks * 7)
    return fetchWeek()
  }

  function goToday() {
    weekStart.value = getMonday(new Date())
    return fetchWeek()
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
