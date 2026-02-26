export interface ChannelHealthMetric {
  id: number
  channelId: number
  channelName: string
  platform: string
  overallScore: number
  growthScore: number
  engagementScore: number
  consistencyScore: number
  audienceScore: number
  monetizationScore: number
  measuredAt: string
}

export interface HealthTrend {
  id: number
  metricId: number
  category: 'GROWTH' | 'ENGAGEMENT' | 'CONSISTENCY' | 'AUDIENCE' | 'MONETIZATION'
  date: string
  score: number
  change: number
  recommendation: string | null
}

export interface ChannelHealthSummary {
  totalChannels: number
  avgOverallScore: number
  topChannel: string
  weakestArea: string
  trendsUp: number
  trendsDown: number
}
