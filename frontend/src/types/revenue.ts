export interface PlatformRevenueItem {
  platform: string
  revenueMicro: number
  revenueKrw: number
  percentage: number
}

export interface RevenueSummary {
  totalRevenue: number
  totalRevenueKrw: number
  growthPercent: number
  platformBreakdown: PlatformRevenueItem[]
}

export interface RevenueTrendPoint {
  date: string
  revenueMicro: number
  revenueKrw: number
  platform?: string
}

export interface RevenueTrend {
  data: RevenueTrendPoint[]
}

export interface PlatformRevenueData {
  platforms: PlatformRevenueItem[]
}
