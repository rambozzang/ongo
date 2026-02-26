export interface CommunityPost {
  id: number
  type: PostType
  title: string
  content: string
  authorName: string
  authorAvatar?: string
  isCreator: boolean
  likes: number
  comments: number
  shares: number
  isPinned: boolean
  tags: string[]
  attachments: PostAttachment[]
  createdAt: string
  updatedAt: string
}

export type PostType = 'ANNOUNCEMENT' | 'DISCUSSION' | 'POLL' | 'QNA' | 'BEHIND_SCENES' | 'FAN_ART'

export interface PostAttachment {
  id: number
  type: 'IMAGE' | 'VIDEO' | 'FILE'
  url: string
  name: string
  size: number
}

export interface PostComment {
  id: number
  postId: number
  authorName: string
  authorAvatar?: string
  isCreator: boolean
  content: string
  likes: number
  createdAt: string
}

export interface CommunityPoll {
  id: number
  postId: number
  question: string
  options: PollOption[]
  totalVotes: number
  endsAt: string
  isActive: boolean
}

export interface PollOption {
  id: number
  text: string
  votes: number
  percentage: number
}

export interface CommunitySummary {
  totalPosts: number
  totalMembers: number
  activeMembersToday: number
  totalLikes: number
  postsThisWeek: number
}

export interface CreatePostRequest {
  type: PostType
  title: string
  content: string
  tags: string[]
  isPinned: boolean
}
