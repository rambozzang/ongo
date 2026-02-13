import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  DashboardKpi,
  TrendDataPoint,
  PlatformComparison,
  HeatmapData,
  TopVideo,
  AnalyticsPeriod,
} from '@/types/analytics'
import { analyticsApi } from '@/api/analytics'

export const useAnalyticsStore = defineStore('analytics', () => {
  const kpi = ref<DashboardKpi | null>(null)
  const trendData = ref<TrendDataPoint[]>([])
  const platformComparison = ref<PlatformComparison[]>([])
  const heatmapData = ref<HeatmapData[]>([])
  const postingHeatmapData = ref<HeatmapData[]>([])
  const topVideos = ref<TopVideo[]>([])
  const loading = ref(false)
  const period = ref<AnalyticsPeriod>('7d')

  async function fetchAnalytics() {
    loading.value = true
    try {
      const results = await Promise.allSettled([
        analyticsApi.dashboard(period.value),
        analyticsApi.trends(period.value),
        analyticsApi.platformComparison(period.value),
        analyticsApi.heatmap(),
        analyticsApi.topVideos(period.value),
      ])

      if (results[0].status === 'fulfilled') kpi.value = results[0].value
      if (results[1].status === 'fulfilled') trendData.value = results[1].value
      if (results[2].status === 'fulfilled') platformComparison.value = results[2].value
      if (results[3].status === 'fulfilled') {
        heatmapData.value = results[3].value
        // Use real heatmap data for posting heatmap as well
        postingHeatmapData.value = results[3].value
      }
      if (results[4].status === 'fulfilled') topVideos.value = results[4].value

      // 실패한 요청 로깅
      results.forEach((result, index) => {
        if (result.status === 'rejected') {
          const labels = ['KPI', 'Trends', 'Platform', 'Heatmap', 'TopVideos']
          console.error(`Analytics ${labels[index]} fetch failed:`, result.reason)
        }
      })
    } catch {
      // analytics fetch failed silently
    } finally {
      loading.value = false
    }
  }

  function setPeriod(p: AnalyticsPeriod) {
    period.value = p
    fetchAnalytics()
  }

  return {
    kpi,
    trendData,
    platformComparison,
    heatmapData,
    postingHeatmapData,
    topVideos,
    loading,
    period,
    fetchAnalytics,
    setPeriod,
  }
})
