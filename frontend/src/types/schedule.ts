import type { Platform } from './channel'

export type ScheduleStatus = 'SCHEDULED' | 'PUBLISHED' | 'FAILED' | 'CANCELLED'

export interface Schedule {
  id: number
  videoId: number
  videoTitle: string
  thumbnailUrl: string | null
  scheduledAt: string
  platforms: SchedulePlatform[]
  status: ScheduleStatus
  createdAt: string
  updatedAt: string
}

export interface SchedulePlatform {
  platform: Platform
  scheduledAt: string
  status: ScheduleStatus
}

export interface ScheduleCreateRequest {
  videoId: number
  scheduledAt: string
  platforms: SchedulePlatformRequest[]
}

export interface SchedulePlatformRequest {
  platform: Platform
  scheduledAt?: string
}

export interface ScheduleUpdateRequest {
  scheduledAt?: string
  platforms?: SchedulePlatformRequest[]
}

export type CalendarView = 'month' | 'week' | 'list'
