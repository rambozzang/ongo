export interface FanDemographic {
  id: number
  platform: string
  ageGroup: string
  gender: string
  percentage: number
  country: string
  city: string
}

export interface FanBehavior {
  id: number
  platform: string
  activeHour: number
  activeDay: string
  watchDuration: number
  returnRate: number
  commentRate: number
  shareRate: number
}

export interface FanSegment {
  id: number
  name: string
  description: string
  memberCount: number
  avgEngagement: number
  topInterests: string[]
  platform: string
}

export interface FanInsightsSummary {
  totalFans: number
  topAgeGroup: string
  topCountry: string
  avgWatchDuration: number
  peakActiveHour: number
}
