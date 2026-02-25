import type { Platform } from './channel'

export interface PredictionMetrics {
  expectedViews: number
  expectedLikes: number
  expectedComments: number
  expectedShares: number
  engagementRate: number
  confidenceScore: number
}

export interface PredictionResult {
  id: number
  videoId?: number
  title: string
  platform: Platform
  metrics: PredictionMetrics
  optimalUploadTime: string
  tags: string[]
  createdAt: string
}

export interface HeatmapCell {
  dayOfWeek: number
  hour: number
  reachScore: number
}

export interface OptimalTimeRecommendation {
  dayOfWeek: number
  hour: number
  expectedReach: number
  reason: string
}

export interface TitleSuggestion {
  original: string
  suggested: string
  expectedImprovement: number
  reason: string
}

export interface TagSuggestion {
  tag: string
  trendScore: number
  relevanceScore: number
  competitionLevel: 'low' | 'medium' | 'high'
}

export interface CompetitorComparison {
  competitorTitle: string
  competitorViews: number
  competitorLikes: number
  competitorEngagement: number
  yourPredictedViews: number
  yourPredictedLikes: number
  yourPredictedEngagement: number
  advantage: 'ahead' | 'behind' | 'similar'
}

export interface PredictionHistoryItem {
  id: number
  videoId: number
  videoTitle: string
  platform: Platform
  predictedViews: number
  actualViews: number
  predictedLikes: number
  actualLikes: number
  predictedEngagement: number
  actualEngagement: number
  accuracyScore: number
  createdAt: string
}

export interface PredictionRequest {
  videoId?: number
  title: string
  description: string
  tags: string[]
  platform: Platform
  category?: string
}

export interface PredictionSummary {
  totalPredictions: number
  avgAccuracy: number
  bestAccuracy: number
  recentPredictions: PredictionHistoryItem[]
}
