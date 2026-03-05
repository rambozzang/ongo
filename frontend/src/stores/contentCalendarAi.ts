import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  CalendarSuggestion,
  CalendarSlot,
  ContentCalendarAiSummary,
  GenerateCalendarRequest,
} from '@/types/contentCalendarAi'
import { contentCalendarAiApi } from '@/api/contentCalendarAi'

export const useContentCalendarAiStore = defineStore('contentCalendarAi', () => {
  const suggestions = ref<CalendarSuggestion[]>([])
  const slots = ref<CalendarSlot[]>([])
  const summary = ref<ContentCalendarAiSummary>({
    totalSuggestions: 0,
    acceptedCount: 0,
    avgConfidence: 0,
    bestTimeSlot: '-',
    topPlatform: '-',
  })
  const isLoading = ref(false)
  const isGenerating = ref(false)

  async function fetchSuggestions(status?: string) {
    isLoading.value = true
    try {
      suggestions.value = await contentCalendarAiApi.getSuggestions(status)
    } catch {
      suggestions.value = []
    } finally {
      isLoading.value = false
    }
  }

  async function generate(request: GenerateCalendarRequest) {
    isGenerating.value = true
    try {
      const result = await contentCalendarAiApi.generate(request)
      suggestions.value = [...result, ...suggestions.value]
      return result
    } finally {
      isGenerating.value = false
    }
  }

  async function fetchSlots(startDate: string, endDate: string) {
    try {
      slots.value = await contentCalendarAiApi.getSlots(startDate, endDate)
    } catch {
      slots.value = []
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentCalendarAiApi.getSummary()
    } catch {
      summary.value = { totalSuggestions: 0, acceptedCount: 0, avgConfidence: 0, bestTimeSlot: '-', topPlatform: '-' }
    }
  }

  return { suggestions, slots, summary, isLoading, isGenerating, fetchSuggestions, generate, fetchSlots, fetchSummary }
})
