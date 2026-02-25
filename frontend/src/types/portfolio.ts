export type PlatformType = 'youtube' | 'tiktok' | 'instagram' | 'naver'

export interface ChannelStat {
  id: number
  platform: PlatformType
  channelName: string
  subscribers: number
  totalViews: number
  avgViews: number
  engagementRate: number
  profileImageUrl: string
}

export interface ShowcaseContent {
  id: number
  title: string
  thumbnailUrl: string
  platform: PlatformType
  viewCount: number
  likeCount: number
  publishedAt: string
  videoUrl: string
  order: number
}

export interface BrandCollaboration {
  id: number
  brandName: string
  brandLogoUrl: string
  campaignTitle: string
  platform: PlatformType
  deliverables: string
  completedAt: string
  resultSummary: string
}

export type PortfolioTheme = 'minimal' | 'creative' | 'professional' | 'bold'

export interface PortfolioProfile {
  displayName: string
  bio: string
  profileImageUrl: string
  categories: string[]
  contactEmail: string
  website: string
}

export interface Portfolio {
  id: number
  profile: PortfolioProfile
  channelStats: ChannelStat[]
  showcaseContents: ShowcaseContent[]
  brandCollaborations: BrandCollaboration[]
  theme: PortfolioTheme
  slug: string
  isPublic: boolean
  publicUrl: string
  createdAt: string
  updatedAt: string
}
