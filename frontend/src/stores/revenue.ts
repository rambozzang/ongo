import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import type {
  RevenueSummary,
  PlatformRevenueData,
  RevenueTrendPoint,
  CpmRpmResponse,
  BrandDealRevenueResponse,
  RevenueInsight,
  RevenueAlertConfig,
} from '@/types/revenue'
import { revenueApi } from '@/api/revenue'

export interface RevenueData {
  period: string
  /** API가 실제로 반환한 플랫폼별 수익. 하드코딩된 플랫폼 목록을 사용하지 않는다. */
  platforms: Record<string, number>
  total: number
}

export interface RevenueSummaryLocal {
  totalRevenue: number
  monthlyGrowth: number
  averageRPM: number
  topPlatform: Platform
  topPlatformRevenue: number
}

function percentageChange(current: number, previous: number): number {
  // A zero baseline has no meaningful percentage denominator. Returning zero
  // keeps the UI truthful and avoids rendering Infinity/NaN for a new revenue
  // stream whose first non-zero period has just arrived.
  if (previous === 0) return 0
  return ((current - previous) / previous) * 100
}

export const useRevenueStore = defineStore('revenue', () => {
  const monthlyRevenue = shallowRef<RevenueData[]>([])
  const summary = ref<RevenueSummaryLocal>({
    totalRevenue: 0,
    monthlyGrowth: 0,
    averageRPM: 0,
    topPlatform: 'YOUTUBE',
    topPlatformRevenue: 0,
  })
  const loading = ref(false)
  const loadError = ref(false)
  const apiSummary = ref<RevenueSummary | null>(null)
  const apiTrends = shallowRef<RevenueTrendPoint[]>([])
  const apiPlatformRevenue = ref<PlatformRevenueData | null>(null)
  const cpmRpmData = ref<CpmRpmResponse | null>(null)
  const brandDealData = ref<BrandDealRevenueResponse | null>(null)
  const cpmRpmLoading = ref(false)
  const brandDealLoading = ref(false)
  const cpmRpmError = ref<string | null>(null)
  const brandDealError = ref<string | null>(null)

  // AI 인사이트
  const revenueInsights = shallowRef<RevenueInsight[]>([])
  const insightsLoading = ref(false)
  const insightsError = ref<string | null>(null)
  const generateInsightLoading = ref(false)

  // 알림 설정
  const alertConfigs = shallowRef<RevenueAlertConfig[]>([])
  const alertConfigLoading = ref(false)
  const alertConfigError = ref<string | null>(null)

  function calculateSummary(data: RevenueData[]): RevenueSummaryLocal {
    if (data.length === 0) {
      return {
        totalRevenue: 0,
        monthlyGrowth: 0,
        averageRPM: 0,
        topPlatform: 'YOUTUBE',
        topPlatformRevenue: 0,
      }
    }

    const totalRevenue = data.reduce((sum, item) => sum + item.total, 0)
    let monthlyGrowth = 0
    if (data.length >= 2) {
      const lastMonth = data[data.length - 1].total
      const previousMonth = data[data.length - 2].total
      monthlyGrowth = percentageChange(lastMonth, previousMonth)
    }

    const averageRPM = Math.floor(totalRevenue / (data.length * 10000))
    const platformTotals = data.reduce<Record<string, number>>((totals, item) => {
      for (const [platform, revenue] of Object.entries(item.platforms)) {
        totals[platform] = (totals[platform] ?? 0) + revenue
      }
      return totals
    }, {})

    const topPlatformEntry = Object.entries(platformTotals).reduce(
      (max, [platform, revenue]) => (revenue > max.revenue ? { platform, revenue } : max),
      { platform: 'YOUTUBE', revenue: 0 },
    )

    const topPlatform = Object.prototype.hasOwnProperty.call(PLATFORM_CONFIG, topPlatformEntry.platform)
      ? topPlatformEntry.platform as Platform
      : 'YOUTUBE'

    return {
      totalRevenue,
      monthlyGrowth: Math.round(monthlyGrowth * 100) / 100,
      averageRPM,
      topPlatform,
      topPlatformRevenue: topPlatformEntry.revenue,
    }
  }

  const totalAnnualRevenue = computed(() =>
    monthlyRevenue.value.reduce((sum, item) => sum + item.total, 0),
  )

  const platformBreakdown = computed(() => {
    const total = totalAnnualRevenue.value
    if (total === 0) return []

    const platformTotals = monthlyRevenue.value.reduce<Record<string, number>>((totals, item) => {
      for (const [platform, revenue] of Object.entries(item.platforms)) {
        // Naver Clip has no public upload/analytics API. Do not present legacy
        // rows as a currently supported revenue source.
        if (platform !== 'NAVER_CLIP') {
          totals[platform] = (totals[platform] ?? 0) + revenue
        }
      }
      return totals
    }, {})

    return Object.entries(platformTotals).map(([platform, revenue]) => ({
      platform: platform as Platform,
      revenue,
      percentage: Math.round((revenue / total) * 100 * 100) / 100,
    }))
  })

  const growthTrend = computed(() => {
    if (monthlyRevenue.value.length < 2) return []

    const recent = monthlyRevenue.value.slice(-6)
    return recent.map((item, index) => {
      if (index === 0) return { period: item.period, growth: 0 }
      const previous = recent[index - 1].total
      const growth = percentageChange(item.total, previous)
      return {
        period: item.period,
        growth: Math.round(growth * 100) / 100,
      }
    })
  })

  async function fetchRevenue() {
    loading.value = true
    loadError.value = false
    try {
      const results = await Promise.allSettled([
        revenueApi.summary('30d'),
        revenueApi.trends('30d'),
        revenueApi.platformRevenue('30d'),
      ])

      if (results[0].status === 'fulfilled') apiSummary.value = results[0].value
      if (results[1].status === 'fulfilled') {
        apiTrends.value = results[1].value.data
        // Build monthlyRevenue from trends API data grouped by date
        const trendsByDate = new Map<string, RevenueData>()
        for (const point of results[1].value.data) {
          const period = point.date
          if (!trendsByDate.has(period)) {
            trendsByDate.set(period, {
              period,
              platforms: {},
              total: 0,
            })
          }
          const entry = trendsByDate.get(period)!
          const amount = point.revenueKrw ?? 0
          const platform = point.platform?.toUpperCase() ?? 'UNKNOWN'
          // Legacy Naver Clip rows must not be presented as a supported,
          // provider-backed revenue source because no public analytics API is
          // available for it.
          if (platform === 'NAVER_CLIP') continue
          entry.platforms[platform] = (entry.platforms[platform] ?? 0) + amount
          entry.total += amount
        }
        monthlyRevenue.value = Array.from(trendsByDate.values())
      }
      if (results[2].status === 'fulfilled') apiPlatformRevenue.value = results[2].value

      summary.value = calculateSummary(monthlyRevenue.value)
      loadError.value = results.some((result) => result.status === 'rejected')
    } catch {
      // Keep the last successful values; an outage must not look like zero revenue.
      loadError.value = true
    } finally {
      loading.value = false
    }
  }

  async function fetchCpmRpm(period = '30d') {
    cpmRpmLoading.value = true
    cpmRpmError.value = null
    try {
      cpmRpmData.value = await revenueApi.cpmRpm(period)
    } catch (error) {
      cpmRpmError.value =
        error instanceof Error ? error.message : 'CPM·RPM 데이터를 불러오지 못했습니다.'
    } finally {
      cpmRpmLoading.value = false
    }
  }

  async function fetchBrandDealRevenue(period = '90d') {
    brandDealLoading.value = true
    brandDealError.value = null
    try {
      brandDealData.value = await revenueApi.brandDealRevenue(period)
    } catch (error) {
      brandDealError.value =
        error instanceof Error ? error.message : '브랜드딜 수익을 불러오지 못했습니다.'
    } finally {
      brandDealLoading.value = false
    }
  }

  async function fetchInsights() {
    insightsLoading.value = true
    insightsError.value = null
    try {
      // 백엔드가 { insights, totalElements, page, size } 래퍼 반환
      const response = await revenueApi.insights()
      revenueInsights.value = response.insights
    } catch (error) {
      insightsError.value =
        error instanceof Error ? error.message : '수익 인사이트를 불러오지 못했습니다.'
    } finally {
      insightsLoading.value = false
    }
  }

  async function generateInsight() {
    generateInsightLoading.value = true
    try {
      const insight = await revenueApi.generateInsight()
      revenueInsights.value = [insight, ...revenueInsights.value]
      return insight
    } finally {
      generateInsightLoading.value = false
    }
  }

  async function fetchAlertConfigs() {
    alertConfigLoading.value = true
    alertConfigError.value = null
    try {
      // 백엔드가 { configs: [...] } 래퍼 반환
      const response = await revenueApi.alertConfigs()
      alertConfigs.value = response.configs
    } catch (error) {
      alertConfigError.value =
        error instanceof Error ? error.message : '수익 알림 설정을 불러오지 못했습니다.'
    } finally {
      alertConfigLoading.value = false
    }
  }

  async function saveAlertConfig(config: Omit<RevenueAlertConfig, 'id'>) {
    const saved = await revenueApi.saveAlertConfig(config)
    alertConfigs.value = [...alertConfigs.value, saved]
    return saved
  }

  async function updateAlertConfig(id: number, config: Partial<RevenueAlertConfig>) {
    const updated = await revenueApi.updateAlertConfig(id, config)
    alertConfigs.value = alertConfigs.value.map((c) => (c.id === id ? updated : c))
    return updated
  }

  async function deleteAlertConfig(id: number) {
    await revenueApi.deleteAlertConfig(id)
    alertConfigs.value = alertConfigs.value.filter((c) => c.id !== id)
  }

  return {
    monthlyRevenue,
    summary,
    loading,
    loadError,
    totalAnnualRevenue,
    platformBreakdown,
    growthTrend,
    apiSummary,
    apiTrends,
    apiPlatformRevenue,
    fetchRevenue,
    cpmRpmData,
    brandDealData,
    cpmRpmLoading,
    brandDealLoading,
    cpmRpmError,
    brandDealError,
    fetchCpmRpm,
    fetchBrandDealRevenue,
    revenueInsights,
    insightsLoading,
    insightsError,
    generateInsightLoading,
    alertConfigs,
    alertConfigLoading,
    alertConfigError,
    fetchInsights,
    generateInsight,
    fetchAlertConfigs,
    saveAlertConfig,
    updateAlertConfig,
    deleteAlertConfig,
  }
})
