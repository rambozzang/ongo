export type SegmentType = 'AGE' | 'GENDER' | 'REGION' | 'INTEREST' | 'BEHAVIOR' | 'CUSTOM'

export interface AudienceSegment {
  id: number
  name: string
  type: SegmentType
  criteria: SegmentCriteria
  size: number
  percentage: number
  avgWatchTime: number
  avgRetention: number
  avgCtr: number
  avgEngagement: number
  growthRate: number
  revenueContribution: number
  topContentCategories: string[]
  bestPostingTimes: { day: string; hour: number; score: number }[]
  createdAt: string
  updatedAt: string
}

export interface SegmentCriteria {
  ageRange?: { min: number; max: number }
  gender?: 'MALE' | 'FEMALE' | 'ALL'
  regions?: string[]
  interests?: string[]
  platforms?: string[]
  minWatchTime?: number
  minEngagement?: number
}

export interface SegmentInsight {
  segmentId: number
  contentRecommendations: { category: string; reason: string; confidence: number }[]
  titleStyle: string
  thumbnailStyle: string
  optimalLength: { min: number; max: number; unit: string }
  toneRecommendation: string
  growthOpportunities: string[]
}

export interface SegmentComparison {
  segments: AudienceSegment[]
  metrics: { metric: string; values: { segmentId: number; value: number }[] }[]
}

export interface CreateSegmentRequest {
  name: string
  type: SegmentType
  criteria: SegmentCriteria
}
