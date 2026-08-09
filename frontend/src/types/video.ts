import type { Platform } from './channel'

export type MediaType = 'VIDEO' | 'IMAGE'

export type UploadStatus =
  | 'DRAFT'
  | 'UPLOADING'
  | 'PROCESSING'
  | 'REVIEW'
  | 'PUBLISHED'
  | 'FAILED'
  | 'REJECTED'
  | 'UNCONFIRMED'
  | 'PARTIALLY_PUBLISHED'
  | 'CANCELLED'

export type Visibility = 'PUBLIC' | 'PRIVATE' | 'UNLISTED'

export interface ContentImage {
  id: number
  imageUrl: string
  displayOrder: number
  width?: number
  height?: number
}

export interface Video {
  id: number
  userId: number
  title: string
  description: string | null
  tags: string[]
  category: string | null
  mediaType: MediaType
  fileUrl: string
  thumbnailUrl: string | null
  thumbnailCandidates: string[]
  fileSize: number | null
  status: UploadStatus
  visibility: Visibility
  createdAt: string
  updatedAt: string
  uploads: VideoUpload[]
  contentImages?: ContentImage[]
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
  mediaType?: MediaType
}

export interface VideoPublishRequest {
  platforms: PlatformPublishConfig[]
  scheduledAt?: string
}

export interface VideoDownloadAvailability {
  available: boolean
  reason?: string | null
}

export interface PlatformPublishConfig {
  platform: Platform
  title: string
  description: string
  tags: string[]
  visibility: Visibility
  scheduledAt?: string
}

export interface PlatformUploadCapability {
  platform: Platform
  directVideoUpload: boolean
  cloudVideoUpload: boolean
  scheduling: boolean
  maxFileSizeBytes: number
  maxTitleLength: number
  maxDescriptionLength: number
  maxTagCount: number
  acceptedExtensions: string[]
  unavailableReason: string | null
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

export interface VideoTranslation {
  id: number
  videoId: number
  language: string
  title?: string
  description?: string
  tags: string[]
  subtitleContent?: string
  status: string
  createdAt?: string
}

export interface VideoFeedItem {
  platformVideoId: string
  platform: Platform
  channelName: string
  title: string
  description: string | null
  thumbnailUrl: string | null
  platformUrl: string | null
  viewCount: number
  likeCount: number
  commentCount: number
  shareCount: number
  publishedAt: string | null
}

export interface VideoFeedResponse {
  items: VideoFeedItem[]
  platforms: Platform[]
  errors: string[] | null
}
