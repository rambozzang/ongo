export interface PlatformHealthScore {
  id: number
  platform: string
  overallScore: number
  growthScore: number
  engagementScore: number
  consistencyScore: number
  audienceScore: number
  trend: string
  checkedAt: string
}

export interface HealthIssue {
  id: number
  healthScoreId: number
  severity: string
  category: string
  description: string
  recommendation: string
}

export interface PlatformHealthSummary {
  totalPlatforms: number
  avgHealthScore: number
  healthiestPlatform: string
  criticalIssues: number
  overallTrend: string
}
