import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  AnalyticsPeriod,
  AvgViewDurationResponse,
  DashboardKpi,
  SubscriberConversionResponse,
  TopVideo,
  TrendDataPoint,
  PlatformComparison,
  TagPerformance,
  OptimalTimeSlot,
} from '@/types/analytics'
import { analyticsApi } from '@/api/analytics'
import { scheduleApi } from '@/api/schedule'
import { kstDateString } from '@/utils/kst'

/** 성과 리디자인 화면에서 사용하는 실제 분석 데이터 저장소. */
export const useRedesignPerformanceStore = defineStore('redesignPerformance', () => {
  const range = ref<AnalyticsPeriod>('30d')
  const kpi = ref<DashboardKpi | null>(null)
  const trends = ref<TrendDataPoint[]>([])
  const topVideos = ref<TopVideo[]>([])
  const averageViewDuration = ref<AvgViewDurationResponse | null>(null)
  const subscriberConversion = ref<SubscriberConversionResponse | null>(null)
  const publishedCount = ref<number | null>(null)
  const loading = ref(false)
  const hasError = ref(false)
  const platformComparison = ref<PlatformComparison[]>([])
  const tagPerformance = ref<TagPerformance[]>([])
  const optimalTimes = ref<OptimalTimeSlot[]>([])
  const optimalTimesReason = ref<string | null>(null)
  const failedPanels = ref<string[]>([])
  const loadedAt = ref<string | null>(null)
  let requestSequence = 0

  async function fetchPerformance(nextRange: AnalyticsPeriod = range.value) {
    const sequence = ++requestSequence
    if (range.value !== nextRange) {
      // Confirmed data belongs to its period. Never show last month's values
      // under a new range if one of the new requests fails.
      kpi.value = null
      trends.value = []
      topVideos.value = []
      averageViewDuration.value = null
      subscriberConversion.value = null
      publishedCount.value = null
      platformComparison.value = []
      tagPerformance.value = []
      optimalTimes.value = []
      loadedAt.value = null
    }
    range.value = nextRange
    loading.value = true
    hasError.value = false
    const days = nextRange === '1y' ? 365 : Number(nextRange.replace('d', ''))
    const endDate = kstDateString()
    const startDate = addDays(endDate, -(days - 1))

    const results = await Promise.allSettled([
      analyticsApi.dashboard(nextRange),
      analyticsApi.trends(nextRange),
      analyticsApi.topVideos(nextRange, 10),
      analyticsApi.avgViewDuration(days),
      analyticsApi.subscriberConversion(days),
      scheduleApi.list({ startDate, endDate }),
      analyticsApi.platformComparison(nextRange),
      analyticsApi.tagPerformance(nextRange),
      analyticsApi.getOptimalTimes(undefined, days),
    ])

    if (sequence !== requestSequence) return

    if (results[0].status === 'fulfilled') kpi.value = results[0].value
    if (results[1].status === 'fulfilled') trends.value = results[1].value
    if (results[2].status === 'fulfilled') topVideos.value = results[2].value
    if (results[3].status === 'fulfilled') averageViewDuration.value = results[3].value
    if (results[4].status === 'fulfilled') subscriberConversion.value = results[4].value
    if (results[5].status === 'fulfilled') {
      publishedCount.value = results[5].value.filter(
        (schedule) => schedule.status === 'PUBLISHED',
      ).length
    }
    if (results[6].status === 'fulfilled') platformComparison.value = results[6].value
    if (results[7].status === 'fulfilled') tagPerformance.value = results[7].value
    if (results[8].status === 'fulfilled') {
      optimalTimes.value = results[8].value.slots
      optimalTimesReason.value = results[8].value.unavailableReason ?? null
    }

    hasError.value = results.some((result) => result.status === 'rejected')
    const panels = [
      'dashboard',
      'trends',
      'topVideos',
      'watchTime',
      'subscribers',
      'schedules',
      'platforms',
      'tags',
      'optimalTimes',
    ]
    failedPanels.value = results.flatMap((result, index) =>
      result.status === 'rejected' ? [panels[index]] : [],
    )
    loadedAt.value = new Date().toISOString()
    loading.value = false
  }

  return {
    range,
    kpi,
    trends,
    topVideos,
    averageViewDuration,
    subscriberConversion,
    publishedCount,
    loading,
    hasError,
    platformComparison,
    tagPerformance,
    optimalTimes,
    optimalTimesReason,
    failedPanels,
    loadedAt,
    fetchPerformance,
  }
})

function addDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}
