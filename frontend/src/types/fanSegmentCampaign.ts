export type CampaignStatus = 'DRAFT' | 'SCHEDULED' | 'SENDING' | 'COMPLETED'

export type CampaignType = 'EMAIL' | 'PUSH' | 'IN_APP' | 'SMS'

export interface FanSegment {
  id: number
  name: string
  criteria: string
  fanCount: number
  avgEngagement: number
  createdAt: string
}

export interface SegmentCampaign {
  id: number
  name: string
  segmentId: number
  segmentName: string
  type: CampaignType
  status: CampaignStatus
  targetCount: number
  sentCount: number
  openRate: number
  clickRate: number
  scheduledAt?: string
  completedAt?: string
  createdAt: string
}

export interface CampaignSummary {
  totalCampaigns: number
  activeCampaigns: number
  avgOpenRate: number
  totalReach: number
}
