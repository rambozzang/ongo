export interface RevenueStream {
  id: number
  channelId: number
  channelName: string
  source: 'AD' | 'MEMBERSHIP' | 'SUPERCHAT' | 'SPONSORSHIP' | 'MERCHANDISE' | 'AFFILIATE'
  amount: number
  currency: string
  period: string
  growth: number
  createdAt: string
}

export interface RevenueProjection {
  id: number
  channelId: number
  source: string
  currentMonthly: number
  projectedMonthly: number
  confidence: number
  projectionDate: string
  factors: string[]
  createdAt: string
}

export interface RevenueAnalyzerSummary {
  totalRevenue: number
  monthlyGrowth: number
  topSource: string
  channelCount: number
  projectedNextMonth: number
  avgConfidence: number
}
