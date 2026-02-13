export type RecurrencePattern = 'daily' | 'weekly' | 'biweekly' | 'monthly'
export type DayOfWeek = 0 | 1 | 2 | 3 | 4 | 5 | 6 // Sun-Sat

export interface RecurringRule {
  id: string
  name: string
  pattern: RecurrencePattern
  daysOfWeek: DayOfWeek[] // for weekly/biweekly
  dayOfMonth?: number // for monthly (1-31)
  nthWeekday?: { week: number; day: DayOfWeek } // e.g., 2nd Tuesday
  timeSlot: string // HH:mm format
  platforms: string[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface ContentQueueSlot {
  id: string
  ruleId: string
  scheduledDate: string
  videoId?: number
  videoTitle?: string
  videoThumbnail?: string
  status: 'empty' | 'assigned' | 'published' | 'failed'
}

export interface RecurringScheduleStats {
  totalRules: number
  activeRules: number
  upcomingSlots: number
  emptySlots: number
}
