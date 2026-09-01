export interface PlatformRevenueItem {
  platform: string
  revenueMicro: number
  revenueKrw: number
  /**
   * 전체 수익에서 이 플랫폼이 차지하는 비율(%). **전체가 0 이면 `null`.**
   *
   * 비율은 분모가 0 이면 정의되지 않는다. 예전 서버는 그 자리에 `0.0` 을 넣어, 수익이
   * 아직 한 푼도 잡히지 않은 상태가 **"비중 0%"** 라는 관측처럼 보였다.
   *
   * **`?? 0` 을 하지 말 것.** 분모가 양수일 때의 `0` 은 실측 비중이라, 0 으로 채우면
   * 둘이 같아진다.
   */
  percentage: number | null
}

export interface RevenueSummary {
  totalRevenue: number
  totalRevenueKrw: number
  /**
   * 직전 동일 길이 기간 대비 성장률(%). **이전 기간 수익이 0 이면 `null`** 이다.
   *
   * `0` 이나 `100` 으로 채우지 말 것. 예전 서버는 이 자리에 임의의 `100.0` 을 넣어,
   * 첫 수익 1,000 원과 100만 원이 똑같이 "+100%" 로 보였다.
   */
  growthPercent: number | null
  platformBreakdown: PlatformRevenueItem[]
  platformRevenueAvailable?: boolean
  platformRevenueUnavailableReason?: string | null
  platformRevenueReconnectRequired?: boolean
}

export interface RevenueTrendPoint {
  date: string
  revenueMicro: number
  revenueKrw: number
  platform?: string
}

export interface RevenueTrend {
  data: RevenueTrendPoint[]
  platformRevenueAvailable?: boolean
  platformRevenueUnavailableReason?: string | null
  platformRevenueReconnectRequired?: boolean
}

export interface PlatformRevenueData {
  platforms: PlatformRevenueItem[]
  platformRevenueAvailable?: boolean
  platformRevenueUnavailableReason?: string | null
  platformRevenueReconnectRequired?: boolean
}

// CPM/RPM
/**
 * 플랫폼별 단가. **분모가 없으면 단가는 `null`** 이다.
 *
 * 노출이 0 이면 CPM 이, 조회가 0 이면 RPM 이 성립하지 않는다. 서버는 그 자리에
 * `null` 과 `unavailableMetrics` 의 사유를 준다. 여기서 `?? 0` 을 하면 화면이
 * "₩0.00" 을 그려 **재지 않은 것이 수익성 0 이라는 관측으로 바뀐다.**
 *
 * 반대로 분모가 양수인데 수익이 0 이면 그 0 은 실측이므로 그대로 보여준다.
 */
export interface CpmRpmItem {
  platform: string
  /** 노출 1,000회당 수익. 노출이 0 이면 `null`. */
  cpm: number | null
  /** 조회 1,000회당 수익. 조회가 0 이면 `null`. */
  rpm: number | null
  impressions: number
  views: number
  revenueMicro: number
  /** 계산할 수 없었던 단가와 그 이유. 키는 `cpm` / `rpm`. */
  unavailableMetrics?: Record<string, string>
}

export interface CpmRpmResponse {
  platforms: CpmRpmItem[]
  platformRevenueAvailable?: boolean
  platformRevenueUnavailableReason?: string | null
  platformRevenueReconnectRequired?: boolean
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
