export type MessageType = 'comment' | 'mention' | 'dm' | 'reply'
export type MessagePlatform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'
export type MessageStatus = 'unread' | 'read' | 'replied' | 'archived'

export interface InboxMessage {
  id: number
  type: MessageType
  platform: MessagePlatform
  senderName: string
  senderAvatar: string
  content: string
  videoTitle?: string
  videoId?: string
  status: MessageStatus
  isStarred: boolean
  sentiment: 'positive' | 'neutral' | 'negative'
  createdAt: string
  repliedAt?: string
  replyContent?: string
}

export interface InboxFilters {
  platform: MessagePlatform | 'ALL'
  type: MessageType | 'ALL'
  status: MessageStatus | 'ALL'
  searchText: string
}
