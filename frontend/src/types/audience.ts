export interface AudienceProfile {
  id: number
  authorName: string
  platform: string
  avatarUrl?: string
  engagementScore: number
  tags: string[]
  totalInteractions: number
  positiveRatio: number
  firstSeenAt?: string
  lastSeenAt?: string
}

export interface AudienceSegment {
  id: number
  name: string
  description?: string
  conditions?: string
  autoUpdate: boolean
  memberCount: number
  createdAt?: string
}
