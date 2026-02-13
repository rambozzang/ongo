export type BlockType = 'link' | 'header' | 'social' | 'video' | 'image' | 'divider' | 'text'
export type ThemeStyle = 'minimal' | 'rounded' | 'gradient' | 'dark' | 'colorful'

export interface LinkBlock {
  id: number
  type: 'link'
  title: string
  url: string
  icon?: string
  isVisible: boolean
  clickCount: number
}

export interface HeaderBlock {
  id: number
  type: 'header'
  text: string
  isVisible: boolean
}

export interface SocialBlock {
  id: number
  type: 'social'
  platform: string
  url: string
  isVisible: boolean
}

export interface VideoBlock {
  id: number
  type: 'video'
  title: string
  videoUrl: string
  thumbnailUrl: string
  isVisible: boolean
}

export interface DividerBlock {
  id: number
  type: 'divider'
  isVisible: boolean
}

export interface TextBlock {
  id: number
  type: 'text'
  content: string
  isVisible: boolean
}

export type BioBlock = LinkBlock | HeaderBlock | SocialBlock | VideoBlock | DividerBlock | TextBlock

export interface BioPage {
  id: string
  username: string
  displayName: string
  bio: string
  avatarUrl: string
  theme: ThemeStyle
  backgroundColor: string
  textColor: string
  buttonColor: string
  buttonTextColor: string
  blocks: BioBlock[]
  totalViews: number
  totalClicks: number
  createdAt: string
  updatedAt: string
}
