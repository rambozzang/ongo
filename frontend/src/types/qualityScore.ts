export type QualityGrade = 'S' | 'A' | 'B' | 'C' | 'D'
export type QualityCategory = 'TITLE' | 'THUMBNAIL' | 'DESCRIPTION' | 'TAGS' | 'AUDIO' | 'VIDEO' | 'SEO' | 'ENGAGEMENT_POTENTIAL'

export interface QualityMetric {
  category: QualityCategory
  score: number
  maxScore: number
  grade: QualityGrade
  feedback: string
  suggestions: string[]
}

export interface QualityReport {
  id: number
  videoId: number
  videoTitle: string
  overallScore: number
  overallGrade: QualityGrade
  metrics: QualityMetric[]
  topIssues: string[]
  improvements: { action: string; expectedImpact: string; priority: 'HIGH' | 'MEDIUM' | 'LOW' }[]
  competitorAvgScore: number
  checkedAt: string
}

export interface QualityCheckRequest {
  videoId?: number
  title: string
  description: string
  tags: string[]
  thumbnailUrl?: string
  platform: string
}

export interface QualityCheckResponse {
  report: QualityReport
  creditsUsed: number
  creditsRemaining: number
}
