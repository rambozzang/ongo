export interface ActionItem {
  priority: 'HIGH' | 'MEDIUM' | 'LOW'
  action: string
  expectedImpact: string
}

export interface OutlierVideo {
  videoTitle: string
  metric: string
  reason: string
}

export interface ChannelAuditReport {
  id: number
  overallScore: number
  strengths: string[]
  weaknesses: string[]
  actionItems: ActionItem[]
  outlierVideos: OutlierVideo[]
  growthForecast: string
  createdAt: string
}

export interface ChannelAuditListResponse {
  audits: ChannelAuditReport[]
  totalCount: number
}
