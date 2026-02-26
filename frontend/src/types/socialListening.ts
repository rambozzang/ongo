export type SentimentType = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
export type MentionSource = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'TWITTER' | 'BLOG' | 'NEWS'

export interface BrandMention {
  id: number
  source: MentionSource
  author: string
  text: string
  sentiment: SentimentType
  sentimentScore: number
  url: string
  reach: number
  createdAt: string
}

export interface SentimentTrend {
  date: string
  positive: number
  neutral: number
  negative: number
  total: number
}

export interface KeywordAlert {
  id: number
  keyword: string
  enabled: boolean
  mentionCount: number
  lastTriggered?: string
}

export interface SocialListeningReport {
  totalMentions: number
  sentimentBreakdown: { positive: number; neutral: number; negative: number }
  topMentions: BrandMention[]
  sentimentTrends: SentimentTrend[]
  topKeywords: { keyword: string; count: number; sentiment: SentimentType }[]
  alerts: KeywordAlert[]
  period: string
}
