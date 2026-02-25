export type AbTestStatus = 'DRAFT' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED'
export type AbTestType = 'THUMBNAIL' | 'TITLE' | 'DESCRIPTION' | 'TAGS'
export type VariantLabel = 'A' | 'B' | 'C' | 'D'

export interface AbTestVariant {
  id: string
  label: VariantLabel
  value: string
  thumbnailUrl?: string
  impressions: number
  clicks: number
  ctr: number
  views: number
  avgWatchTime: number
  isWinner: boolean
}

export interface AbTest {
  id: number
  videoId: number
  videoTitle: string
  type: AbTestType
  status: AbTestStatus
  variants: AbTestVariant[]
  startedAt?: string
  endedAt?: string
  durationHours: number
  totalImpressions: number
  confidenceLevel: number
  winnerId?: string
  createdAt: string
}

export interface CreateAbTestRequest {
  videoId: number
  type: AbTestType
  variants: {
    label: VariantLabel
    value: string
    thumbnailFile?: File
  }[]
  durationHours: number
}

export interface CreateAbTestResponse {
  test: AbTest
  creditsUsed: number
  creditsRemaining: number
}

export interface AbTestSummary {
  totalTests: number
  activeTests: number
  completedTests: number
  avgCtrImprovement: number
  bestPerformingType: AbTestType
}

export interface VideoForAbTest {
  id: number
  title: string
  thumbnailUrl: string
  currentCtr: number
  views: number
  publishedAt: string
  hasActiveTest: boolean
}
