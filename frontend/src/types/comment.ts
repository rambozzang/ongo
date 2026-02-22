import type { Platform } from './channel'

export type CommentSentiment = 'positive' | 'neutral' | 'negative'

export interface Comment {
  id: number
  videoId: number
  videoTitle: string
  platform: Platform
  platformCommentId?: string
  author: string
  authorAvatar?: string
  authorChannelUrl?: string
  content: string
  likeCount: number
  replyCount: number
  sentiment: CommentSentiment
  isReplied: boolean
  isHidden: boolean
  isPinned: boolean
  replyContent?: string
  repliedAt?: string
  syncedAt?: string
  createdAt: string
}

// Backend DTO types
export interface CommentResponse {
  id: number
  videoId: number | null
  platform: string | null
  platformCommentId: string | null
  authorName: string
  authorAvatarUrl: string | null
  authorChannelUrl: string | null
  content: string
  sentiment: string
  likeCount: number
  replyCount: number
  isReplied: boolean
  isHidden: boolean
  isPinned: boolean
  replyContent: string | null
  repliedAt: string | null
  publishedAt: string | null
  syncedAt: string | null
  createdAt: string | null
}

export interface CommentStats {
  total: number
  positive: number
  neutral: number
  negative: number
}

export interface CommentCapabilities {
  canListComments: boolean
  canReply: boolean
  canLike: boolean
  canDelete: boolean
  canHide: boolean
}

export interface CommentSyncResult {
  totalSynced: number
  totalNew: number
  errors: string[]
}

export interface CommentListResponse {
  comments: CommentResponse[]
  totalCount: number
  stats: CommentStats
  capabilities: Record<string, CommentCapabilities>
}

// 감정 트렌드
export interface SentimentTrendPoint {
  date: string
  positive: number
  neutral: number
  negative: number
}

export interface SentimentSummary {
  totalPositive: number
  totalNeutral: number
  totalNegative: number
  trend: 'IMPROVING' | 'STABLE' | 'WORSENING'
}

export interface SentimentTrendResponse {
  data: SentimentTrendPoint[]
  summary: SentimentSummary
}

// FAQ 클러스터링
export interface FaqCluster {
  topic: string
  questionCount: number
  sampleQuestions: string[]
  suggestedReply: string
}

export interface FaqClusterResponse {
  clusters: FaqCluster[]
  generatedAt: string
}

// 배치 AI 답변 초안
export interface AiDraftItem {
  commentId: number
  commentContent: string
  draftReply: string
  tone: string
}

export interface BatchAiDraftResponse {
  drafts: AiDraftItem[]
  totalCreditsUsed: number
}
