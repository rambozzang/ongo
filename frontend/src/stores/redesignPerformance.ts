import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  AnalyticsPeriod,
  AvgViewDurationResponse,
  DashboardKpi,
  SubscriberConversionResponse,
  TopVideo,
  TrendDataPoint,
} from '@/types/analytics'
import { analyticsApi } from '@/api/analytics'

/** 성과 리디자인 화면에서 사용하는 실제 분석 데이터 저장소. */
export const useRedesignPerformanceStore = defineStore('redesignPerformance', () => {
  const range = ref<AnalyticsPeriod>('30d')
  const kpi = ref<DashboardKpi | null>(null)
  const trends = ref<TrendDataPoint[]>([])
  const topVideos = ref<TopVideo[]>([])
  const averageViewDuration = ref<AvgViewDurationResponse | null>(null)
  const subscriberConversion = ref<SubscriberConversionResponse | null>(null)
  const loading = ref(false)
  const hasError = ref(false)

  async function fetchPerformance(nextRange: AnalyticsPeriod = range.value) {
    range.value = nextRange
    loading.value = true
    hasError.value = false

    const results = await Promise.allSettled([
      analyticsApi.dashboard(nextRange),
      analyticsApi.trends(nextRange),
      analyticsApi.topVideos(nextRange, 10),
      analyticsApi.avgViewDuration(Number(nextRange.replace('d', ''))),
      analyticsApi.subscriberConversion(Number(nextRange.replace('d', ''))),
    ])

    if (results[0].status === 'fulfilled') kpi.value = results[0].value
    if (results[1].status === 'fulfilled') trends.value = results[1].value
    if (results[2].status === 'fulfilled') topVideos.value = results[2].value
    if (results[3].status === 'fulfilled') averageViewDuration.value = results[3].value
    if (results[4].status === 'fulfilled') subscriberConversion.value = results[4].value

    hasError.value = results.some((result) => result.status === 'rejected')
    loading.value = false
  }

  return {
    range,
    kpi,
    trends,
    topVideos,
    averageViewDuration,
    subscriberConversion,
    loading,
    hasError,
    fetchPerformance,
  }
})
