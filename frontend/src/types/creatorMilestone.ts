export interface CreatorMilestone {
  id: number
  type: 'SUBSCRIBERS' | 'VIEWS' | 'VIDEOS' | 'WATCH_HOURS' | 'REVENUE' | 'CUSTOM'
  title: string
  description: string
  targetValue: number
  currentValue: number
  progress: number
  platform: string
  status: 'PENDING' | 'IN_PROGRESS' | 'ACHIEVED' | 'EXPIRED'
  achievedAt?: string
  targetDate?: string
  createdAt: string
}

export interface MilestoneTimeline {
  id: number
  milestoneId: number
  date: string
  value: number
  note?: string
}

export interface MilestoneReward {
  id: number
  milestoneId: number
  type: 'BADGE' | 'FEATURE_UNLOCK' | 'CREDITS' | 'CUSTOM'
  title: string
  description: string
  isRedeemed: boolean
}

export interface CreatorMilestoneSummary {
  totalMilestones: number
  achievedCount: number
  inProgressCount: number
  nextMilestone: string
  achievementRate: number
}

export interface CreateMilestoneRequest {
  type: string
  title: string
  description: string
  targetValue: number
  platform: string
  targetDate?: string
}
