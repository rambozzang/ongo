export type HashtagDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'VERY_HARD'
export type HashtagTrend = 'RISING' | 'STABLE' | 'DECLINING'

export interface Hashtag {
  tag: string
  reachEstimate: number
  competition: HashtagDifficulty
  trend: HashtagTrend
  avgViews: number
  relatedTags: string[]
}

export interface HashtagSet {
  id: number
  name: string
  hashtags: Hashtag[]
  totalReachEstimate: number
  overallDifficulty: HashtagDifficulty
  score: number
  platform: string
  createdAt: string
}

export interface HashtagAnalysisRequest {
  content: string
  platform: string
  targetAudience?: string
  count?: number
}

export interface HashtagAnalysisResponse {
  sets: HashtagSet[]
  creditsUsed: number
  creditsRemaining: number
}
