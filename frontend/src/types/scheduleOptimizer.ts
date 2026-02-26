export interface OptimalSlot {
  id: number
  platform: string
  dayOfWeek: string
  hour: number
  score: number
  audienceOnline: number
  competitionLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  reason: string
  timeSlot?: string
  createdAt: string
}

export interface ScheduleRecommendation {
  id: number
  videoId: number
  videoTitle: string
  title?: string
  currentSchedule: string | null
  recommendedSchedule: string
  suggestedTime?: string
  platform: string
  expectedImprovement: number
  confidence: number
  status: 'PENDING' | 'APPLIED' | 'DISMISSED'
  applied?: boolean
  createdAt: string
}

export interface ScheduleOptimizerSummary {
  totalRecommendations: number
  appliedCount: number
  avgImprovement: number
  avgPerformanceBoost?: number
  bestDay: string
  bestHour: number
  bestTimeSlot?: string
}
