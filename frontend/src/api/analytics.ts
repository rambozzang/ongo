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
  TrafficSourceResponse,
  DemographicsResponse,
  CTRResponse,
  AvgViewDurationResponse,
  SubscriberConversionResponse,
  CrossPlatformSummaryResponse,
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

/**
 * 서버 응답 그대로의 모양. **수집하지 않는 지표는 `null`** 이다.
 *
 * 여기를 `number` 로 두면 매퍼에서 `null` 이 조용히 통과해 화면이 그것을 숫자로 그린다.
 * 경계 타입이 서버 계약과 어긋나면 `vue-tsc` 가 아무것도 잡아 주지 못한다.
 */
interface BackendVideoAnalyticsResponse {
  videoId: number
  title: string | null
  platforms: {
    platform: Platform
    views: number | null
    likes: number | null
    comments: number | null
    shares: number | null
    unavailableMetrics?: string[]
    /** 조회수를 수집하지 않는 플랫폼은 비어 있다 — 그릴 추이가 없다. */
    dailyData: { date: string; views: number; likes: number; comments: number }[]
  }[]
}

interface BackendPlatformComparisonResponse {
  platforms: PlatformComparison[]
}

/**
 * 게시 요일·시각별 **조회수 합계**. 요일 키는 `SUN`~`SAT` 이름이고, 데이터가 없는
 * 칸은 **키 자체가 없다** — 서버가 0 을 채우지 않는다.
 */
interface BackendHeatmapResponse {
  data: Record<string, Record<string, number>>
}

/** 서버의 요일 이름 → `HeatmapData.dayOfWeek` 인덱스(0=일). */
const DAY_NAME_TO_INDEX: Record<string, number> = {
  SUN: 0,
  MON: 1,
  TUE: 2,
  WED: 3,
  THU: 4,
  FRI: 5,
  SAT: 6,
}

/** 인기 영상. **지표를 수집하는 업로드가 하나도 없으면 그 합계는 `null`** 이다. */
interface BackendTopVideoResponse {
  videos: {
    id: number
    title: string
    thumbnailUrl: string | null
    totalViews: number | null
    totalLikes?: number | null
    unavailableMetrics?: string[]
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
          unavailableMetrics: p.unavailableMetrics ?? [],
          /*
           * **집계 행이 하나라도 있었는가.**
           *
           * 서버는 이제 기간 내 행이 없으면 합계를 `null` 로 준다(예전에는 `0` 이었고,
           * "실제로 0회" 와 "아직 수집 안 됨" 이 응답에서 똑같이 보였다). 그래도 이
           * 플래그는 남긴다 — 값의 `null` 만으로는 **미지원**(플랫폼이 안 줌)과
           * **수집 대기**(줄 수 있는데 행이 없음)를 가를 수 없고, 그 구분은
           * `unavailableMetrics` 와 이 플래그를 함께 봐야 나온다.
           *
           * **`?? 0` 을 하지 말 것.** 값의 `null` 을 0 으로 채우면 서버 수정이 무의미해진다.
           */
          hasData: p.dailyData.length > 0,
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
          /*
           * **서버는 요일을 이름으로 준다** — `{"WED": {"14": 1000}}`.
           * 예전에는 `parseInt('WED', 10)` 이라 `dayOfWeek` 가 전부 `NaN` 이 됐고,
           * 히트맵 조회 키(`${dayOfWeek}-${hour}`)가 어떤 칸과도 맞지 않아
           * **화면 전체가 빈 칸**으로 그려졌다. 데이터가 없는 것과 구분되지 않았다.
           */
          const dayOfWeek = DAY_NAME_TO_INDEX[dayKey]
          if (dayOfWeek === undefined) continue
          for (const [hourKey, value] of Object.entries(hours)) {
            const hour = parseInt(hourKey, 10)
            if (Number.isNaN(hour)) continue
            result.push({ dayOfWeek, hour, value })
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
          /*
           * **`?? 0` 을 하지 않는다.** 서버는 그 지표를 수집하는 업로드가 하나도 없을 때
           * `null` 을 준다 — 0 으로 바꾸면 "좋아요 0개" 라는 관측이 되어 서버 수정이
           * 통째로 무의미해진다. 필드가 없는 옛 응답(`undefined`)도 판단 불가라 `null` 이다.
           */
          totalLikes: v.totalLikes ?? null,
          unavailableMetrics: v.unavailableMetrics ?? [],
          publishedAt: v.publishedAt ?? null,
          platforms: v.platforms as Platform[],
        })),
      )
  },

  getOptimalTimes(platform?: string) {
    const params = platform ? { platform } : {}
    return apiClient
      .get<ResData<{ slots: OptimalTimeSlot[]; unavailableReason?: string | null }>>('/analytics/optimal-times', { params })
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

  trafficSources(days = 30) {
    return apiClient
      .get<ResData<TrafficSourceResponse>>(`/analytics/traffic-sources`, { params: { days } })
      .then(unwrapResponse)
  },

  demographics(days = 30) {
    return apiClient
      .get<ResData<DemographicsResponse>>(`/analytics/demographics`, { params: { days } })
      .then(unwrapResponse)
  },

  ctr(days = 30) {
    return apiClient
      .get<ResData<CTRResponse>>(`/analytics/ctr`, { params: { days } })
      .then(unwrapResponse)
  },

  avgViewDuration(days = 30) {
    return apiClient
      .get<ResData<AvgViewDurationResponse>>(`/analytics/avg-view-duration`, { params: { days } })
      .then(unwrapResponse)
  },

  subscriberConversion(days = 30) {
    return apiClient
      .get<ResData<SubscriberConversionResponse>>(`/analytics/subscriber-conversion`, { params: { days } })
      .then(unwrapResponse)
  },

  crossPlatformComparison(days = 30) {
    return apiClient
      .get<ResData<CrossPlatformSummaryResponse>>('/analytics/cross-platform', { params: { days } })
      .then(unwrapResponse)
  },
}
