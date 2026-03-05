import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { OptimalSlot, ScheduleRecommendation, ScheduleOptimizerSummary } from '@/types/scheduleOptimizer'
import { scheduleOptimizerApi } from '@/api/scheduleOptimizer'

export const useScheduleOptimizerStore = defineStore('scheduleOptimizer', () => {
  const slots = ref<OptimalSlot[]>([])
  const recommendations = ref<ScheduleRecommendation[]>([])
  const summary = ref<ScheduleOptimizerSummary | null>(null)
  const loading = ref(false)

  const fetchSlots = async (platform: string) => {
    loading.value = true
    try {
      slots.value = await scheduleOptimizerApi.getSlots(platform)
    } catch {
      slots.value = []
    } finally {
      loading.value = false
    }
  }

  const fetchRecommendations = async () => {
    try {
      recommendations.value = await scheduleOptimizerApi.getRecommendations()
    } catch {
      recommendations.value = []
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await scheduleOptimizerApi.getSummary()
    } catch {
      summary.value = null
    }
  }

  return { slots, recommendations, summary, loading, fetchSlots, fetchRecommendations, fetchSummary }
})
