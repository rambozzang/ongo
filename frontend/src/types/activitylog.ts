export type ActivityAction =
  | 'upload'
  | 'publish'
  | 'edit'
  | 'delete'
  | 'schedule'
  | 'ai_generate'
  | 'channel_connect'
  | 'channel_disconnect'
  | 'settings_change'
  | 'team_invite'
  | 'team_remove'
  | 'login'
  | 'credit_purchase'

export type ActivityEntityType =
  | 'video'
  | 'schedule'
  | 'channel'
  | 'user'
  | 'team'
  | 'credit'
  | 'settings'

export interface ActivityLog {
  id: string
  userId: string
  userName: string
  userAvatar?: string
  action: ActivityAction
  entityType: ActivityEntityType
  entityId?: string
  entityName?: string
  details?: Record<string, any>
  ipAddress?: string
  createdAt: string
}

export interface ActivityLogFilter {
  action?: ActivityAction | null
  dateRange?: ActivityDateRange | null
  userId?: string | null
  searchQuery?: string
}

export type ActivityDateRange =
  | 'today'
  | 'yesterday'
  | 'this_week'
  | 'this_month'
  | 'custom'

export interface ActivityDateCustomRange {
  startDate: string
  endDate: string
}

export const ACTION_LABELS: Record<ActivityAction, string> = {
  upload: '업로드',
  publish: '게시',
  edit: '편집',
  delete: '삭제',
  schedule: '예약',
  ai_generate: 'AI 생성',
  channel_connect: '채널 연결',
  channel_disconnect: '채널 해제',
  settings_change: '설정 변경',
  team_invite: '팀 초대',
  team_remove: '팀 삭제',
  login: '로그인',
  credit_purchase: '크레딧 구매',
}

export const ENTITY_TYPE_LABELS: Record<ActivityEntityType, string> = {
  video: '영상',
  schedule: '예약',
  channel: '채널',
  user: '사용자',
  team: '팀',
  credit: '크레딧',
  settings: '설정',
}
