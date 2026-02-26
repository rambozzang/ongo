export interface HashtagPerformance {
  id: number
  hashtag: string
  platform: string
  usageCount: number
  totalViews: number
  avgEngagement: number
  growthRate: number
  trendDirection: 'UP' | 'DOWN' | 'STABLE'
  category: string
  lastUsedAt: string
  createdAt: string
}

export interface HashtagTrend {
  hashtag: string
  date: string
  volume: number
  engagement: number
}

export interface HashtagRecommendation {
  hashtag: string
  relevanceScore: number
  expectedReach: number
  competition: 'LOW' | 'MEDIUM' | 'HIGH'
  reason: string
}

export interface HashtagGroup {
  id: number
  name: string
  hashtags: string[]
  platform: string
  usageCount: number
  createdAt: string
}

export interface HashtagAnalyticsSummary {
  totalHashtags: number
  trendingCount: number
  avgEngagementRate: number
  topPerformer: string
  groupCount: number
}

export interface AnalyzeHashtagsRequest {
  topic: string
  platform: string
  count: number
}
