export interface SentimentResult {
  id: number
  contentTitle: string
  platform: string
  totalComments: number
  positiveCount: number
  neutralCount: number
  negativeCount: number
  positiveRate: number
  avgSentimentScore: number
  topPositiveKeywords: string[]
  topNegativeKeywords: string[]
  analyzedAt: string
  createdAt: string
}

export interface CommentSentiment {
  id: number
  resultId: number
  commentText: string
  authorName: string
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
  score: number
  keywords: string[]
  createdAt: string
}

export interface SentimentTrend {
  date: string
  positiveRate: number
  neutralRate: number
  negativeRate: number
  totalComments: number
}

export interface SentimentAnalyzerSummary {
  totalAnalyzed: number
  avgPositiveRate: number
  avgSentimentScore: number
  mostPositiveContent: string
  mostNegativeContent: string
}

export interface AnalyzeSentimentRequest {
  contentId: number
  contentTitle: string
  platform: string
}
