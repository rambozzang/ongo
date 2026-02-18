export type TemplateCategory = 'title' | 'description' | 'tags' | 'thumbnail' | 'full'
export type TemplatePlatform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP' | 'ALL'

export interface ContentTemplate {
  id: number
  name: string
  category: TemplateCategory
  platform: TemplatePlatform
  titleTemplate?: string
  descriptionTemplate?: string
  tagsTemplate?: string[]
  thumbnailStyle?: string
  variables: string[]  // e.g. ['{{video_title}}', '{{date}}', '{{channel_name}}']
  usageCount: number
  isFavorite: boolean
  createdAt: string
  updatedAt: string
}

// Backend DTO types
export interface TemplateResponse {
  id: number
  name: string
  titleTemplate: string | null
  descriptionTemplate: string | null
  tags: string[]
  category: string | null
  platform: string | null
  usageCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface TemplateListResponse {
  templates: TemplateResponse[]
  totalCount: number
}

export interface CreateTemplateRequest {
  name: string
  titleTemplate?: string
  descriptionTemplate?: string
  tags?: string[]
  category?: string
  platform?: string
}

export interface UpdateTemplateRequest {
  name: string
  titleTemplate?: string
  descriptionTemplate?: string
  tags?: string[]
  category?: string
  platform?: string
}
