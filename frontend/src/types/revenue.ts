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

// CPM/RPM
export interface CpmRpmItem {
  platform: string
  cpm: number
  rpm: number
  impressions: number
  views: number
  revenueMicro: number
}

export interface CpmRpmResponse {
  platforms: CpmRpmItem[]
}

// 브랜드딜 수익
export interface BrandDealRevenueItem {
  id: number
  brandName: string
  dealValue: number
  dealValueKrw: number
  status: string
  platform: string | null
}

export interface BrandDealRevenueResponse {
  deals: BrandDealRevenueItem[]
  totalRevenue: number
  totalRevenueKrw: number
}
