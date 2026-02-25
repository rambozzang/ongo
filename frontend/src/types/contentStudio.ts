// 콘텐츠 스튜디오 타입 정의

export type ClipStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type CaptionStatus = 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED'
export type ThumbnailStatus = 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED'
export type ThumbnailStyle = 'MINIMAL' | 'BOLD' | 'CINEMATIC' | 'PLAYFUL' | 'PROFESSIONAL'

export interface Clip {
  id: number
  videoId: number
  title: string
  startTime: number
  endTime: number
  duration: number
  outputUrl: string | null
  thumbnailUrl: string | null
  status: ClipStatus
  aspectRatio: string
  createdAt: string
}

export interface Caption {
  id: number
  videoId: number
  language: string
  segments: CaptionSegment[]
  fullText: string
  status: CaptionStatus
  createdAt: string
}

export interface CaptionSegment {
  startTime: number
  endTime: number
  text: string
}

export interface Thumbnail {
  id: number
  videoId: number
  imageUrl: string
  style: ThumbnailStyle
  overlayText: string | null
  status: ThumbnailStatus
  createdAt: string
}

export interface VideoSummary {
  id: number
  title: string
  duration: number | null
  thumbnailUrl: string | null
  fileUrl: string
}

// API Request/Response 타입
export interface CreateClipRequest {
  videoId: number
  title: string
  startTime: number
  endTime: number
  aspectRatio?: string
}

export interface CreateClipResponse {
  clip: Clip
  creditsUsed: number
  creditsRemaining: number
}

export interface GenerateCaptionRequest {
  videoId: number
  language?: string
}

export interface GenerateCaptionResponse {
  caption: Caption
  creditsUsed: number
  creditsRemaining: number
}

export interface UpdateCaptionRequest {
  segments: CaptionSegment[]
}

export interface GenerateThumbnailRequest {
  videoId: number
  style: ThumbnailStyle
  overlayText?: string
  count?: number
}

export interface GenerateThumbnailResponse {
  thumbnails: Thumbnail[]
  creditsUsed: number
  creditsRemaining: number
}

export interface ContentStudioHistory {
  id: number
  type: 'CLIP' | 'CAPTION' | 'THUMBNAIL'
  videoTitle: string
  description: string
  creditsUsed: number
  createdAt: string
}
