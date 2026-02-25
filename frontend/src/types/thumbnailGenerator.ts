export type ThumbnailStyle = 'BOLD_TEXT' | 'MINIMALIST' | 'COLLAGE' | 'FACE_FOCUS' | 'CINEMATIC' | 'CLICKBAIT'
export type GenerationStatus = 'IDLE' | 'GENERATING' | 'DONE' | 'FAILED'

export interface ThumbnailTemplate {
  id: number
  name: string
  style: ThumbnailStyle
  previewUrl: string
  popularity: number
}

export interface GeneratedThumbnail {
  id: number
  imageUrl: string
  style: ThumbnailStyle
  ctrPrediction: number
  prompt: string
  createdAt: string
}

export interface ThumbnailGenerateRequest {
  videoTitle: string
  style: ThumbnailStyle
  keywords: string[]
  platform: string
  includeText: boolean
  textOverlay?: string
  colorScheme?: string
}

export interface ThumbnailGenerateResponse {
  thumbnails: GeneratedThumbnail[]
  creditsUsed: number
  creditsRemaining: number
}

export interface ThumbnailHistory {
  id: number
  videoTitle: string
  thumbnails: GeneratedThumbnail[]
  selectedThumbnailId?: number
  createdAt: string
}
