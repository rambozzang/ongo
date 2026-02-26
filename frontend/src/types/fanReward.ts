export interface FanReward {
  id: number
  name: string
  description: string
  pointsCost: number
  category: 'BADGE' | 'EXCLUSIVE' | 'SHOUTOUT' | 'MERCH' | 'MEETUP'
  isActive: boolean
  claimedCount: number
  maxClaims: number | null
  imageUrl: string | null
  createdAt: string
}

export interface FanActivity {
  id: number
  fanName: string
  activityType: 'COMMENT' | 'LIKE' | 'SHARE' | 'WATCH' | 'SUBSCRIBE'
  points: number
  description: string
  timestamp: string
}

export interface FanRewardSummary {
  totalRewards: number
  activeRewards: number
  totalPointsDistributed: number
  totalClaims: number
  topCategory: string
}
