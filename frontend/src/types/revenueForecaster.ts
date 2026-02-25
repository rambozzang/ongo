export type ForecastPeriod = '1M' | '3M' | '6M' | '1Y'
export type RevenueSource = 'AD_REVENUE' | 'SPONSORSHIP' | 'MERCHANDISE' | 'MEMBERSHIP' | 'SUPER_CHAT' | 'AFFILIATE'

export interface RevenueDataPoint {
  month: string
  actual?: number
  forecast?: number
  lowerBound?: number
  upperBound?: number
}

export interface RevenueBreakdown {
  source: RevenueSource
  currentMonthly: number
  forecastMonthly: number
  growthRate: number
  share: number
}

export interface ForecastScenario {
  id: number
  name: string
  description: string
  uploadFrequency: number
  avgViewsPerVideo: number
  subscriberGrowthRate: number
  forecastData: RevenueDataPoint[]
  totalForecast: number
}

export interface RevenueForecast {
  id: number
  period: ForecastPeriod
  currentMonthlyRevenue: number
  forecastedMonthlyRevenue: number
  growthRate: number
  breakdowns: RevenueBreakdown[]
  scenarios: ForecastScenario[]
  chartData: RevenueDataPoint[]
  confidence: number
  generatedAt: string
}

export interface ForecastRequest {
  period: ForecastPeriod
  includeScenarios: boolean
}

export interface ForecastResponse {
  forecast: RevenueForecast
  creditsUsed: number
  creditsRemaining: number
}
