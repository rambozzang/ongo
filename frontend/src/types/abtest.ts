export type TestType = 'thumbnail' | 'title' | 'description'
export type TestStatus = 'draft' | 'running' | 'completed' | 'cancelled'

export interface TestVariant {
  id: string
  label: string
  content: string
  thumbnailUrl?: string
  impressions: number
  clicks: number
  ctr: number
  watchTime: number
  isWinner: boolean
}

export interface ABTest {
  id: number
  name: string
  videoTitle: string
  videoId: string
  type: TestType
  status: TestStatus
  variants: TestVariant[]
  startDate: string
  endDate: string | null
  duration: number  // hours
  sampleSize: number
  confidence: number  // percentage
  createdAt: string
  completedAt: string | null
}

// API types
export interface ABTestVariantResponse {
  id: number
  variantName: string
  title: string | null
  description: string | null
  thumbnailUrl: string | null
  views: number
  clicks: number
  engagementRate: number
}

export interface ABTestResponse {
  id: number
  videoId: number | null
  testName: string
  status: string
  metricType: string
  winnerVariantId: number | null
  startedAt: string | null
  endedAt: string | null
  createdAt: string | null
  variants: ABTestVariantResponse[]
}

export interface ABTestListResponse {
  tests: ABTestResponse[]
  totalCount: number
}

export interface CreateABTestRequest {
  videoId?: number
  testName: string
  metricType?: string
  variants?: Array<{
    variantName: string
    title?: string
    description?: string
    thumbnailUrl?: string
  }>
}

export interface UpdateABTestRequest {
  testName?: string
  metricType?: string
}

// Statistics types
export interface VariantStatistics {
  variantId: number
  name: string
  impressions: number
  conversions: number
  conversionRate: number
  confidenceInterval: [number, number]
  isWinner: boolean
}

export interface ABTestStatisticsResponse {
  testId: number
  sampleSizeRequired: number
  currentSampleSize: number
  sampleProgress: number
  confidence: number
  pValue: number
  isSignificant: boolean
  winnerVariantId: number | null
  variants: VariantStatistics[]
}
