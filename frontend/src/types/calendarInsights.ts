export interface CalendarInsight {
  id: number
  date: string
  dayOfWeek: string
  hour: number
  uploadCount: number
  avgViews: number
  avgEngagement: number
  score: number
}

export interface OptimalTimeSlot {
  dayOfWeek: string
  hour: number
  score: number
  expectedViews: number
  expectedEngagement: number
  reason: string
}

export interface UploadPattern {
  platform: string
  totalUploads: number
  avgUploadsPerWeek: number
  mostActiveDay: string
  mostActiveHour: number
  consistency: number
}

export interface CalendarInsightsSummary {
  totalUploads: number
  avgUploadsPerWeek: number
  bestDay: string
  bestHour: number
  consistencyScore: number
}
