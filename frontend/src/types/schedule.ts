import type { Platform } from './channel'

export type ScheduleStatus = 'SCHEDULED' | 'PROCESSING' | 'PUBLISHED' | 'PARTIALLY_PUBLISHED' | 'UNCONFIRMED' | 'FAILED' | 'CANCELLED'

export interface Schedule {
  id: number
  videoId: number
  videoTitle: string
  thumbnailUrl: string | null
  scheduledAt: string
  platforms: SchedulePlatform[]
  status: ScheduleStatus
  recurrence?: RecurrenceConfig
  parentScheduleId?: number | null
  createdAt: string
  updatedAt: string
}

export interface SchedulePlatform {
  platform: Platform
  channelId?: number | null
  scheduledAt: string
  status?: ScheduleStatus | null
  platformUrl?: string | null
}

export interface ScheduleCreateRequest {
  videoId: number
  scheduledAt: string
  platforms: SchedulePlatformRequest[]
  recurrence?: RecurrenceConfig
}

export interface SchedulePlatformRequest {
  platform: Platform
  channelId?: number
  scheduledAt?: string
}

export interface ScheduleUpdateRequest {
  scheduledAt?: string
  platforms?: SchedulePlatformRequest[]
}

export type CalendarView = 'month' | 'week' | 'list'

export type ScheduleSortMode = 'date' | 'platform' | 'status' | 'manual'

/** 캘린더 그리드에 표시되는 단일 이벤트 (플랫폼 단위로 분해된 예약) */
export interface CalendarEvent {
  id: string
  videoId: string
  title: string
  platform: Platform | null
  scheduledAt: string
  status: 'scheduled' | 'published' | 'failed'
  color?: string
}

export type RecurrenceType = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY'

export interface RecurrenceConfig {
  type: RecurrenceType
  interval: number // 매 N일/주/월
  daysOfWeek?: number[] // 주간 반복 시 요일 (0=일, 1=월, ..., 6=토)
  endDate?: string // 반복 종료일
  maxOccurrences?: number // 최대 반복 횟수
}
