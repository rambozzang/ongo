import type { Platform } from './channel'

export type CalendarPeriod = '2weeks' | '3weeks' | '4weeks'
export type PostingFrequency = 'daily' | '3perWeek' | '2perWeek' | '1perWeek'
export type ContentCategory = string
export type AiCalendarViewMode = 'weekly' | 'monthly'

export type SlotStatus = 'suggested' | 'confirmed' | 'edited'

export interface SeasonEvent {
  date: string
  name: string
  nameEn: string
  type: 'holiday' | 'season' | 'event'
}

export interface ContentSlot {
  id: string
  date: string
  time: string
  title: string
  category: ContentCategory
  platform: Platform
  trendScore: number
  trendKeywords: string[]
  seasonEvent?: SeasonEvent
  status: SlotStatus
  description?: string
}

export interface AiCalendarGenerateRequest {
  period: CalendarPeriod
  frequency: PostingFrequency
  platforms: Platform[]
  categories: ContentCategory[]
  channelId?: number
  includeSeasonEvents: boolean
}

export interface AiCalendarResult {
  id: string
  slots: ContentSlot[]
  generatedAt: string
  period: CalendarPeriod
  seasonEvents: SeasonEvent[]
  creditsUsed: number
  creditsRemaining: number
}

export interface AiCalendarApplyRequest {
  calendarId: string
  slotIds: string[]
}
