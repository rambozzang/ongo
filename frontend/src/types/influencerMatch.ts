export type MatchScore = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'LOW'
export type CollabStatus = 'SUGGESTED' | 'CONTACTED' | 'NEGOTIATING' | 'CONFIRMED' | 'COMPLETED' | 'DECLINED'

export interface InfluencerProfile {
  id: number
  name: string
  avatarUrl: string
  platform: string
  followers: number
  avgViews: number
  engagementRate: number
  category: string
  matchScore: number
  matchReason: string
  audienceOverlap: number
  estimatedCost: number
  recentContent: { title: string; views: number }[]
}

export interface CollabRequest {
  id: number
  influencerId: number
  influencerName: string
  status: CollabStatus
  message: string
  proposedBudget: number
  createdAt: string
  updatedAt: string
}

export interface MatchFilter {
  category?: string
  minFollowers?: number
  maxFollowers?: number
  platform?: string
  minEngagement?: number
  maxBudget?: number
}

export interface MatchResponse {
  influencers: InfluencerProfile[]
  totalCount: number
  creditsUsed: number
  creditsRemaining: number
}
