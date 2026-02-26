export type ConnectionStatus = 'CONNECTED' | 'PENDING' | 'NONE'

export type CollabStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED'

export type CollabType = 'COLLAB_VIDEO' | 'GUEST_APPEARANCE' | 'CROSS_PROMOTION' | 'LIVE_TOGETHER' | 'CHALLENGE'

export type CreatorCategory =
  | 'BEAUTY'
  | 'GAMING'
  | 'FOOD'
  | 'TECH'
  | 'TRAVEL'
  | 'MUSIC'
  | 'EDUCATION'
  | 'LIFESTYLE'
  | 'FITNESS'
  | 'OTHER'

export interface Creator {
  id: number
  name: string
  avatarUrl?: string
  platform: string
  subscriberCount: number
  category: CreatorCategory
  matchScore: number
  connectionStatus: ConnectionStatus
  bio?: string
  avgViews: number
  engagementRate: number
}

export interface CollabRequest {
  id: number
  senderId: number
  senderName: string
  senderAvatar?: string
  receiverId: number
  receiverName: string
  receiverAvatar?: string
  message: string
  status: CollabStatus
  collabType: CollabType
  createdAt: string
}

export interface NetworkSummary {
  totalConnections: number
  pendingRequests: number
  sentRequests: number
  avgMatchScore: number
  collabCompleted: number
}

export interface NetworkFilter {
  category?: CreatorCategory
  platform?: string
  minMatchScore?: number
}
