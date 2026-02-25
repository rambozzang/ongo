export type PlatformType = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'

export interface AgencyCreator {
  id: number
  name: string
  profileImageUrl: string | null
  channels: AgencyChannel[]
  totalSubscribers: number
  totalViews: number
  totalRevenue: number
  recentGrowthPercent: number
  status: 'active' | 'inactive' | 'pending'
  joinedAt: string
}

export interface AgencyChannel {
  id: number
  platform: PlatformType
  channelName: string
  subscriberCount: number
  videoCount: number
}

export interface AgencyKpi {
  totalCreators: number
  totalSubscribers: number
  totalViews: number
  totalRevenue: number
  subscribersChange: number
  viewsChangePercent: number
  revenueChangePercent: number
  creatorsChange: number
}

export interface CreatorComparison {
  creatorId: number
  creatorName: string
  color: string
  data: { date: string; views: number; engagement: number }[]
}

export interface AgencyScheduleItem {
  id: number
  creatorId: number
  creatorName: string
  videoTitle: string
  platforms: PlatformType[]
  scheduledAt: string
  status: 'scheduled' | 'published' | 'failed'
}

export interface AgencyActivity {
  id: number
  creatorName: string
  action: string
  target: string
  createdAt: string
  type: 'upload' | 'schedule' | 'analytics' | 'channel' | 'setting'
}

export interface ClientPortalLink {
  id: number
  creatorId: number
  creatorName: string
  token: string
  expiresAt: string
  isActive: boolean
  createdAt: string
}

export interface ClientPortalData {
  creator: {
    name: string
    profileImageUrl: string | null
    bio: string | null
  }
  channels: AgencyChannel[]
  kpi: {
    totalViews: number
    totalSubscribers: number
    engagementRate: number
    estimatedROI: number
    viewsChangePercent: number
    subscribersChange: number
    engagementChangePercent: number
    roiChangePercent: number
  }
  campaigns: ClientCampaign[]
  upcomingContent: ClientScheduleItem[]
  recentPerformance: { date: string; views: number; engagement: number }[]
}

export interface ClientCampaign {
  id: number
  name: string
  status: 'active' | 'completed' | 'upcoming'
  startDate: string
  endDate: string
  totalViews: number
  totalEngagement: number
  deliverables: number
  completedDeliverables: number
}

export interface ClientScheduleItem {
  id: number
  title: string
  platform: PlatformType
  scheduledAt: string
  status: 'scheduled' | 'published'
}
