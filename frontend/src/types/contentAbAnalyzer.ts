export interface ContentAbTest {
  id: number
  title: string
  variantA: ContentVariant
  variantB: ContentVariant
  status: 'RUNNING' | 'COMPLETED' | 'PAUSED'
  winner: 'A' | 'B' | null
  confidence: number
  startDate: string
  endDate: string | null
  createdAt: string
}

export interface ContentVariant {
  id: number
  label: string
  videoId: number
  videoTitle: string
  views: number
  likes: number
  comments: number
  ctr: number
  avgWatchTime: number
}

export interface ContentAbSummary {
  totalTests: number
  completedTests: number
  avgConfidence: number
  winRateA: number
  winRateB: number
}
