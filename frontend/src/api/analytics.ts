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
  TagPerformance,
} from '@/types/analytics'
import type { Platform } from '@/types/channel'

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

// ── Backend response shapes (differ from frontend types) ──────────────

interface BackendVideoAnalyticsResponse {
  videoId: number
  title: string | null
  platforms: {
    platform: Platform
    views: number
    likes: number
    comments: number
    shares: number
    dailyData: { date: string; views: number; likes: number; comments: number }[]
  }[]
}

interface BackendPlatformComparisonResponse {
  platforms: PlatformComparison[]
}

interface BackendHeatmapResponse {
  data: Record<string, Record<string, number>>
}

interface BackendTopVideoResponse {
  videos: {
    id: number
    title: string
    thumbnailUrl: string | null
    totalViews: number
    totalLikes?: number
    publishedAt?: string | null
    platforms: string[]
  }[]
}

// ── Public API ────────────────────────────────────────────────────────

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

  platformComparison(period: string = '7d'): Promise<PlatformComparison[]> {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<BackendPlatformComparisonResponse>>('/analytics/platform-comparison', { params: { days } })
      .then(unwrapResponse)
      .then((res) => res.platforms)
  },

  videoAnalytics(videoId: number): Promise<VideoAnalytics[]> {
    return apiClient
      .get<ResData<BackendVideoAnalyticsResponse>>(`/analytics/videos/${videoId}`)
      .then(unwrapResponse)
      .then((res) =>
        res.platforms.map((p) => ({
          platform: p.platform,
          views: p.views,
          likes: p.likes,
          comments: p.comments,
          shares: p.shares,
          dailyTrend: p.dailyData.map((d) => ({
            date: d.date,
            totalViews: d.views,
            platformViews: {} as Record<string, number>,
          })),
        })),
      )
  },

  heatmap(): Promise<HeatmapData[]> {
    return apiClient
      .get<ResData<BackendHeatmapResponse>>('/analytics/heatmap')
      .then(unwrapResponse)
      .then((res) => {
        const result: HeatmapData[] = []
        for (const [dayKey, hours] of Object.entries(res.data)) {
          const dayOfWeek = parseInt(dayKey, 10)
          for (const [hourKey, value] of Object.entries(hours)) {
            result.push({ dayOfWeek, hour: parseInt(hourKey, 10), value })
          }
        }
        return result
      })
  },

  topVideos(period: string = '7d', limit: number = 10): Promise<TopVideo[]> {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<BackendTopVideoResponse>>('/analytics/top-videos', { params: { days, limit } })
      .then(unwrapResponse)
      .then((res) =>
        res.videos.map((v) => ({
          videoId: v.id,
          title: v.title,
          thumbnailUrl: v.thumbnailUrl,
          totalViews: v.totalViews,
          totalLikes: v.totalLikes ?? 0,
          publishedAt: v.publishedAt ?? null,
          platforms: v.platforms as Platform[],
        })),
      )
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

  tagPerformance(period: string = '30d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<{ tags: TagPerformance[] }>>('/analytics/tags', { params: { days } })
      .then(unwrapResponse)
      .then((res) => res.tags)
  },
}
