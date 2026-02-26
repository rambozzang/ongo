export interface AudienceOverlapResult {
  id: number
  platformA: string
  platformB: string
  overlapPercent: number
  uniqueToA: number
  uniqueToB: number
  sharedAudience: number
  analyzedAt: string
}

export interface OverlapSegment {
  id: number
  name: string
  platforms: string[]
  audienceSize: number
  engagementRate: number
  topInterest: string
}

export interface AudienceOverlapSummary {
  totalAnalyses: number
  avgOverlap: number
  maxOverlapPair: string
  totalUniqueAudience: number
  mostSharedSegment: string
}
