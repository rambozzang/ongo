export type LicenseStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'UNKNOWN'
export type AssetType = 'MUSIC' | 'IMAGE' | 'FONT' | 'VIDEO_CLIP' | 'SOUND_EFFECT' | 'OTHER'
export type LicenseType = 'FREE' | 'ROYALTY_FREE' | 'CREATIVE_COMMONS' | 'PAID' | 'SUBSCRIPTION' | 'CUSTOM'

export interface ContentRight {
  id: number
  videoId: string
  videoTitle: string
  assetName: string
  assetType: AssetType
  licenseType: LicenseType
  licenseStatus: LicenseStatus
  source: string
  licenseUrl?: string
  expiresAt?: string
  purchasedAt?: string
  cost: number
  currency: string
  notes?: string
  createdAt: string
  updatedAt: string
}

export interface RightsAlert {
  id: number
  contentRightId: number
  assetName: string
  assetType: AssetType
  message: string
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  daysUntilExpiry: number
  isRead: boolean
  createdAt: string
}

export interface AlternativeAsset {
  name: string
  source: string
  licenseType: LicenseType
  cost: number
  previewUrl?: string
  similarity: number
}

export interface RightsSummary {
  totalAssets: number
  activeCount: number
  expiringCount: number
  expiredCount: number
  totalCost: number
  byType: { type: AssetType; count: number }[]
  byLicense: { license: LicenseType; count: number }[]
  upcomingExpirations: ContentRight[]
}

export interface CreateRightRequest {
  videoId: string
  videoTitle: string
  assetName: string
  assetType: AssetType
  licenseType: LicenseType
  source: string
  licenseUrl?: string
  expiresAt?: string
  cost?: number
  currency?: string
  notes?: string
}
