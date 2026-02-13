import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  DashboardKpi,
  TrendDataPoint,
  PlatformComparison,
  VideoAnalytics,
  HeatmapData,
  TopVideo,
  VideoCompareResponse,
  OptimalTimeSlot,
  PerformanceScoreResponse,
  AnomalyListResponse,
  CohortAnalysisResponse,
  RetentionCurveResponse,
} from '@/types/analytics'

function periodToDays(period: string): number {
  const match = period.match(/^(\d+)d$/)
  if (match) return parseInt(match[1], 10)
  switch (period) {
    case '7d': return 7
    case '30d': return 30
    case '90d': return 90
    case '1y': return 365
    default: return 7
  }
}

export const analyticsApi = {
  dashboard(period: string = '7d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<DashboardKpi>>('/analytics/dashboard', { params: { days } })
      .then(unwrapResponse)
  },

  trends(period: string = '7d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<{ data: TrendDataPoint[] }>>('/analytics/trends', { params: { days } })
      .then(unwrapResponse)
      .then((res) => res.data)
  },

  platformComparison(period: string = '7d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<PlatformComparison[]>>('/analytics/platform-comparison', { params: { days } })
      .then(unwrapResponse)
  },

  videoAnalytics(videoId: number) {
    return apiClient
      .get<ResData<VideoAnalytics[]>>(`/analytics/videos/${videoId}`)
      .then(unwrapResponse)
  },

  heatmap() {
    return apiClient.get<ResData<HeatmapData[]>>('/analytics/heatmap').then(unwrapResponse)
  },

  topVideos(period: string = '7d', limit: number = 10) {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<TopVideo[]>>('/analytics/top-videos', { params: { days, limit } })
      .then(unwrapResponse)
  },

  getOptimalTimes(platform?: string) {
    const params = platform ? { platform } : {}
    return apiClient
      .get<ResData<{ slots: OptimalTimeSlot[] }>>('/analytics/optimal-times', { params })
      .then(unwrapResponse)
  },

  videoCompare(videoIds: number[], period: string = '30d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<VideoCompareResponse>>('/analytics/compare', {
        params: { videoIds: videoIds.join(','), days },
      })
      .then(unwrapResponse)
  },

  performanceScore(videoId: number) {
    return apiClient
      .get<ResData<PerformanceScoreResponse>>(`/analytics/videos/${videoId}/performance-score`)
      .then(unwrapResponse)
  },

  anomalies() {
    return apiClient
      .get<ResData<AnomalyListResponse>>('/analytics/anomalies')
      .then(unwrapResponse)
  },

  cohortAnalysis(groupBy: string = 'CATEGORY', from?: string, to?: string) {
    const params: Record<string, string> = { groupBy }
    if (from) params.from = from
    if (to) params.to = to
    return apiClient
      .get<ResData<CohortAnalysisResponse>>('/analytics/cohort', { params })
      .then(unwrapResponse)
  },

  retentionCurve(videoId: number) {
    return apiClient
      .get<ResData<RetentionCurveResponse>>(`/analytics/videos/${videoId}/retention`)
      .then(unwrapResponse)
  },
}
