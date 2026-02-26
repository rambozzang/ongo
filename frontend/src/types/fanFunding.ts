export type FundingSource =
  | 'SUPER_CHAT'
  | 'MEMBERSHIP'
  | 'SUPER_THANKS'
  | 'TIKTOK_GIFT'
  | 'INSTAGRAM_BADGE'
  | 'NAVER_STAR'
  | 'EXTERNAL_DONATION'

export interface FundingTransaction {
  id: number
  source: FundingSource
  platform: string
  amount: number
  currency: string
  donorName: string
  message?: string
  videoId?: string
  videoTitle?: string
  createdAt: string
}

export interface FundingSummary {
  totalRevenue: number
  thisMonthRevenue: number
  lastMonthRevenue: number
  growthRate: number
  topDonors: { name: string; totalAmount: number; count: number }[]
  bySource: { source: FundingSource; amount: number; count: number; percentage: number }[]
  byPlatform: { platform: string; amount: number; count: number }[]
  dailyTrends: { date: string; amount: number; count: number }[]
  membershipCount: number
  membershipMRR: number
}

export interface FundingGoal {
  id: number
  title: string
  targetAmount: number
  currentAmount: number
  deadline?: string
  isActive: boolean
}

export interface FundingFilter {
  source?: FundingSource
  platform?: string
  dateFrom?: string
  dateTo?: string
}
