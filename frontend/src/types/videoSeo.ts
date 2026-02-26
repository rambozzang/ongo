export interface SeoAnalysis {
  id: number
  videoTitle: string
  platform: string
  overallScore: number
  titleScore: number
  descriptionScore: number
  tagScore: number
  thumbnailScore: number
  suggestions: string[]
  analyzedAt: string
}

export interface SeoKeyword {
  id: number
  keyword: string
  searchVolume: number
  competition: string
  relevance: number
  trend: string
}

export interface SeoOptimizeRequest {
  videoId: number
  platform: string
}

export interface VideoSeoSummary {
  totalAnalyzed: number
  avgScore: number
  topKeyword: string
  improvementRate: number
  weakestArea: string
}
