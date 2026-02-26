export interface OptimalSlot {
  id: number
  platform: string
  dayOfWeek: string
  hour: number
  score: number
  audienceOnline: number
  competitionLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  reason: string
  createdAt: string
}

export interface ScheduleRecommendation {
  id: number
  videoId: number
  videoTitle: string
  currentSchedule: string | null
  recommendedSchedule: string
  platform: string
  expectedImprovement: number
  confidence: number
  status: 'PENDING' | 'APPLIED' | 'DISMISSED'
  createdAt: string
}

export interface ScheduleOptimizerSummary {
  totalRecommendations: number
  appliedCount: number
  avgImprovement: number
  bestDay: string
  bestHour: number
}
