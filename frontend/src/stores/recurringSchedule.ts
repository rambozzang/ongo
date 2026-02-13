import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type {
  RecurringRule,
  ContentQueueSlot,
  RecurringScheduleStats,
  DayOfWeek,
} from '@/types/recurringSchedule'
import { recurringScheduleApi } from '@/api/recurringSchedule'
import type { RecurringScheduleResponse } from '@/api/recurringSchedule'

function mapApiSchedule(s: RecurringScheduleResponse): RecurringRule {
  return {
    id: String(s.id),
    name: s.name,
    pattern: s.frequency.toLowerCase() as RecurringRule['pattern'],
    daysOfWeek: s.dayOfWeek !== null ? [s.dayOfWeek as DayOfWeek] : [],
    dayOfMonth: s.dayOfMonth ?? undefined,
    timeSlot: s.timeOfDay,
    platforms: s.platforms,
    isActive: s.isActive,
    createdAt: s.createdAt ?? new Date().toISOString(),
    updatedAt: s.updatedAt ?? new Date().toISOString(),
  }
}

export const useRecurringScheduleStore = defineStore('recurringSchedule', () => {
  // ── State ──────────────────────────────────────────────────────────
  const rules = ref<RecurringRule[]>([])
  const contentQueue = ref<ContentQueueSlot[]>([])
  const loading = ref(false)

  // ── Getters ────────────────────────────────────────────────────────
  const activeRules = computed<RecurringRule[]>(() =>
    rules.value.filter((r) => r.isActive),
  )

  const upcomingSlots = computed<ContentQueueSlot[]>(() => {
    const now = new Date().toISOString()
    return [...contentQueue.value]
      .filter((s) => s.scheduledDate >= now)
      .sort((a, b) => a.scheduledDate.localeCompare(b.scheduledDate))
  })

  const emptySlots = computed<ContentQueueSlot[]>(() =>
    upcomingSlots.value.filter((s) => s.status === 'empty'),
  )

  const stats = computed<RecurringScheduleStats>(() => ({
    totalRules: rules.value.length,
    activeRules: activeRules.value.length,
    upcomingSlots: upcomingSlots.value.length,
    emptySlots: emptySlots.value.length,
  }))

  // ── Helpers ────────────────────────────────────────────────────────
  function generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
  }

  function computeOccurrences(rule: RecurringRule, count: number, from: Date = new Date()): Date[] {
    const dates: Date[] = []
    const [hours, minutes] = rule.timeSlot.split(':').map(Number)
    const cursor = new Date(from)
    cursor.setHours(hours, minutes, 0, 0)

    if (cursor <= from) {
      cursor.setDate(cursor.getDate() + 1)
    }

    const maxIterations = count * 60
    let iterations = 0

    while (dates.length < count && iterations < maxIterations) {
      iterations++
      const dayOfWeek = cursor.getDay() as DayOfWeek

      let match = false
      switch (rule.pattern) {
        case 'daily':
          match = true
          break
        case 'weekly':
          match = rule.daysOfWeek.includes(dayOfWeek)
          break
        case 'biweekly': {
          if (rule.daysOfWeek.includes(dayOfWeek)) {
            const epochStart = new Date(2024, 0, 1)
            const diffDays = Math.floor(
              (cursor.getTime() - epochStart.getTime()) / (24 * 60 * 60 * 1000),
            )
            const weekNumber = Math.floor(diffDays / 7)
            match = weekNumber % 2 === 0
          }
          break
        }
        case 'monthly':
          if (rule.nthWeekday) {
            const targetDay = rule.nthWeekday.day
            const targetWeek = rule.nthWeekday.week
            if (cursor.getDay() === targetDay) {
              const dayOfMonth = cursor.getDate()
              const weekInMonth = Math.ceil(dayOfMonth / 7)
              match = weekInMonth === targetWeek
            }
          } else if (rule.dayOfMonth !== undefined) {
            match = cursor.getDate() === rule.dayOfMonth
          }
          break
      }

      if (match) {
        const occurrence = new Date(cursor)
        occurrence.setHours(hours, minutes, 0, 0)
        dates.push(occurrence)
      }

      cursor.setDate(cursor.getDate() + 1)
    }

    return dates
  }

  // ── Actions ────────────────────────────────────────────────────────
  async function fetchRules() {
    loading.value = true
    try {
      const data = await recurringScheduleApi.list()
      rules.value = data.map(mapApiSchedule)
      // Generate slots for active rules
      for (const rule of rules.value) {
        if (rule.isActive) generateSlots(rule.id, 2)
      }
    } catch (e) {
      console.error('Failed to fetch recurring schedules:', e)
    } finally {
      loading.value = false
    }
  }

  async function addRule(rule: Omit<RecurringRule, 'id' | 'createdAt' | 'updatedAt'>): Promise<RecurringRule> {
    try {
      const data = await recurringScheduleApi.create({
        name: rule.name,
        frequency: rule.pattern.toUpperCase(),
        dayOfWeek: rule.daysOfWeek[0] ?? undefined,
        dayOfMonth: rule.dayOfMonth,
        timeOfDay: rule.timeSlot,
        platforms: rule.platforms,
        isActive: rule.isActive,
      })
      const newRule = mapApiSchedule(data)
      rules.value.push(newRule)
      generateSlots(newRule.id, 2)
      return newRule
    } catch (e) {
      console.error('Failed to create recurring schedule:', e)
      const now = new Date().toISOString()
      const newRule: RecurringRule = {
        ...rule,
        id: generateId(),
        createdAt: now,
        updatedAt: now,
      }
      rules.value.push(newRule)
      generateSlots(newRule.id, 2)
      return newRule
    }
  }

  async function updateRule(id: string, updates: Partial<Omit<RecurringRule, 'id' | 'createdAt'>>): Promise<void> {
    try {
      await recurringScheduleApi.update(Number(id), {
        name: updates.name,
        frequency: updates.pattern?.toUpperCase(),
        dayOfWeek: updates.daysOfWeek?.[0],
        dayOfMonth: updates.dayOfMonth,
        timeOfDay: updates.timeSlot,
        platforms: updates.platforms,
      })
    } catch (e) {
      console.error('Failed to update recurring schedule:', e)
    }
    const index = rules.value.findIndex((r) => r.id === id)
    if (index !== -1) {
      rules.value[index] = {
        ...rules.value[index],
        ...updates,
        updatedAt: new Date().toISOString(),
      }
      contentQueue.value = contentQueue.value.filter(
        (s) => s.ruleId !== id || s.status !== 'empty',
      )
      generateSlots(id, 2)
    }
  }

  async function deleteRule(id: string): Promise<void> {
    try {
      await recurringScheduleApi.delete(Number(id))
    } catch (e) {
      console.error('Failed to delete recurring schedule:', e)
    }
    rules.value = rules.value.filter((r) => r.id !== id)
    contentQueue.value = contentQueue.value.filter((s) => s.ruleId !== id)
  }

  async function toggleRule(id: string): Promise<void> {
    try {
      await recurringScheduleApi.toggle(Number(id))
    } catch (e) {
      console.error('Failed to toggle recurring schedule:', e)
    }
    const rule = rules.value.find((r) => r.id === id)
    if (rule) {
      rule.isActive = !rule.isActive
      rule.updatedAt = new Date().toISOString()
      if (!rule.isActive) {
        contentQueue.value = contentQueue.value.filter(
          (s) => !(s.ruleId === id && s.status === 'empty'),
        )
      } else {
        generateSlots(id, 2)
      }
    }
  }

  function assignVideo(slotId: string, videoId: number, videoTitle: string, videoThumbnail?: string): void {
    const slot = contentQueue.value.find((s) => s.id === slotId)
    if (slot) {
      slot.videoId = videoId
      slot.videoTitle = videoTitle
      slot.videoThumbnail = videoThumbnail
      slot.status = 'assigned'
    }
  }

  function unassignVideo(slotId: string): void {
    const slot = contentQueue.value.find((s) => s.id === slotId)
    if (slot) {
      slot.videoId = undefined
      slot.videoTitle = undefined
      slot.videoThumbnail = undefined
      slot.status = 'empty'
    }
  }

  function generateSlots(ruleId: string, weeks: number): void {
    const rule = rules.value.find((r) => r.id === ruleId)
    if (!rule || !rule.isActive) return

    const now = new Date()
    const endDate = new Date(now)
    endDate.setDate(endDate.getDate() + weeks * 7)

    const occurrences = computeOccurrences(rule, weeks * 7, now).filter(
      (d) => d <= endDate,
    )

    const existingDates = new Set(
      contentQueue.value
        .filter((s) => s.ruleId === ruleId)
        .map((s) => s.scheduledDate),
    )

    for (const date of occurrences) {
      const isoDate = date.toISOString()
      if (!existingDates.has(isoDate)) {
        contentQueue.value.push({
          id: generateId(),
          ruleId,
          scheduledDate: isoDate,
          status: 'empty',
        })
      }
    }
  }

  // ── Persistence ────────────────────────────────────────────────────
  function saveToLocalStorage(): void {
    localStorage.setItem('ongo_recurring_rules', JSON.stringify(rules.value))
    localStorage.setItem('ongo_recurring_queue', JSON.stringify(contentQueue.value))
  }

  function loadFromLocalStorage(): void {
    const rulesData = localStorage.getItem('ongo_recurring_rules')
    const queueData = localStorage.getItem('ongo_recurring_queue')

    if (rulesData) {
      rules.value = JSON.parse(rulesData)
    }
    if (queueData) {
      contentQueue.value = JSON.parse(queueData)
    }
  }

  function initialize(): void {
    loadFromLocalStorage()
    for (const rule of rules.value) {
      if (rule.isActive) {
        generateSlots(rule.id, 2)
      }
    }
  }

  return {
    // state
    rules,
    contentQueue,
    loading,
    // getters
    activeRules,
    upcomingSlots,
    emptySlots,
    stats,
    // actions
    fetchRules,
    addRule,
    updateRule,
    deleteRule,
    toggleRule,
    assignVideo,
    unassignVideo,
    generateSlots,
    initialize,
    // helpers exposed for preview in form
    computeOccurrences,
    saveToLocalStorage,
    loadFromLocalStorage,
  }
})
