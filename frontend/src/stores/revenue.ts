import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Platform } from '@/types/channel'
import type { RevenueSummary, PlatformRevenueData, RevenueTrendPoint } from '@/types/revenue'
import { revenueApi } from '@/api/revenue'

export interface RevenueData {
  period: string
  youtube: number
  tiktok: number
  instagram: number
  naverClip: number
  total: number
}

export interface RevenueSummaryLocal {
  totalRevenue: number
  monthlyGrowth: number
  averageRPM: number
  topPlatform: Platform
  topPlatformRevenue: number
}

export const useRevenueStore = defineStore('revenue', () => {
  const monthlyRevenue = ref<RevenueData[]>([])
  const summary = ref<RevenueSummaryLocal>({
    totalRevenue: 0,
    monthlyGrowth: 0,
    averageRPM: 0,
    topPlatform: 'YOUTUBE',
    topPlatformRevenue: 0,
  })
  const loading = ref(false)
  const apiSummary = ref<RevenueSummary | null>(null)
  const apiTrends = ref<RevenueTrendPoint[]>([])
  const apiPlatformRevenue = ref<PlatformRevenueData | null>(null)

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
      monthlyGrowth = ((lastMonth - previousMonth) / previousMonth) * 100
    }

    const averageRPM = Math.floor(totalRevenue / (data.length * 10000))
    const platformTotals = {
      youtube: data.reduce((sum, item) => sum + item.youtube, 0),
      tiktok: data.reduce((sum, item) => sum + item.tiktok, 0),
      instagram: data.reduce((sum, item) => sum + item.instagram, 0),
      naverClip: data.reduce((sum, item) => sum + item.naverClip, 0),
    }

    const topPlatformEntry = Object.entries(platformTotals).reduce((max, [platform, revenue]) =>
      revenue > max.revenue ? { platform, revenue } : max
    , { platform: 'youtube', revenue: platformTotals.youtube })

    const platformMap: Record<string, Platform> = {
      youtube: 'YOUTUBE',
      tiktok: 'TIKTOK',
      instagram: 'INSTAGRAM',
      naverClip: 'NAVER_CLIP',
    }

    return {
      totalRevenue,
      monthlyGrowth: Math.round(monthlyGrowth * 100) / 100,
      averageRPM,
      topPlatform: platformMap[topPlatformEntry.platform],
      topPlatformRevenue: topPlatformEntry.revenue,
    }
  }

  const totalAnnualRevenue = computed(() =>
    monthlyRevenue.value.reduce((sum, item) => sum + item.total, 0)
  )

  const platformBreakdown = computed(() => {
    const total = totalAnnualRevenue.value
    if (total === 0) return []

    const platformTotals = {
      YOUTUBE: monthlyRevenue.value.reduce((sum, item) => sum + item.youtube, 0),
      TIKTOK: monthlyRevenue.value.reduce((sum, item) => sum + item.tiktok, 0),
      INSTAGRAM: monthlyRevenue.value.reduce((sum, item) => sum + item.instagram, 0),
      NAVER_CLIP: monthlyRevenue.value.reduce((sum, item) => sum + item.naverClip, 0),
    }

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
      const growth = ((item.total - previous) / previous) * 100
      return {
        period: item.period,
        growth: Math.round(growth * 100) / 100,
      }
    })
  })

  async function fetchRevenue() {
    loading.value = true
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
            trendsByDate.set(period, { period, youtube: 0, tiktok: 0, instagram: 0, naverClip: 0, total: 0 })
          }
          const entry = trendsByDate.get(period)!
          const amount = point.revenueKrw ?? 0
          switch (point.platform?.toUpperCase()) {
            case 'YOUTUBE': entry.youtube += amount; break
            case 'TIKTOK': entry.tiktok += amount; break
            case 'INSTAGRAM': entry.instagram += amount; break
            case 'NAVER_CLIP': entry.naverClip += amount; break
          }
          entry.total += amount
        }
        monthlyRevenue.value = Array.from(trendsByDate.values())
      }
      if (results[2].status === 'fulfilled') apiPlatformRevenue.value = results[2].value

      summary.value = calculateSummary(monthlyRevenue.value)
    } catch {
      // Keep empty state on failure
      monthlyRevenue.value = []
      summary.value = calculateSummary([])
    } finally {
      loading.value = false
    }
  }

  return {
    monthlyRevenue,
    summary,
    loading,
    totalAnnualRevenue,
    platformBreakdown,
    growthTrend,
    apiSummary,
    apiTrends,
    apiPlatformRevenue,
    fetchRevenue,
  }
})
