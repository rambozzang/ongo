export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'
export type HeatmapMetric = 'VIEWS' | 'LIKES' | 'COMMENTS' | 'CTR' | 'WATCH_TIME'

export interface HeatmapCell {
  day: DayOfWeek
  hour: number
  value: number
  contentCount: number
}

export interface HeatmapData {
  metric: HeatmapMetric
  platform: string
  period: string
  cells: HeatmapCell[]
  bestSlots: { day: DayOfWeek; hour: number; value: number }[]
  worstSlots: { day: DayOfWeek; hour: number; value: number }[]
}

export interface UploadTimeRecommendation {
  platform: string
  bestDay: DayOfWeek
  bestHour: number
  expectedLift: number
  confidence: number
}

export interface HeatmapRequest {
  platform: string
  metric: HeatmapMetric
  period: string
}
