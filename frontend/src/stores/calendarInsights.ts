import { defineStore } from 'pinia'
import { ref } from 'vue'
import { calendarInsightsApi } from '@/api/calendarInsights'
import type {
  CalendarInsight,
  OptimalTimeSlot,
  UploadPattern,
  CalendarInsightsSummary,
} from '@/types/calendarInsights'

export const useCalendarInsightsStore = defineStore('calendarInsights', () => {
  const insights = ref<CalendarInsight[]>([])
  const optimalTimeSlots = ref<OptimalTimeSlot[]>([])
  const uploadPatterns = ref<UploadPattern[]>([])
  const summary = ref<CalendarInsightsSummary>({
    totalUploads: 0,
    avgUploadsPerWeek: 0,
    bestDay: '',
    bestHour: 0,
    consistencyScore: 0,
  })
  const isLoading = ref(false)

  async function fetchInsights(year: number, month: number) {
    isLoading.value = true
    try {
      insights.value = await calendarInsightsApi.getInsights(year, month)
    } catch {
      insights.value = []
    } finally {
      isLoading.value = false
    }
  }

  async function fetchOptimalTimeSlots(platform?: string) {
    try {
      optimalTimeSlots.value = await calendarInsightsApi.getOptimalTimeSlots(platform)
    } catch {
      optimalTimeSlots.value = []
    }
  }

  async function fetchUploadPatterns() {
    try {
      uploadPatterns.value = await calendarInsightsApi.getUploadPatterns()
    } catch {
      uploadPatterns.value = []
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await calendarInsightsApi.getSummary()
    } catch {
      // keep default empty summary
    }
  }

  return {
    insights,
    optimalTimeSlots,
    uploadPatterns,
    summary,
    isLoading,
    fetchInsights,
    fetchOptimalTimeSlots,
    fetchUploadPatterns,
    fetchSummary,
  }
})
