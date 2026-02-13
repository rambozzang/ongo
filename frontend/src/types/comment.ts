import type { Platform } from './channel'

export type CommentSentiment = 'positive' | 'neutral' | 'negative'

export interface Comment {
  id: number
  videoId: number
  videoTitle: string
  platform: Platform
  author: string
  authorAvatar?: string
  content: string
  likeCount: number
  replyCount: number
  sentiment: CommentSentiment
  isHidden: boolean
  isPinned: boolean
  createdAt: string
}

// Backend DTO types
export interface CommentResponse {
  id: number
  videoId: number | null
  platform: string | null
  authorName: string
  authorAvatarUrl: string | null
  content: string
  sentiment: string
  isReplied: boolean
  replyContent: string | null
  repliedAt: string | null
  publishedAt: string | null
  createdAt: string | null
}

export interface CommentListResponse {
  comments: CommentResponse[]
  totalCount: number
}
