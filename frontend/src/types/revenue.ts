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

// AI 수익 인사이트
export type InsightType = 'REVENUE_TREND' | 'PLATFORM_PERFORMANCE' | 'ANOMALY' | 'OPPORTUNITY' | 'FORECAST'

export interface RevenueInsight {
  id: number
  insightType: InsightType
  // 백엔드가 content(JSON 문자열)와 confidence(BigDecimal → number) 반환
  content: string
  confidence: number | null
  platform?: string | null
  createdAt: string | null
}

/** insights 목록 API 래퍼 — 백엔드 RevenueInsightListResponse */
export interface RevenueInsightListResponse {
  insights: RevenueInsight[]
  totalElements: number
  page: number
  size: number
}

// 수익 알림 설정
export type AlertType = 'DAILY_SUMMARY' | 'ANOMALY_DETECTION' | 'GOAL_ACHIEVEMENT' | 'MILESTONE'

export interface RevenueAlertConfig {
  id: number
  alertType: AlertType
  // 백엔드 필드명에 맞춤: isEnabled / scheduleTime / thresholdValue
  isEnabled: boolean
  scheduleTime?: string | null   // HH:mm:ss (DAILY_SUMMARY용)
  thresholdValue?: number | null // 임계값 (ANOMALY_DETECTION용)
}

/** alertConfigs API 래퍼 — 백엔드 RevenueAlertConfigListResponse */
export interface RevenueAlertConfigListResponse {
  configs: RevenueAlertConfig[]
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
