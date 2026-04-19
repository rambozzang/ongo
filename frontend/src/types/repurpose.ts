export interface RepurposeClip {
  startSeconds: number
  endSeconds: number
  title: string
  viralScore: number
  reason: string
}

export interface RepurposeJob {
  id: number
  videoId: number
  videoTitle: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  clips: RepurposeClip[]
  createdAt: string
  completedAt: string | null
  errorMessage: string | null
}

export interface RepurposeDetail extends RepurposeJob {
  platform: string
  thumbnailUrl: string | null
}
