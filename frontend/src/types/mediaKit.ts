export interface MediaKitTemplate {
  id: number
  name: string
  style: 'MODERN' | 'CLASSIC' | 'MINIMAL' | 'CREATIVE'
  previewUrl?: string
}

export interface AudienceDemographic {
  ageGroup: string
  percentage: number
  gender: 'MALE' | 'FEMALE' | 'OTHER'
}

export interface PlatformStat {
  platform: string
  followers: number
  avgViews: number
  engagementRate: number
  growthRate: number
}

export interface CampaignResult {
  id: number
  brandName: string
  campaignName: string
  platform: string
  views: number
  engagement: number
  roi: number
  date: string
}

export interface RateCard {
  type: 'SPONSORED_VIDEO' | 'PRODUCT_PLACEMENT' | 'STORY_POST' | 'SHORT_FORM' | 'LIVE_STREAM' | 'BUNDLE'
  price: number
  currency: string
  description: string
}

export interface MediaKit {
  id: number
  title: string
  templateStyle: MediaKitTemplate['style']
  bio: string
  profileImageUrl?: string
  platforms: PlatformStat[]
  demographics: AudienceDemographic[]
  topContent: { title: string; views: number; thumbnail?: string }[]
  campaignResults: CampaignResult[]
  rateCards: RateCard[]
  contactEmail: string
  createdAt: string
  updatedAt: string
  publishedUrl?: string
  status: 'DRAFT' | 'PUBLISHED'
}

export interface MediaKitGenerateRequest {
  templateStyle: MediaKitTemplate['style']
  includeRateCards: boolean
  includeCampaigns: boolean
  customBio?: string
}
