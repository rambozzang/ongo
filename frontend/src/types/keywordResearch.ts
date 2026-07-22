export type KeywordPlatform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'

export interface KeywordPlatformResult {
  platform: KeywordPlatform
  searchVolume: string
  competition: 'LOW' | 'MEDIUM' | 'HIGH'
  trend: 'RISING' | 'STABLE' | 'DECLINING'
  opportunityScore: number
  relatedKeywords: string[]
}

export interface KeywordResearchResult {
  keyword: string
  platforms: KeywordPlatformResult[]
  analyzedAt: string
}

export interface KeywordHistoryItem {
  id: number
  keyword: string
  platforms: KeywordPlatform[]
  result: KeywordResearchResult | null
  createdAt: string
}

export interface KeywordHistoryResponse {
  items: KeywordHistoryItem[]
  totalCount: number
}
