export type RecyclingFrequency = 'weekly' | 'biweekly' | 'monthly'

export type RecyclingStatus = 'published' | 'pending' | 'failed'

export type TitleVariation = 'same' | 'ai' | 'manual'

export interface RecyclingFilterCriteria {
  minViews?: number
  maxAge?: number // months
  categories?: string[]
  platforms?: string[]
}

export interface RecyclingQueue {
  id: number
  name: string
  filterCriteria: RecyclingFilterCriteria
  frequency: RecyclingFrequency
  scheduleDays: number[] // 0=Sun, 1=Mon, ..., 6=Sat
  scheduleTime: string // HH:mm format
  platforms: string[]
  isActive: boolean
  videoCount: number
  nextScheduledAt: string
  titleVariation: TitleVariation
  createdAt: string
}

export interface RecyclingHistory {
  id: number
  queueId: number
  videoId: number
  videoTitle: string
  platforms: string[]
  scheduledAt: string
  publishedAt: string | null
  status: RecyclingStatus
}

export interface RecyclingQueueCreateRequest {
  name: string
  filterCriteria: RecyclingFilterCriteria
  frequency: RecyclingFrequency
  scheduleDays: number[]
  scheduleTime: string
  platforms: string[]
  titleVariation: TitleVariation
}

// Recycling Suggestions (API-backed)
export type SuggestionType = 'REPOST' | 'CLIP' | 'REMIX' | 'UPDATE_METADATA'
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'DISMISSED'

export interface RecyclingSuggestion {
  id: number
  videoId: number
  suggestionType: SuggestionType
  reason: string | null
  suggestedPlatforms: string[]
  priorityScore: number
  status: SuggestionStatus
  createdAt: string | null
}
