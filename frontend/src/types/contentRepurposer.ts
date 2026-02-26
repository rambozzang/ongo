export interface RepurposeJob {
  id: number
  originalTitle: string
  originalPlatform: string
  targetPlatform: string
  targetFormat: string
  status: string
  progress: number
  outputUrl: string | null
  createdAt: string
}

export interface RepurposeTemplate {
  id: number
  name: string
  sourcePlatform: string
  targetPlatform: string
  targetFormat: string
  description: string
  isDefault: boolean
}

export interface RepurposeRequest {
  videoId: number
  targetPlatform: string
  targetFormat: string
  templateId?: number
}

export interface ContentRepurposerSummary {
  totalJobs: number
  completedJobs: number
  avgTimeSaved: number
  topTargetPlatform: string
  successRate: number
}
