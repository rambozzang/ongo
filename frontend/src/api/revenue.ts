import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  RevenueSummary,
  RevenueTrend,
  PlatformRevenueData,
  CpmRpmResponse,
  BrandDealRevenueResponse,
  RevenueInsight,
  RevenueInsightListResponse,
  RevenueAlertConfig,
  RevenueAlertConfigListResponse,
} from '@/types/revenue'

function periodToDays(period: string): number {
  const match = period.match(/^(\d+)d$/)
  if (match) return parseInt(match[1], 10)
  switch (period) {
    case '30d': return 30
    case '90d': return 90
    case '1y': return 365
    default: return 30
  }
}

export const revenueApi = {
  summary(period: string = '30d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<RevenueSummary>>('/analytics/revenue', { params: { days } })
      .then(unwrapResponse)
  },

  trends(period: string = '30d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<RevenueTrend>>('/analytics/revenue/trends', { params: { days } })
      .then(unwrapResponse)
  },

  platformRevenue(period: string = '30d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<PlatformRevenueData>>('/analytics/revenue/platform', { params: { days } })
      .then(unwrapResponse)
  },

  cpmRpm(period: string = '30d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<CpmRpmResponse>>('/analytics/revenue/cpm-rpm', { params: { days } })
      .then(unwrapResponse)
  },

  brandDealRevenue(period: string = '90d') {
    const days = periodToDays(period)
    return apiClient
      .get<ResData<BrandDealRevenueResponse>>('/analytics/revenue/brand-deals', { params: { days } })
      .then(unwrapResponse)
  },

  // AI 인사이트
  insights(page = 0, size = 10) {
    return apiClient
      .get<ResData<RevenueInsightListResponse>>('/revenue/insights', { params: { page, size } })
      .then(unwrapResponse)
  },

  generateInsight() {
    return apiClient
      .post<ResData<RevenueInsight>>('/revenue/insights/generate', {})
      .then(unwrapResponse)
  },

  // 알림 설정
  alertConfigs() {
    return apiClient
      .get<ResData<RevenueAlertConfigListResponse>>('/revenue/alerts/config')
      .then(unwrapResponse)
  },

  saveAlertConfig(config: Omit<RevenueAlertConfig, 'id'>) {
    return apiClient
      .post<ResData<RevenueAlertConfig>>('/revenue/alerts/config', config)
      .then(unwrapResponse)
  },

  updateAlertConfig(id: number, config: Partial<RevenueAlertConfig>) {
    return apiClient
      .put<ResData<RevenueAlertConfig>>(`/revenue/alerts/config/${id}`, config)
      .then(unwrapResponse)
  },

  deleteAlertConfig(id: number) {
    return apiClient
      .delete<ResData<void>>(`/revenue/alerts/config/${id}`)
      .then(unwrapResponse)
  },
}
