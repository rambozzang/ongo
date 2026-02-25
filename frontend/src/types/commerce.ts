export type CommercePlatform = 'COUPANG' | 'NAVER_SMARTSTORE' | 'TIKTOK_SHOP'

export type CommercePlatformStatus = 'CONNECTED' | 'DISCONNECTED' | 'EXPIRED'

export interface CommercePlatformConnection {
  platform: CommercePlatform
  status: CommercePlatformStatus
  storeName?: string
  connectedAt?: string
  expiresAt?: string
}

export interface CommerceProduct {
  id: number
  name: string
  imageUrl?: string
  price: number
  platform: CommercePlatform
  category?: string
  linkedVideoCount: number
  clicks: number
  conversions: number
  revenue: number
  affiliateUrl?: string
  createdAt: string
}

export interface AffiliateLink {
  id: number
  productId: number
  productName: string
  originalUrl: string
  affiliateUrl: string
  shortUrl?: string
  platform: CommercePlatform
  clicks: number
  conversions: number
  revenue: number
  createdAt: string
}

export interface VideoProductLink {
  id: number
  videoId: number
  videoTitle: string
  videoThumbnail?: string
  productId: number
  productName: string
  productImageUrl?: string
  platform: CommercePlatform
  clicks: number
  conversions: number
  revenue: number
  linkedAt: string
}

export interface CommerceKpi {
  totalRevenue: number
  totalClicks: number
  conversionRate: number
  linkedProductCount: number
  revenueGrowth: number
  clicksGrowth: number
}

export interface CommerceRevenueTrend {
  date: string
  revenue: number
  clicks: number
  conversions: number
  platform?: CommercePlatform
}

export interface PlatformPerformance {
  platform: CommercePlatform
  revenue: number
  clicks: number
  conversions: number
  conversionRate: number
  productCount: number
}
