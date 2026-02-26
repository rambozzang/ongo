export interface AlgorithmInsight {
  id: number
  platform: string
  factor: string
  importance: number
  currentScore: number
  recommendation: string
  category: 'CONTENT' | 'ENGAGEMENT' | 'METADATA' | 'CONSISTENCY' | 'AUDIENCE'
  trend: 'UP' | 'DOWN' | 'STABLE'
  updatedAt: string
}

export interface AlgorithmScore {
  platform: string
  overallScore: number
  contentScore: number
  engagementScore: number
  metadataScore: number
  consistencyScore: number
  audienceScore: number
  updatedAt: string
}

export interface AlgorithmChange {
  id: number
  platform: string
  title: string
  description: string
  impact: 'HIGH' | 'MEDIUM' | 'LOW'
  affectedAreas: string[]
  detectedAt: string
  recommendation: string
}

export interface AlgorithmInsightsSummary {
  avgOverallScore: number
  bestPlatform: string
  worstFactor: string
  recentChanges: number
  improvementSuggestions: number
}
