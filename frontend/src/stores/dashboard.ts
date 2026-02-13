import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DashboardKpi, TrendDataPoint, PlatformComparison, TopVideo } from '@/types/analytics'
import type { Video } from '@/types/video'
import type { Schedule } from '@/types/schedule'
import { analyticsApi } from '@/api/analytics'
import { videoApi } from '@/api/video'
import { scheduleApi } from '@/api/schedule'

export const useDashboardStore = defineStore('dashboard', () => {
  const kpi = ref<DashboardKpi | null>(null)
  const trendData = ref<TrendDataPoint[]>([])
  const platformComparison = ref<PlatformComparison[]>([])
  const recentVideos = ref<Video[]>([])
  const upcomingSchedules = ref<Schedule[]>([])
  const topVideos = ref<TopVideo[]>([])
  const isLoadingDashboard = ref(false)
  const period = ref<'7d' | '30d'>('7d')

  // Backwards-compatible loading ref
  const loading = isLoadingDashboard

  async function fetchDashboard() {
    isLoadingDashboard.value = true
    try {
      const results = await Promise.allSettled([
        analyticsApi.dashboard(period.value),
        analyticsApi.trends(period.value),
        analyticsApi.platformComparison(period.value),
        videoApi.list({ page: 0, size: 5, sort: 'createdAt', direction: 'DESC' }),
        scheduleApi.list({ status: 'SCHEDULED' }),
        analyticsApi.topVideos('30d'),
      ])

      if (results[0].status === 'fulfilled') kpi.value = results[0].value
      if (results[1].status === 'fulfilled') trendData.value = Array.isArray(results[1].value) ? results[1].value : []
      if (results[2].status === 'fulfilled') platformComparison.value = Array.isArray(results[2].value) ? results[2].value : []
      if (results[3].status === 'fulfilled') recentVideos.value = results[3].value?.content ?? []
      if (results[4].status === 'fulfilled') {
        const schedules = results[4].value as Schedule[] | { content: Schedule[] }
        // Handle both array and object with content property
        const scheduleList = Array.isArray(schedules) ? schedules : (schedules?.content ?? [])
        upcomingSchedules.value = scheduleList.slice(0, 3)
      }
      if (results[5].status === 'fulfilled') topVideos.value = Array.isArray(results[5].value) ? results[5].value : []
    } catch {
      // dashboard fetch failed silently - partial data may be available
    } finally {
      isLoadingDashboard.value = false
    }
  }

  function setPeriod(p: '7d' | '30d') {
    period.value = p
    fetchDashboard()
  }

  return {
    kpi,
    trendData,
    platformComparison,
    recentVideos,
    upcomingSchedules,
    topVideos,
    loading,
    isLoadingDashboard,
    period,
    fetchDashboard,
    setPeriod,
  }
})
