import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RevenueStream, RevenueProjection, RevenueAnalyzerSummary } from '@/types/revenueAnalyzer'
import { revenueAnalyzerApi } from '@/api/revenueAnalyzer'

export const useRevenueAnalyzerStore = defineStore('revenueAnalyzer', () => {
  const streams = ref<RevenueStream[]>([])
  const projections = ref<RevenueProjection[]>([])
  const summary = ref<RevenueAnalyzerSummary | null>(null)
  const loading = ref(false)

  const fetchStreams = async () => {
    loading.value = true
    try {
      streams.value = await revenueAnalyzerApi.getStreams()
    } catch {
      streams.value = []
    } finally {
      loading.value = false
    }
  }

  const fetchProjections = async (channelId: number) => {
    try {
      projections.value = await revenueAnalyzerApi.getProjections(channelId)
    } catch {
      projections.value = []
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await revenueAnalyzerApi.getSummary()
    } catch {
      summary.value = null
    }
  }

  return { streams, projections, summary, loading, fetchStreams, fetchProjections, fetchSummary }
})
