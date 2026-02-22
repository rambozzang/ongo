import { defineStore } from 'pinia'
import { ref } from 'vue'
import { trendApi } from '@/api/trend'
import type { Trend, TrendAnalysis, TrendAlert } from '@/types/trend'

export const useTrendStore = defineStore('trend', () => {
  const trends = ref<Trend[]>([])
  const analysis = ref<TrendAnalysis | null>(null)
  const alerts = ref<TrendAlert[]>([])
  const loading = ref(false)

  async function loadTrends(params?: { date?: string; category?: string; source?: string }) {
    loading.value = true
    try {
      trends.value = await trendApi.list(params)
    } finally {
      loading.value = false
    }
  }

  async function loadAnalysis(category?: string) {
    loading.value = true
    try {
      analysis.value = await trendApi.analyze(category)
    } finally {
      loading.value = false
    }
  }

  async function loadAlerts() {
    alerts.value = await trendApi.getAlerts()
  }

  return { trends, analysis, alerts, loading, loadTrends, loadAnalysis, loadAlerts }
})
