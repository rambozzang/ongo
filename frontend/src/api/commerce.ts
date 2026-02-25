import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CommercePlatformConnection,
  CommerceProduct,
  AffiliateLink,
  VideoProductLink,
  CommerceKpi,
  CommerceRevenueTrend,
  PlatformPerformance,
  CommercePlatform,
} from '@/types/commerce'

export const commerceApi = {
  // 플랫폼 연동
  getPlatformConnections() {
    return apiClient
      .get<ResData<CommercePlatformConnection[]>>('/commerce/platforms')
      .then(unwrapResponse)
  },

  connectPlatform(platform: CommercePlatform) {
    return apiClient
      .post<ResData<CommercePlatformConnection>>('/commerce/platforms/connect', { platform })
      .then(unwrapResponse)
  },

  disconnectPlatform(platform: CommercePlatform) {
    return apiClient
      .post<ResData<void>>('/commerce/platforms/disconnect', { platform })
      .then(unwrapResponse)
  },

  // 상품
  getProducts(params?: { platform?: CommercePlatform; search?: string }) {
    return apiClient
      .get<ResData<CommerceProduct[]>>('/commerce/products', { params })
      .then(unwrapResponse)
  },

  // 어필리에이트 링크
  getAffiliateLinks() {
    return apiClient
      .get<ResData<AffiliateLink[]>>('/commerce/links')
      .then(unwrapResponse)
  },

  createAffiliateLink(productId: number) {
    return apiClient
      .post<ResData<AffiliateLink>>('/commerce/links', { productId })
      .then(unwrapResponse)
  },

  // 영상-상품 연결
  getVideoProductLinks() {
    return apiClient
      .get<ResData<VideoProductLink[]>>('/commerce/video-links')
      .then(unwrapResponse)
  },

  linkVideoProduct(videoId: number, productId: number) {
    return apiClient
      .post<ResData<VideoProductLink>>('/commerce/video-links', { videoId, productId })
      .then(unwrapResponse)
  },

  unlinkVideoProduct(linkId: number) {
    return apiClient
      .delete<ResData<void>>(`/commerce/video-links/${linkId}`)
      .then(unwrapResponse)
  },

  // 대시보드 / KPI
  getKpi(days: number = 30) {
    return apiClient
      .get<ResData<CommerceKpi>>('/commerce/kpi', { params: { days } })
      .then(unwrapResponse)
  },

  getRevenueTrends(days: number = 30) {
    return apiClient
      .get<ResData<CommerceRevenueTrend[]>>('/commerce/revenue/trends', { params: { days } })
      .then(unwrapResponse)
  },

  getPlatformPerformance(days: number = 30) {
    return apiClient
      .get<ResData<PlatformPerformance[]>>('/commerce/platforms/performance', { params: { days } })
      .then(unwrapResponse)
  },
}
