export type WebhookEvent =
  | 'video.uploaded'
  | 'video.published'
  | 'video.failed'
  | 'schedule.executed'
  | 'analytics.milestone'
  | 'credit.low'
  | 'subscription.changed'

export interface WebhookDelivery {
  id: number
  webhookId: number
  event: WebhookEvent
  statusCode: number
  responseBody?: string
  sentAt: string
  duration: number
}

export interface Webhook {
  id: number
  url: string
  events: WebhookEvent[]
  secret?: string
  isActive: boolean
  lastTriggeredAt?: string
  failureCount: number
  createdAt: string
  recentDeliveries?: WebhookDelivery[]
}

export const WEBHOOK_EVENT_LABELS: Record<WebhookEvent, { label: string; description: string }> = {
  'video.uploaded': {
    label: '영상 업로드',
    description: '영상 업로드 완료 시',
  },
  'video.published': {
    label: '영상 게시',
    description: '영상 게시 완료 시',
  },
  'video.failed': {
    label: '영상 실패',
    description: '영상 게시 실패 시',
  },
  'schedule.executed': {
    label: '예약 실행',
    description: '예약 게시 실행 시',
  },
  'analytics.milestone': {
    label: '분석 마일스톤',
    description: '분석 마일스톤 달성 시',
  },
  'credit.low': {
    label: '크레딧 부족',
    description: '크레딧 부족 시',
  },
  'subscription.changed': {
    label: '구독 변경',
    description: '구독 변경 시',
  },
}
