export type PlatformKey = 'youtube' | 'tiktok' | 'instagram' | 'naverClip'

export interface CrossPlatformContent {
  id: number
  title: string
  thumbnailUrl: string
  publishedAt: string
  platforms: {
    platform: PlatformKey
    videoId: string
    views: number
    likes: number
    comments: number
    shares: number
    avgWatchTime: number
    ctr: number
    publishedAt: string
  }[]
}

export interface PlatformPerformanceSummary {
  platform: PlatformKey
  totalViews: number
  totalLikes: number
  avgEngagement: number
  avgCtr: number
  contentCount: number
  bestPerformingContentId: number
}

export interface CrossPlatformReport {
  id: number
  period: string
  contents: CrossPlatformContent[]
  platformSummaries: PlatformPerformanceSummary[]
  audienceOverlap: {
    platforms: [PlatformKey, PlatformKey]
    overlapPercent: number
  }[]
  recommendations: string[]
  generatedAt: string
}

export interface ContentCompareRequest {
  contentIds: number[]
  period?: string
}
