export interface TrendPrediction {
  id: number
  keyword: string
  category: string
  platform: string
  currentScore: number
  predictedScore: number
  confidence: number
  direction: 'RISING' | 'STABLE' | 'DECLINING'
  peakDate: string | null
  createdAt: string
}

export interface TrendTopic {
  id: number
  predictionId: number
  topic: string
  relatedKeywords: string[]
  videoCount: number
  avgViews: number
  growthRate: number
}

export interface TrendPredictorSummary {
  totalPredictions: number
  risingTrends: number
  accuracy: number
  topCategory: string
  lastUpdated: string
}
