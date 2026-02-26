export type TestStatus = 'ACTIVE' | 'COMPLETED' | 'PENDING'

export interface ThumbnailVariant {
  ctr: number
  impressions: number
  clicks: number
}

export interface ThumbnailTest {
  id: number
  videoTitle: string
  platform: string
  status: TestStatus
  variantA: ThumbnailVariant
  variantB: ThumbnailVariant
  winner: 'A' | 'B' | null
  startedAt: string
  endedAt?: string
}

export interface ThumbnailAbTestSummary {
  totalTests: number
  activeTests: number
  avgCtrImprovement: number
  bestVariantWinRate: number
  topPlatform: string
}
