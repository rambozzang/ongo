import type { Platform } from './channel'

export type UploadStatus =
  | 'DRAFT'
  | 'UPLOADING'
  | 'PROCESSING'
  | 'REVIEW'
  | 'PUBLISHED'
  | 'FAILED'
  | 'REJECTED'

export type Visibility = 'PUBLIC' | 'PRIVATE' | 'UNLISTED'

export interface Video {
  id: number
  userId: number
  title: string
  description: string | null
  tags: string[]
  category: string | null
  fileUrl: string
  thumbnailUrl: string | null
  thumbnailCandidates: string[]
  duration: number | null
  fileSize: number | null
  resolution: string | null
  status: UploadStatus
  visibility: Visibility
  createdAt: string
  updatedAt: string
  uploads: VideoUpload[]
  variants?: VideoVariant[]
}

export interface VideoUpload {
  id: number
  videoId: number
  platform: Platform
  status: UploadStatus
  platformVideoId: string | null
  platformUrl: string | null
  title: string
  description: string | null
  tags: string[]
  errorMessage: string | null
  publishedAt: string | null
  createdAt: string
}

export type VariantStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface VideoVariant {
  platform: Platform
  status: VariantStatus
  fileUrl: string | null
  fileSizeBytes: number | null
  width: number | null
  height: number | null
  errorMessage: string | null
}

export interface VideoListFilter {
  platform?: Platform
  status?: UploadStatus
  startDate?: string
  endDate?: string
  keyword?: string
}

export interface VideoCreateRequest {
  title: string
  description?: string
  tags?: string[]
  category?: string
  thumbnailUrl?: string
  visibility?: Visibility
}

export interface VideoPublishRequest {
  platforms: PlatformPublishConfig[]
  scheduledAt?: string
}

export interface PlatformPublishConfig {
  platform: Platform
  title: string
  description: string
  tags: string[]
  visibility: Visibility
}

export interface UploadProgress {
  bytesUploaded: number
  bytesTotal: number
  percentage: number
  speed: number
  remainingSeconds: number
}

export type OptimizationSeverity = 'GOOD' | 'WARNING' | 'ERROR'

export interface OptimizationSuggestion {
  field: string
  severity: OptimizationSeverity
  message: string
  currentValue: string | null
  recommendedValue: string | null
}

export interface OptimizationResult {
  platform: Platform
  score: number
  suggestions: OptimizationSuggestion[]
}

export interface OptimizationCheckRequest {
  title: string
  description?: string
  tags?: string[]
  thumbnailUrl?: string
  platforms?: Platform[]
}

export interface OptimizationCheckResponse {
  results: OptimizationResult[]
}
