import type { Platform } from './channel'

export interface DashboardKpi {
  /**
   * 기간 내 조회수 합계. **조회수를 수집하는 플랫폼의 행이 없으면 `null`** 이다.
   *
   * Tumblr 는 `total_notes`(좋아요+리블로그+답글 총합)를 `views` 컬럼에 넣는다. 서버가
   * 그런 행을 빼고 나면 더할 것이 없는 사용자가 생기는데, 그때의 `0` 은 "0 회" 가 아니라
   * 물어볼 곳이 없다는 뜻이다. **여기서 `?? 0` 을 하면 둘이 다시 같아진다.**
   *
   * 수집 플랫폼의 행이 있는 상태의 `0` 은 관측이므로 그대로 `0` 이다.
   */
  totalViews: number | null
  /**
   * 이전 기간 대비 증감률(%). **`null` 이면 비교 불가.**
   *
   * 이전 기간이 0 이라 기준이 없거나, 두 기간 중 하나라도 측정되지 않은 경우다.
   * `0` 이나 `100` 으로 채우지 말 것. 예전 서버는 이 자리에 임의의 `100.0` 을 넣어,
   * 첫 주에 5만 조회를 낸 채널과 100 → 200 으로 는 채널이 똑같이 "▲100%" 로 보였다.
   */
  viewsChangePercent: number | null
  /**
   * 신규 구독(팔로워) 증가 수. **측정된 행이 없으면 `null`** 이다.
   *
   * `subscriber_gained` 를 조회하는 어댑터는 YouTube 하나뿐이라, YouTube 업로드가 없거나
   * 그 기간에 집계 행이 없으면 물어볼 곳 자체가 없다. **YouTube 행이 있는 상태의 0 은
   * "0명 늘었다" 는 관측**이므로 그대로 `0` 이다. 여기서 `?? 0` 을 하면 셋이 다시 같아진다.
   */
  totalSubscribers: number | null
  /** 절대 증감 수(비율이 아니다). 현재·이전 기간 중 하나라도 미측정이면 `null`. */
  subscribersChange: number | null
  /**
   * 기간 내 좋아요 합계. **좋아요를 수집하는 플랫폼의 행이 없으면 `null`.**
   *
   * Pinterest 는 `SAVE`(저장 수)를 `likes` 에 넣는다. 저장은 좋아요가 아니므로 그 행은
   * 서버에서 빠진다. 여기서 `?? 0` 을 하면 미수집이 "좋아요 0" 으로 위장된다.
   */
  totalLikes: number | null
  /** 이전 기간 대비 증감률(%). 기준이 없거나 한쪽 기간이 미측정이면 `null`. */
  likesChangePercent: number | null
  /** 기간 내 댓글 합계. **댓글을 수집하는 플랫폼의 행이 없으면 `null`.** */
  totalComments?: number | null
  creditBalance: number
  creditTotal: number
}

/**
 * 하루치 추세. **수집하는 플랫폼의 값만 담긴다.**
 *
 * `subscriber_gained` 를 채우는 어댑터는 YouTube 하나뿐이라, 예전에는 나머지 12개
 * 플랫폼의 하드코딩 0 이 합계에 들어가고 `platformSubscribers` 에도 플랫폼마다 "+0" 이
 * 실렸다. 여기서 `?? 0` 을 하면 그 위장이 그대로 되살아난다.
 */
export interface TrendDataPoint {
  date: string
  totalViews: number
  /** 조회수를 수집하는 플랫폼만. 키가 없다는 것이 곧 미수집이다. */
  platformViews: Record<string, number>
  /** 구독 증가를 수집하는 플랫폼이 없으면 `null`. */
  totalSubscribers?: number | null
  /** 구독 증가를 수집하는 플랫폼만. */
  platformSubscribers?: Record<string, number>
  unavailableMetrics?: string[]
}

/**
 * 플랫폼 하나의 성과 합계.
 *
 * **`null` 은 "그 플랫폼이 이 지표를 주지 않는다" 이며 0 이 아니다.**
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다.
 * 예전에는 저장된 0 이 그대로 내려와 "Facebook 공유 0회" 를 성과처럼 보여줬다.
 *
 * 지원하는 지표의 실제 0 은 관측 결과이므로 그대로 `0` 이다.
 */
export interface PlatformComparison {
  platform: Platform
  /**
   * 조회수. **그 플랫폼이 보고하지 않으면 `null`.**
   *
   * 예전 주석은 "모든 플랫폼이 보고한다" 였지만 사실이 아니다. Tumblr 의 `views` 자리에는
   * `total_notes`(노트 총합)가, Naver Clip 에는 아무것도 들어 있지 않다.
   */
  views: number | null
  likes: number | null
  comments: number | null
  shares: number | null
  /** 이 플랫폼이 수집하지 않는 지표 이름들. */
  unavailableMetrics?: string[]
}

/**
 * 플랫폼 하나의 합계. **그 플랫폼이 수집하지 않는 지표는 `null`** 이다.
 *
 * 0 만 문제가 아니다 — Pinterest 의 `shares` 자리에는 PIN_CLICK(클릭 수),
 * Dailymotion 에는 bookmarks_total(북마크), Tumblr 의 `views` 자리에는
 * total_notes(노트 총합)가 들어 있었다. **이름이 다른 지표라 0 이 아니라 큰 숫자로
 * 조용히 틀린다.**
 */
export interface VideoAnalytics {
  platform: Platform
  views: number | null
  likes: number | null
  comments: number | null
  shares: number | null
  /** 이 플랫폼이 수집하지 않는 지표 이름. 값의 `null` 과 짝을 이룬다. */
  unavailableMetrics?: string[]
  viewsChange?: number
  likesChange?: number
  /**
   * 집계 행이 하나라도 있었는지. **합계 0 만으로는 알 수 없다** — 서버는 수집이 없는
   * 업로드에도 합계 0 인 행을 만들기 때문이다. 필드가 없는 옛 응답은 판단 불가(undefined).
   */
  hasData?: boolean
  dailyTrend: TrendDataPoint[]
}

export interface HeatmapData {
  dayOfWeek: number
  hour: number
  value: number
}

/** 인기 영상. **지표를 수집하는 업로드가 하나도 없으면 그 합계는 `null`** 이다. */
export interface TopVideo {
  videoId: number
  title: string
  thumbnailUrl: string | null
  totalViews: number | null
  totalLikes: number | null
  unavailableMetrics?: string[]
  publishedAt: string | null
  platforms: Platform[]
}

export type AnalyticsPeriod = '7d' | '30d' | '90d' | '1y'

/**
 * 영상 비교 한 줄. **그 지표를 수집하는 업로드가 하나도 없으면 합계는 `null`** 이다.
 *
 * `unavailableMetrics` 에는 구성 지표가 하나라도 빠져 **불완전한** 값(`engagementRate`)도
 * 함께 담긴다 — 그때 값은 `null` 이 아니라 "일부만 반영된 숫자" 이므로 화면이 그 표시를
 * 함께 그려야 한다.
 */
export interface VideoCompareItem {
  videoId: number
  title: string | null
  totalViews: number | null
  totalLikes: number | null
  totalComments: number | null
  totalShares: number | null
  totalWatchTimeSeconds: number | null
  avgDailyViews: number | null
  engagementRate: number | null
  unavailableMetrics: string[]
}

export interface VideoCompareResponse {
  videos: VideoCompareItem[]
}

/**
 * 최적 게시 시간 슬롯 하나.
 *
 * `engagementRate` 는 그 슬롯을 만든 게시물이 전부 **참여 지표를 보고하지 않는
 * 플랫폼**의 것이면 `null` 이다. 예전 서버는 그 자리에 빈 표본의 중앙값 `0.0` 을 넣어
 * 화면이 "참여율 0%" 를 그렸다 — 재지 않았을 뿐인데 "참여가 없던 시간대" 가 된다.
 * **여기서 `?? 0` 을 하면 그 위장이 그대로 되살아난다.**
 *
 * 보고하는 플랫폼의 측정된 `0` 은 관측이므로 그대로 `0` 이다.
 */
export interface OptimalTimeSlot {
  dayOfWeek: number
  dayLabel: string
  hour: number
  timeLabel: string
  expectedViews: number
  /** 참여율(%). **표본이 없으면 `null`.** */
  engagementRate: number | null
  confidenceScore: number
  /**
   * 정렬용 종합 점수. 참여율을 재지 못한 슬롯이 하나라도 있으면 서버가 참여 항을
   * **모든 슬롯에서** 빼고 계산한다 — 측정 격차가 순위를 바꾸지 않게 하기 위함이다.
   */
  score: number
}

/**
 * 영상 성과 점수. **계산할 수 없는 값은 전부 `null`** 이다.
 *
 * 하위 점수는 모두 비율이라 분모(조회수)나 비교 기준(채널 평균)이 없으면 성립하지
 * 않는다. 여기서 `?? 0` 을 하면 화면이 "그 축에서 최하위"라는 판정을 그리게 되고,
 * 서버가 임의 기준값을 걷어낸 일이 통째로 무의미해진다.
 */
export interface PerformanceScoreResponse {
  videoId: number
  /** 측정된 하위 점수의 가중 평균. 계산된 축이 하나도 없으면 `null`. */
  overallScore: number | null
  /** 하위 점수. 계산할 수 없는 축은 `null` — `unavailableMetrics` 에 이유가 있다. */
  breakdown: Record<string, number | null>
  /**
   * 상위 몇 %인가. 1~100 이며 **낮을수록 좋다**(최고 성과가 가장 작은 값).
   *
   * 비교할 영상이 자기 자신뿐이거나 측정값이 없으면 `null` 이다. `0` 으로 대체하면
   * "Top 0%"(최상위)라는 없는 사실을 만든다.
   */
  percentileRank: number | null
  /** 추세를 판단할 기간이 없으면 `null` — "안정"으로 채우지 말 것. */
  trend: 'up' | 'down' | 'stable' | null
  isAnomaly: boolean
  anomalyDescription: string | null
  /** 회귀선을 그을 점이 부족하면 `null` — 관측 합계를 예측으로 쓰지 말 것. */
  prediction7d: number | null
  /**
   * 계산할 수 없었던 항목과 그 이유.
   *
   * 키는 하위 점수 이름과 `overall`·`trend`·`prediction7d`·`percentileRank`.
   */
  unavailableMetrics?: Record<string, string>
  /**
   * 점수를 계산할 집계 데이터가 있었는가.
   *
   * `false` 면 위 숫자는 계산 결과가 아니라 **채워 넣은 기본값**이다. 그리면
   * "0점 / 7일 예상 조회수 0회 / 안정적 추세"가 되어 미수집이 나쁜 성과로 읽힌다.
   * 필드가 없는 옛 응답은 `undefined`(판단 불가).
   */
  dataAvailable?: boolean
  /** `NO_UPLOADS` | `NO_ANALYTICS`. 화면 문구 선택에 쓴다. */
  unavailableReason?: string | null
}

export type AnomalyType = 'VIRAL_SPIKE' | 'ENGAGEMENT_SURGE' | 'UNUSUAL_DROP' | 'SHARE_SPIKE'

export interface AnomalyItem {
  videoId: number
  videoTitle: string | null
  anomalyType: AnomalyType
  severity: 'info' | 'warning' | 'critical'
  description: string
  detectedAt: string
}

export interface AnomalyListResponse {
  anomalies: AnomalyItem[]
}

/**
 * 태그 하나의 성과. **그 지표를 수집하는 업로드가 없으면 `null`** 이다.
 *
 * 태그는 여러 영상·여러 플랫폼에 걸쳐 있어 raw 합계가 서로 다른 뜻의 숫자를 섞는다.
 */
export interface TagPerformance {
  tag: string
  videoCount: number
  totalViews: number | null
  totalLikes: number | null
  avgViews: number | null
  avgEngagement: number | null
  /** 비교할 이전 관측이 없으면 `null` — "stable" 로 채우지 말 것. */
  trend: 'up' | 'down' | 'stable' | null
  unavailableMetrics?: string[]
}

// Cohort Analysis
export interface CohortDataPoint {
  day: number
  /** 그 구간까지의 누적 조회수. 측정된 0 은 그대로 0 이다. */
  value: number
  /**
   * 최대 누적 조회수 대비 비율(%). **기준이 될 최대값이 0 이면 `null`.**
   *
   * 예전 서버는 `maxViews.coerceAtLeast(1)` 로 분모를 1 로 세워, 조회가 전혀 없는
   * 코호트의 모든 구간이 `0.0` 이 됐다. 화면은 그것을 **평평한 0% 유지 곡선**으로
   * 그렸다 — 여기서 `?? 0` 을 하면 그 곡선이 그대로 되살아난다.
   */
  normalizedPercent: number | null
}

/** 코호트 하나. **정규화할 기준이 없으면 값 대신 `unavailableReason` 이 온다.** */
export interface CohortGroupData {
  name: string
  videoCount: number
  /** 영상당 평균 조회수. 영상이 없으면 `null`. */
  avgViews: number | null
  cumulativeViewCurve: CohortDataPoint[]
  unavailableReason?: string | null
}

export interface CohortAnalysisResponse {
  groupBy: string
  cohorts: CohortGroupData[]
  dateRange: { from: string; to: string }
}

// Retention Curve
export interface RetentionDataPoint {
  timestamp: number
  retentionRate: number
  viewCount: number
}

export interface DropOffPoint {
  timestamp: number
  dropRate: number
  possibleReason: string
}

export interface RetentionCurveResponse {
  videoId: number
  retentionPoints: RetentionDataPoint[]
  avgRetention: RetentionDataPoint[]
  dropOffPoints: DropOffPoint[]
  /**
   * 구간별 유지율을 실제로 측정했는지. 서버가 안 내려주면 **미측정으로 본다** —
   * 값이 없는 것과 기능이 없는 것은 사용자가 할 일이 다르다.
   */
  available?: boolean
  unavailableReason?: string | null
}

// Deep analytics types

/**
 * 트래픽 소스 분포. **현재 서버에는 이 값을 수집하는 경로가 없어 항상 `available=false`.**
 *
 * `channel_insights_daily` 를 채우는 `upsertChannelInsights` 는 호출부가 하나도 없고,
 * 플랫폼 어댑터 응답에도 트래픽 소스 필드가 없다. 그래서 빈 `sources` 와 `total=0` 은
 * **"유입 0 건" 이라는 관측이 아니라 재지 않았다**는 뜻이다.
 *
 * 화면은 `total===0` 을 성과로 그리지 말고 `available` 을 봐야 한다.
 */
export interface TrafficSourceResponse {
  period: string
  sources: Record<string, number>
  total: number
  /** 실제로 수집했는가. 이 필드를 모르는 옛 응답은 `undefined`(판단 불가). */
  available?: boolean
  /** `available` 이 `false` 일 때의 사유. 화면이 그대로 보여준다. */
  unavailableReason?: string | null
}

/**
 * 시청자 인구통계. **현재 서버에는 수집 경로가 없어 항상 `available=false`.**
 *
 * 빈 분포는 "그런 시청자가 없었다" 가 아니라 재지 않았다는 뜻이다.
 */
export interface DemographicsResponse {
  period: string
  ageDistribution: Record<string, number>
  genderDistribution: Record<string, number>
  topCountries: Record<string, number>
  /** 실제로 수집했는가. 이 필드를 모르는 옛 응답은 `undefined`(판단 불가). */
  available?: boolean
  /** `available` 이 `false` 일 때의 사유. 화면이 그대로 보여준다. */
  unavailableReason?: string | null
}

/**
 * 하루치 CTR. **이 포인트가 존재한다는 것 자체가 그날 노출이 측정됐다는 뜻**이다.
 *
 * 측정된 행이 없는 날짜는 서버가 포인트를 만들지 않는다 — 0 포인트를 그리면 그날
 * 클릭률이 0 이었다는 관측이 된다.
 */
export interface CTRTrendPoint {
  date: string
  impressions: number
  /** [impressions] 와 같은 행에서 나온 조회수. 다른 플랫폼 조회수가 섞이지 않는다. */
  views: number
  ctr: number | null
}

/**
 * CTR 추세.
 *
 * ## `null` 은 "재지 않았다" 이며 0 이 아니다
 *
 * 노출을 조회하는 어댑터는 YouTube 하나뿐이다. 다른 플랫폼만 쓰는 크리에이터에게는
 * 분모가 없으므로 클릭률이 존재하지 않는다. 예전에는 그 자리에 0 이 들어가 화면이
 * **"평균 CTR 0% · 총 노출 0"** 을 성과처럼 보여줬다.
 *
 * 측정된 행이 있을 때의 0% 는 관측 결과이므로 그대로 표시한다.
 */
export interface CTRResponse {
  period: string
  avgCTR: number | null
  totalImpressions: number | null
  data: CTRTrendPoint[]
  /** 이 합계가 어느 플랫폼의 표본인지. */
  measuredPlatforms?: string[]
  /** `avgCTR` 이 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
  unavailableReason?: string | null
}

/**
 * 하루치 평균 시청 시간. **이 포인트가 존재한다는 것 자체가 그날 조회가 측정됐다는 뜻**이다.
 */
export interface AvgViewDurationPoint {
  date: string
  avgDurationSeconds: number
  totalWatchTimeSeconds: number
  totalViews: number
}

/**
 * 평균 시청 시간 추세.
 *
 * `avgDurationSeconds` 가 `null` 이면 **재지 않았다** — 0초가 아니다. 시청 시간을
 * 조회하는 어댑터는 YouTube 하나뿐이라, 다른 플랫폼만 쓰는 크리에이터에게는 분자가 없다.
 * 예전에는 그 자리에 0 이 들어가 화면이 "0초" 를 관측 결과처럼 보여줬다.
 */
export interface AvgViewDurationResponse {
  period: string
  avgDurationSeconds: number | null
  data: AvgViewDurationPoint[]
  /** 이 평균이 어느 플랫폼의 표본인지. */
  measuredPlatforms?: string[]
  /** `avgDurationSeconds` 가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
  unavailableReason?: string | null
}

/**
 * 하루치 구독 전환. **이 포인트가 존재한다는 것 자체가 그날 조회가 측정됐다는 뜻**이다.
 */
export interface SubscriberConversionPoint {
  date: string
  gained: number
  views: number
  conversionRate: number | null
}

/**
 * 구독 전환 추세.
 *
 * `totalGained` 가 `null` 이면 **재지 않았다** — 0 이 아니다. 구독 증가를 조회하는
 * 어댑터는 YouTube 하나뿐이라, 다른 플랫폼만 쓰는 크리에이터에게는 분자가 없다.
 * 예전에는 그 자리에 0 이 들어가 화면이 초록색 `+0` 을 성과처럼 보여줬다.
 */
export interface SubscriberConversionResponse {
  period: string
  totalGained: number | null
  data: SubscriberConversionPoint[]
  /** 이 합계가 어느 플랫폼의 표본인지. */
  measuredPlatforms?: string[]
  /** `totalGained` 가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
  unavailableReason?: string | null
}

// 크로스 플랫폼 성과 비교
/**
 * 플랫폼 하나의 지표. **그 플랫폼이 수집하지 않는 값은 `null`** 이다.
 *
 * 예전 서버는 `unavailableMetrics` 로 "이건 미수집" 이라고 알리면서도 숫자는 raw 그대로
 * 내보냈다. 그것을 읽으면 Pinterest 의 `likes` 에서 저장(Save) 수를, `shares` 에서 클릭
 * 수를, Tumblr 의 `views` 에서 노트 총합을 받는다.
 */
export interface PlatformMetrics {
  platform: string
  views: number | null
  likes: number | null
  comments: number | null
  shares: number | null
  watchTimeSeconds: number | null
  engagementRate: number | null
  avgViewDuration: number | null
  revenueMicro: number | null
  /** Metrics whose numeric value is unavailable, rather than measured as zero. */
  unavailableMetrics: string[]
}

export interface CrossPlatformComparisonResponse {
  videoId: number
  videoTitle: string | null
  platforms: PlatformMetrics[]
  bestPlatform: string | null
  insights: string[]
}

/** 플랫폼 순위. **조회수를 수집하지 않는 플랫폼은 `rank` 가 `null`** 이다. */
export interface PlatformRanking {
  platform: string
  avgEngagementRate: number | null
  totalViews: number | null
  totalRevenue: number | null
  rank: number | null
  unavailableMetrics?: string[]
}

export interface CrossPlatformSummaryResponse {
  videos: CrossPlatformComparisonResponse[]
  platformRankings: Record<string, PlatformRanking>
}
