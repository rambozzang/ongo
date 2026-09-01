export interface AdminUserListItem {
  id: number
  name: string
  email: string
  role: string
  planType: string
  storageUsedBytes: number
  storageLimitBytes: number
  createdAt: string | null
}

export interface AdminUserDetail {
  id: number
  name: string
  email: string
  role: string
  planType: string
  storageUsedBytes: number
  storageLimitBytes: number
  storageQuotaOverride: number | null
  videoCount: number
  createdAt: string | null
}

export interface StorageQuotaUpdateRequest {
  limitBytes: number | null
}

export interface AdminVideoItem {
  id: number
  title: string
  status: string
  mediaType: string
  fileSizeBytes: number | null
  platforms: AdminPlatformUploadItem[]
  createdAt: string | null
}

export interface AdminPlatformUploadItem {
  platform: string
  status: string
  platformUrl: string | null
  errorMessage: string | null
}

export interface AdminChannelItem {
  id: number
  platform: string
  channelName: string
  channelUrl: string | null
  /** 구독자 수. **조회하지 않는 플랫폼이면 `null`** — `Channel.subscriberCount` 와 같은 계약. */
  subscriberCount: number | null
  status: string
  tokenExpiresAt: string | null
  connectedAt: string | null
}

export interface AdminSubscriptionDetail {
  planType: string
  status: string
  price: number
  billingCycle: string
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  nextBillingDate: string | null
  pendingPlanType: string | null
  pendingBillingCycle: string | null
  storageQuotaOverride: number | null
  cancelledAt: string | null
  createdAt: string | null
}

export interface AdminPublishQueueItem {
  uploadId: number
  videoId: number
  platform: string
  status: string
  attemptCount: number
  nextRetryAt: string | null
  leaseUntil: string | null
  lastError: string | null
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminPublishQueueSummary {
  capturedAt: string
  totalPending: number
  statusCounts: Record<string, number>
  activeLeases: number
  dueRetries: number
  unconfirmed: number
  items: AdminPublishQueueItem[]
}

export interface AdminAccountDeletionJob {
  jobId: number
  userId: number
  status: string
  attemptCount: number
  requestedAt: string | null
  updatedAt: string | null
  completedAt: string | null
  lastErrorCode: string | null
  supportReference: string | null
}

/**
 * 재시도를 모두 소진한 결제 웹훅.
 *
 * 서버가 원문 본문·서명을 내려주지 않고 멱등 키도 마스킹한다. 여기에 `payload` 같은 필드를
 * 추가하면 결제 식별자·고객 정보가 화면과 스크린샷으로 퍼진다.
 */
export interface AdminDeadLetterWebhook {
  /** 재큐잉 대상 지정용 대리 키. 멱등 키를 노출하지 않기 위해 이것을 쓴다. */
  id: number
  provider: string
  eventType: string
  maskedEventId: string
  retryCount: number
  maxRetries: number
  nextRetryAt: string | null
  errorMessage: string | null
  createdAt: string | null
  processedAt: string | null
}

export interface AdminDeadLetterRequeueResult {
  id: number
  status: string
  nextRetryAt: string
}

export interface UpdateRoleRequest {
  role: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}
