import type { AiEstimate } from '@/types/aiEstimate'

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

/** 감사 시점의 실측 지표(최근 periodDays일). 수집하지 않는 지표는 null — 0 이 아니다. */
export interface ChannelAuditMeasured {
  periodDays: number
  totalViews: number | null
  /** 이전 같은 기간 대비 조회수 증감(%) */
  totalViewsChange: number | null
  totalLikes: number | null
  totalComments: number | null
  subscriberCount: number | null
  analyzedVideoCount: number
}

export interface ChannelAuditReport {
  id: number
  /** 예전 보고서에만 있는 AI 점수. 새 보고서는 null. 값이 있으면 overallScoreEstimate 와 함께 보여야 한다 */
  overallScore: number | null
  overallScoreEstimate: AiEstimate | null
  /** 이 기능 도입 전 보고서는 null */
  measured: ChannelAuditMeasured | null
  forecastEstimate: AiEstimate
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
