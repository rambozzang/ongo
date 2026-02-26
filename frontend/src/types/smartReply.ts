export type ReplyTone = 'FRIENDLY' | 'PROFESSIONAL' | 'CASUAL' | 'GRATEFUL' | 'HUMOROUS'
export type ReplyContext = 'COMMENT' | 'DM' | 'MENTION' | 'REVIEW'

export interface SmartReplyRule {
  id: number
  name: string
  isActive: boolean
  context: ReplyContext
  triggerKeywords: string[]
  sentiment?: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL'
  tone: ReplyTone
  templateText: string
  useAi: boolean
  autoSend: boolean
  platform?: string
  replyCount: number
  lastUsed?: string
}

export interface SmartReplySuggestion {
  id: string
  originalText: string
  originalAuthor: string
  platform: string
  context: ReplyContext
  sentiment: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL'
  suggestions: { text: string; tone: ReplyTone; confidence: number }[]
  videoId?: string
  videoTitle?: string
  createdAt: string
}

export interface SmartReplyStats {
  totalRepliesSent: number
  avgResponseTime: number
  sentimentBreakdown: { positive: number; negative: number; neutral: number }
  topKeywords: { keyword: string; count: number }[]
  repliesByPlatform: { platform: string; count: number }[]
  automatedPercentage: number
  satisfactionScore: number
}

export interface SmartReplyConfig {
  defaultTone: ReplyTone
  enableAutoReply: boolean
  maxAutoRepliesPerDay: number
  excludeKeywords: string[]
  replyDelay: number
  platforms: string[]
}
