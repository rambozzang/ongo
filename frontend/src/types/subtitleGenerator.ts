export interface SubtitleJob {
  id: number
  videoId: number
  videoTitle: string
  platform: string
  language: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  progress: number
  subtitleUrl: string | null
  wordCount: number
  duration: number
  createdAt: string
  completedAt: string | null
}

export interface SubtitleSegment {
  id: number
  jobId: number
  startTime: number
  endTime: number
  text: string
  confidence: number
}

export interface SubtitleGeneratorSummary {
  totalJobs: number
  completedJobs: number
  avgAccuracy: number
  totalWords: number
  recentLanguage: string
}
