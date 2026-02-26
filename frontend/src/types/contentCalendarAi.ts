export interface CalendarSuggestion {
  id: number
  title: string
  description: string
  suggestedDate: string
  suggestedTime: string
  platform: string
  contentType: string
  topic: string
  expectedEngagement: number
  confidence: number
  status: string
  createdAt: string
}

export interface CalendarSlot {
  date: string
  time: string
  platform: string
  score: number
  reason: string
}

export interface ContentCalendarAiSummary {
  totalSuggestions: number
  acceptedCount: number
  avgConfidence: number
  bestTimeSlot: string
  topPlatform: string
}

export interface GenerateCalendarRequest {
  startDate: string
  endDate: string
  platforms: string[]
  frequency: string
}
