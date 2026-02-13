export type IdeaStatus = 'idea' | 'planning' | 'producing' | 'completed'
export type IdeaPriority = 'low' | 'medium' | 'high'

export interface ContentIdea {
  id: number
  title: string
  description: string
  status: IdeaStatus
  priority: IdeaPriority
  platform: ('YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP')[]
  tags: string[]
  dueDate: string | null
  createdAt: string
  updatedAt: string
  notes: string
  thumbnailUrl?: string
}
