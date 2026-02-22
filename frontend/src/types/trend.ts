export interface Trend {
  id: number
  keyword: string
  score: number
  source: string
  category: string | null
  platform: string | null
  region: string
  date: string
}

export interface TrendAnalysis {
  summary: string
  recommendations: string[]
  topKeywords: Trend[]
}

export interface TrendAlert {
  id: number
  keyword: string
  threshold: number
  enabled: boolean
  createdAt: string | null
}

export interface CreateTrendAlertRequest {
  keyword: string
  threshold?: number
}

export interface UpdateTrendAlertRequest {
  keyword?: string
  threshold?: number
  enabled?: boolean
}
