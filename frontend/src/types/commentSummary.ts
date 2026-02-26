export interface CommentSummaryResult {
  id: number
  videoTitle: string
  platform: string
  totalComments: number
  positivePct: number
  negativePct: number
  neutralPct: number
  topTopics: string[]
  aiSummary: string
  analyzedAt: string
}

export interface TopComment {
  id: number
  summaryId: number
  author: string
  text: string
  likes: number
  sentiment: string
  isHighlighted: boolean
}

export interface CommentSummarySummary {
  totalAnalyzed: number
  avgPositive: number
  avgNegative: number
  mostDiscussedTopic: string
  totalComments: number
}
