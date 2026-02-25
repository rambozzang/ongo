import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  ContentSlot,
  AiCalendarResult,
  AiCalendarGenerateRequest,
  SeasonEvent,
  AiCalendarViewMode,
} from '@/types/aiCalendar'
import { aiCalendarApi } from '@/api/aiCalendar'

export const useAiCalendarStore = defineStore('aiCalendar', () => {
  const calendarResult = ref<AiCalendarResult | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const viewMode = ref<AiCalendarViewMode>('weekly')
  const currentWeekStart = ref(getMonday(new Date()))

  const slots = computed<ContentSlot[]>(() => calendarResult.value?.slots ?? [])

  const seasonEvents = computed<SeasonEvent[]>(() => calendarResult.value?.seasonEvents ?? [])

  const weekSlots = computed(() => {
    const start = currentWeekStart.value
    const end = new Date(start)
    end.setDate(end.getDate() + 7)
    return slots.value.filter((slot) => {
      const d = new Date(slot.date)
      return d >= start && d < end
    })
  })

  const confirmedSlotIds = computed(() =>
    slots.value.filter((s) => s.status === 'confirmed').map((s) => s.id),
  )

  async function generateCalendar(request: AiCalendarGenerateRequest) {
    loading.value = true
    error.value = null
    try {
      calendarResult.value = await aiCalendarApi.generate(request)
      return calendarResult.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'AI 캘린더 생성 중 오류가 발생했습니다'
      return null
    } finally {
      loading.value = false
    }
  }

  async function regenerateSlots(slotIds: string[]) {
    if (!calendarResult.value) return null
    loading.value = true
    error.value = null
    try {
      calendarResult.value = await aiCalendarApi.regenerateSlots(
        calendarResult.value.id,
        slotIds,
      )
      return calendarResult.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : '재생성 중 오류가 발생했습니다'
      return null
    } finally {
      loading.value = false
    }
  }

  async function applyToSchedule(slotIds: string[]) {
    if (!calendarResult.value) return null
    loading.value = true
    error.value = null
    try {
      const result = await aiCalendarApi.applyToSchedule({
        calendarId: calendarResult.value.id,
        slotIds,
      })
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : '스케줄 적용 중 오류가 발생했습니다'
      return null
    } finally {
      loading.value = false
    }
  }

  function updateSlot(slotId: string, updates: Partial<ContentSlot>) {
    if (!calendarResult.value) return
    const idx = calendarResult.value.slots.findIndex((s) => s.id === slotId)
    if (idx !== -1) {
      calendarResult.value.slots[idx] = {
        ...calendarResult.value.slots[idx],
        ...updates,
        status: 'edited',
      }
    }
  }

  function toggleSlotConfirm(slotId: string) {
    if (!calendarResult.value) return
    const idx = calendarResult.value.slots.findIndex((s) => s.id === slotId)
    if (idx !== -1) {
      const slot = calendarResult.value.slots[idx]
      calendarResult.value.slots[idx] = {
        ...slot,
        status: slot.status === 'confirmed' ? 'suggested' : 'confirmed',
      }
    }
  }

  function clearResult() {
    calendarResult.value = null
    error.value = null
  }

  function setViewMode(mode: AiCalendarViewMode) {
    viewMode.value = mode
  }

  function setWeekStart(date: Date) {
    currentWeekStart.value = getMonday(date)
  }

  function previousWeek() {
    const d = new Date(currentWeekStart.value)
    d.setDate(d.getDate() - 7)
    currentWeekStart.value = d
  }

  function nextWeek() {
    const d = new Date(currentWeekStart.value)
    d.setDate(d.getDate() + 7)
    currentWeekStart.value = d
  }

  return {
    calendarResult,
    loading,
    error,
    viewMode,
    currentWeekStart,
    slots,
    seasonEvents,
    weekSlots,
    confirmedSlotIds,
    generateCalendar,
    regenerateSlots,
    applyToSchedule,
    updateSlot,
    toggleSlotConfirm,
    clearResult,
    setViewMode,
    setWeekStart,
    previousWeek,
    nextWeek,
  }
})

function getMonday(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  d.setDate(diff)
  d.setHours(0, 0, 0, 0)
  return d
}
