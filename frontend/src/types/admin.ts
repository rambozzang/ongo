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
  subscriberCount: number
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
  storageQuotaOverride: number | null
  cancelledAt: string | null
  createdAt: string | null
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
