import type { Platform } from './channel'

export interface DashboardKpi {
  totalViews: number
  viewsChangePercent: number
  totalSubscribers: number
  subscribersChange: number
  totalLikes: number
  likesChangePercent: number
  creditBalance: number
  creditTotal: number
}

export interface TrendDataPoint {
  date: string
  totalViews: number
  platformViews: Record<string, number>
  totalSubscribers?: number
  platformSubscribers?: Record<string, number>
}

export interface PlatformComparison {
  platform: Platform
  views: number
  likes: number
  comments: number
  shares: number
}

export interface VideoAnalytics {
  platform: Platform
  views: number
  likes: number
  comments: number
  shares: number
  viewsChange?: number
  likesChange?: number
  dailyTrend: TrendDataPoint[]
}

export interface HeatmapData {
  dayOfWeek: number
  hour: number
  value: number
}

export interface TopVideo {
  videoId: number
  title: string
  thumbnailUrl: string | null
  totalViews: number
  totalLikes: number
  publishedAt: string | null
  platforms: Platform[]
}

export type AnalyticsPeriod = '7d' | '30d' | '90d' | '1y'

export interface VideoCompareItem {
  videoId: number
  title: string | null
  totalViews: number
  totalLikes: number
  totalComments: number
  totalShares: number
  totalWatchTimeSeconds: number
  avgDailyViews: number
  engagementRate: number
}

export interface VideoCompareResponse {
  videos: VideoCompareItem[]
}

export interface OptimalTimeSlot {
  dayOfWeek: number
  dayLabel: string
  hour: number
  timeLabel: string
  expectedViews: number
  engagementRate: number
  confidenceScore: number
  score: number
}

export interface PerformanceScoreResponse {
  videoId: number
  overallScore: number
  breakdown: Record<string, number>
  percentileRank: number
  trend: 'up' | 'down' | 'stable'
  isAnomaly: boolean
  anomalyDescription: string | null
  prediction7d: number
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

export interface TagPerformance {
  tag: string
  videoCount: number
  totalViews: number
  totalLikes: number
  avgViews: number
  avgEngagement: number
  trend: 'up' | 'down' | 'stable'
}

// Cohort Analysis
export interface CohortDataPoint {
  day: number
  value: number
  normalizedPercent: number
}

export interface CohortGroupData {
  name: string
  videoCount: number
  avgViews: number
  cumulativeViewCurve: CohortDataPoint[]
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
}

// Deep analytics types
export interface TrafficSourceResponse {
  period: string
  sources: Record<string, number>
  total: number
}

export interface DemographicsResponse {
  period: string
  ageDistribution: Record<string, number>
  genderDistribution: Record<string, number>
  topCountries: Record<string, number>
}

export interface CTRTrendPoint {
  date: string
  impressions: number
  views: number
  ctr: number
}

export interface CTRResponse {
  period: string
  avgCTR: number
  totalImpressions: number
  data: CTRTrendPoint[]
}

export interface AvgViewDurationPoint {
  date: string
  avgDurationSeconds: number
  totalWatchTimeSeconds: number
  totalViews: number
}

export interface AvgViewDurationResponse {
  period: string
  avgDurationSeconds: number
  data: AvgViewDurationPoint[]
}

export interface SubscriberConversionPoint {
  date: string
  gained: number
  views: number
  conversionRate: number
}

export interface SubscriberConversionResponse {
  period: string
  totalGained: number
  data: SubscriberConversionPoint[]
}
