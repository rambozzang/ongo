export type CompetitorPlatform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'

export interface Competitor {
  id: number
  name: string
  channelUrl: string
  platform: CompetitorPlatform
  avatarUrl: string
  subscriberCount: number
  videoCount: number
  avgViews: number
  avgEngagement: number
  growthRate: number  // monthly percentage
  lastVideoAt: string
  addedAt: string
  isTracking: boolean
}

export interface CompetitorComparison {
  metric: string
  myValue: number
  competitorValue: number
  difference: number
  differencePercent: number
}

export interface CompetitorVideo {
  id: number
  competitorId: number
  title: string
  views: number
  likes: number
  comments: number
  publishedAt: string
  duration: string
  thumbnailUrl: string
}

// API types
export interface CompetitorResponse {
  id: number
  platform: string
  platformChannelId: string
  channelName: string
  channelUrl: string | null
  subscriberCount: number
  totalViews: number
  videoCount: number
  avgViews: number
  profileImageUrl: string | null
  lastSyncedAt: string | null
  createdAt: string | null
}

export interface CompetitorListResponse {
  competitors: CompetitorResponse[]
  totalCount: number
}

export interface CreateCompetitorRequest {
  platform: string
  platformChannelId: string
  channelName: string
  channelUrl?: string
  subscriberCount?: number
  totalViews?: number
  videoCount?: number
  avgViews?: number
  profileImageUrl?: string
}

export interface UpdateCompetitorRequest {
  channelName?: string
  channelUrl?: string
  subscriberCount?: number
  totalViews?: number
  videoCount?: number
  avgViews?: number
  profileImageUrl?: string
}

export interface ChannelLookupRequest {
  platform: string
  query: string
}

export interface ChannelLookupResponse {
  found: boolean
  platformChannelId?: string
  channelName?: string
  channelUrl?: string
  subscriberCount: number
  totalViews: number
  videoCount: number
  profileImageUrl?: string
  platform?: string
  requiresManualInput: boolean
  message?: string
}

// Trend API types
export interface CompetitorTrendPoint {
  date: string
  subscriberCount: number
  avgViews: number
  totalViews: number
}

export interface CompetitorTrendResponse {
  competitorId: number
  channelName: string
  data: CompetitorTrendPoint[]
}

// Benchmark API types
export interface MyChannelStats {
  subscriberCount: number
  totalViews: number
  videoCount: number
  avgViews: number
  engagementRate: number
  growthRate: number
}

export interface CompetitorBenchmark {
  id: number
  channelName: string
  platform: string
  subscriberCount: number
  totalViews: number
  videoCount: number
  avgViews: number
  engagementRate: number
  growthRate: number
  profileImageUrl: string | null
}

export interface BenchmarkResponse {
  myStats: MyChannelStats
  competitors: CompetitorBenchmark[]
}

// AI Insight types
export interface CompetitorInsightResult {
  summary: string
  strengths: string[]
  weaknesses: string[]
  opportunities: string[]
  recommendations: string[]
}
