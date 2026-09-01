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
  /**
   * 목록 응답(`GET /videos`)이 함께 주는 총 조회수. **잰 적이 없으면 `null`.**
   *
   * `null` 인 이유는 두 가지이고 [pendingViewUploads] 로 갈린다.
   *
   * - 대기 `0` 건: 조회수를 수집하는 업로드가 없다(Tumblr 는 `views` 자리에 노트 총합을
   *   넣고 Naver Clip 은 분석 API 가 없다) → **측정 불가**.
   * - 대기 `N` 건: 수집하는 업로드는 있으나 아직 집계 전이다 → **수집 대기**.
   *
   * **`?? 0` 을 하지 말 것.** 집계 행이 있는 상태의 `0` 은 실측이라, 0 으로 채우면
   * 동기화 전 영상과 실제로 0 회인 영상이 같아진다. 서버가 예전에 그 버그를 갖고 있었다.
   */
  totalViews?: number | null
  /** 총 조회수에 **포함되지 않은** 미수집 업로드 수. `0` 보다 크면 부분 합계다. */
  pendingViewUploads?: number
}

export interface VideoUpload {
  id: number
  videoId: number
  platform: Platform
  channelId?: number | null
  channelName?: string | null
  status: UploadStatus
  platformVideoId: string | null
  platformUrl: string | null
  title?: string
  description: string | null
  tags: string[]
  errorMessage: string | null
  publishedAt: string | null
  createdAt: string
  meta?: {
    title: string | null
    description: string | null
    tags: string[]
    visibility: Visibility
    customThumbnailUrl: string | null
  } | null
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
  /** Present when a compose draft saves platform-specific overrides. */
  platforms?: VideoPlatformDraftRequest[]
}

export interface VideoPlatformDraftRequest {
  platform: Platform
  /** The connected account this platform override belongs to. */
  channelId?: number
  title: string
  description?: string
  tags: string[]
  visibility: Visibility
  customThumbnailUrl?: string
}

export interface VideoPublishRequest {
  platforms: PlatformPublishConfig[]
  scheduledAt?: string
}

export interface VideoDownloadAvailability {
  available: boolean
  reason?: string | null
}

export interface VideoDeletionResult {
  videoId: number
  storageDeletionFailed: boolean
  externalFailures: Array<{
    platform: Platform
    reason: string
  }>
}

export interface PlatformPublishConfig {
  platform: Platform
  channelId?: number
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
  maxCaptionLength?: number | null
  acceptedExtensions: string[]
  acceptedMediaTypes?: MediaType[]
  unavailableReason: string | null
  configurationAvailable?: boolean
  configurationUnavailableReason?: string | null
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
  /** Internal onGo video ID; provider-only feed items do not have one. */
  videoId?: number | null
  platformVideoId: string
  platform: Platform
  channelName: string
  title: string
  description: string | null
  thumbnailUrl: string | null
  platformUrl: string | null
  /**
   * 피드 지표. **플랫폼이 주지 않거나 응답에 없으면 `null`** — `0` 이 아니다.
   *
   * Instagram 미디어 목록은 조회수·공유를 주지 않는다. 예전에는 서버가 그 자리를 `0` 으로
   * 채워 목록이 "조회수 0" 을 그리고, 조회수 정렬에서 Instagram 영상이 전부 맨 아래로
   * 밀렸다. `?? 0` 으로 되돌리지 말 것.
   */
  viewCount: number | null
  likeCount: number | null
  commentCount: number | null
  shareCount: number | null
  publishedAt: string | null
}

export interface VideoFeedResponse {
  /**
   * 채널 ID → 그 채널의 **다음 페이지 토큰**.
   *
   * 플랫폼 목록 API 는 불투명한 continuation token 으로 다음 페이지를 준다. 다음 요청에
   * `channelToken=<채널ID>:<토큰>` 으로 그대로 돌려줘야 이어서 볼 수 있다.
   * **토큰을 해석하거나 만들어내면 안 된다.** 맵이 비어 있으면 마지막 페이지다.
   */
  nextPageTokens: Record<string, string>
  items: VideoFeedItem[]
  platforms: Platform[]
  errors: string[] | null
}
