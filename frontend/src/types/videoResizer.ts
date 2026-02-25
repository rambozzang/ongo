export type AspectRatio = '16:9' | '9:16' | '1:1' | '4:5' | '4:3'
export type ResizeStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type PlatformTarget = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM_REELS' | 'NAVER_CLIP' | 'YOUTUBE_SHORTS'

export interface ResizePreset {
  platform: PlatformTarget
  label: string
  aspectRatio: AspectRatio
  width: number
  height: number
  maxDuration?: number
}

export interface ResizeJob {
  id: number
  videoId: number
  videoTitle: string
  originalAspectRatio: AspectRatio
  targetAspectRatio: AspectRatio
  platform: PlatformTarget
  status: ResizeStatus
  progress: number
  outputUrl?: string
  thumbnailUrl?: string
  createdAt: string
  completedAt?: string
}

export interface CreateResizeRequest {
  videoId: number
  targets: {
    platform: PlatformTarget
    aspectRatio: AspectRatio
    focusPoint?: { x: number; y: number }
    smartCrop: boolean
  }[]
}

export interface CreateResizeResponse {
  jobs: ResizeJob[]
  creditsUsed: number
  creditsRemaining: number
}

export interface VideoForResize {
  id: number
  title: string
  thumbnailUrl: string
  duration: number
  aspectRatio: AspectRatio
  uploadedAt: string
}
