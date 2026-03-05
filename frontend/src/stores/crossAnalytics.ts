import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  CrossPlatformReport,
  PlatformPerformanceSummary,
} from '@/types/crossAnalytics'
import { crossAnalyticsApi } from '@/api/crossAnalytics'

export const useCrossAnalyticsStore = defineStore('crossAnalytics', () => {
  const report = ref<CrossPlatformReport | null>(null)
  const selectedPeriod = ref('7d')
  const loading = ref(false)
  const error = ref<string | null>(null)
  const activeTab = ref<'overview' | 'contents' | 'audience'>('overview')

  const bestPlatform = computed<PlatformPerformanceSummary | null>(() => {
    if (!report.value) return null
    return [...report.value.platformSummaries].sort((a, b) => b.totalViews - a.totalViews)[0] ?? null
  })

  const totalViewsAllPlatforms = computed(() => {
    if (!report.value) return 0
    return report.value.platformSummaries.reduce((sum, p) => sum + p.totalViews, 0)
  })

  const platformViewShare = computed(() => {
    if (!report.value || totalViewsAllPlatforms.value === 0) return []
    return report.value.platformSummaries.map((p) => ({
      platform: p.platform,
      share: Math.round((p.totalViews / totalViewsAllPlatforms.value) * 100),
    }))
  })

  async function fetchReport(period?: string) {
    loading.value = true
    error.value = null
    if (period) selectedPeriod.value = period
    try {
      report.value = await crossAnalyticsApi.getReport(selectedPeriod.value)
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      loading.value = false
    }
  }

  return {
    report,
    selectedPeriod,
    loading,
    error,
    activeTab,
    bestPlatform,
    totalViewsAllPlatforms,
    platformViewShare,
    fetchReport,
  }
})
