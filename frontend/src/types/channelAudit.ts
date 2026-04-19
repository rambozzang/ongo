export interface ActionItem {
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  category: string
  title: string
  description: string
}

export interface OutlierVideo {
  platformVideoId: string
  title: string
  thumbnailUrl: string | null
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt: string
  reason: string
}

export interface ChannelAuditReport {
  id: number
  overallScore: number
  consistencyScore: number
  engagementScore: number
  growthScore: number
  contentQualityScore: number
  strengths: string[]
  weaknesses: string[]
  actionItems: ActionItem[]
  outlierVideos: OutlierVideo[]
  summary: string
  createdAt: string
}

export interface ChannelAuditListResponse {
  reports: ChannelAuditReport[]
  totalCount: number
}
