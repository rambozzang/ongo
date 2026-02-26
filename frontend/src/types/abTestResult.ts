export interface ABTestResult {
  id: number
  testId: number
  testName: string
  status: 'RUNNING' | 'COMPLETED' | 'PAUSED' | 'CANCELLED'
  startDate: string
  endDate?: string
  variants: TestVariant[]
  winner?: string
  confidence: number
  metric: string
  platform: string
  createdAt: string
}

export interface TestVariant {
  id: string
  name: string
  description: string
  thumbnailUrl?: string
  views: number
  clicks: number
  ctr: number
  avgWatchTime: number
  engagement: number
  conversions: number
  isControl: boolean
  isWinner: boolean
}

export interface TestTimeline {
  date: string
  variantA: number
  variantB: number
  variantC?: number
}

export interface ABTestResultSummary {
  totalTests: number
  runningTests: number
  completedTests: number
  avgConfidence: number
  avgImprovement: number
}
