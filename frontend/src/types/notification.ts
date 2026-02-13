export type NotificationType =
  | 'upload_success'
  | 'upload_failed'
  | 'platform_published'
  | 'platform_failed'
  | 'schedule_reminder'
  | 'schedule_completed'
  | 'token_expiring'
  | 'token_expired'
  | 'credit_low'
  | 'credit_depleted'
  | 'milestone'
  | 'report_ready'
  | 'payment_reminder'
  | 'payment_failed'

export type NotificationCategory = 'upload' | 'schedule' | 'channel' | 'ai' | 'analytics' | 'subscription'

export interface Notification {
  id: number
  type: NotificationType
  category: NotificationCategory
  title: string
  message: string
  isRead: boolean
  referenceType?: string // 'video' | 'schedule' | 'channel' | 'credit' | 'report' | 'payment'
  referenceId?: number
  createdAt: string
}

export interface NotificationSetting {
  category: NotificationCategory
  inApp: boolean
  email: boolean
  kakao: boolean
}
