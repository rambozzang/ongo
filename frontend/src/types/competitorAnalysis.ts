export type PlatformType = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER'
export type MetricTrend = 'UP' | 'DOWN' | 'STABLE'
export type AnalysisPeriod = '7d' | '30d' | '90d' | '1y'

export interface Competitor {
  id: number
  name: string
  avatarUrl: string
  platforms: PlatformType[]
  subscriberCount: number
  avgViews: number
  avgEngagement: number
  addedAt: string
}

export interface CompetitorMetric {
  competitorId: number
  date: string
  views: number
  subscribers: number
  engagement: number
  uploads: number
  avgViewDuration: number
}

export interface CompetitorComparison {
  myMetrics: {
    views: number
    viewsTrend: MetricTrend
    subscribers: number
    subscribersTrend: MetricTrend
    engagement: number
    engagementTrend: MetricTrend
    uploadFrequency: number
  }
  competitors: {
    competitor: Competitor
    views: number
    viewsTrend: MetricTrend
    subscribers: number
    subscribersTrend: MetricTrend
    engagement: number
    engagementTrend: MetricTrend
    uploadFrequency: number
  }[]
}

export interface ContentGap {
  id: number
  keyword: string
  competitorCount: number
  avgViews: number
  difficulty: 'LOW' | 'MEDIUM' | 'HIGH'
  opportunity: 'LOW' | 'MEDIUM' | 'HIGH'
  suggestedTitle?: string
}

export interface TrendingTopic {
  keyword: string
  volume: number
  growth: number
  competitorUsage: number
  platform: PlatformType
}

export interface AddCompetitorRequest {
  name: string
  platformUrls: { platform: PlatformType; url: string }[]
}

export interface AnalysisReport {
  id: number
  period: AnalysisPeriod
  comparison: CompetitorComparison
  contentGaps: ContentGap[]
  trendingTopics: TrendingTopic[]
  generatedAt: string
}
