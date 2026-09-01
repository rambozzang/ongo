import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AdminUserListItem,
  AdminUserDetail,
  AdminVideoItem,
  AdminChannelItem,
  AdminSubscriptionDetail,
  AdminPublishQueueSummary,
  AdminAccountDeletionJob,
  AdminDeadLetterWebhook,
  AdminDeadLetterRequeueResult,
  StorageQuotaUpdateRequest,
  UpdateRoleRequest,
  PageResponse,
} from '@/types/admin'

export const adminApi = {
  getUsers(query?: string, page = 0, size = 20) {
    return apiClient
      .get<ResData<PageResponse<AdminUserListItem>>>('/admin/users', {
        params: { query, page, size },
      })
      .then(unwrapResponse)
  },

  getUserDetail(userId: number) {
    return apiClient
      .get<ResData<AdminUserDetail>>(`/admin/users/${userId}`)
      .then(unwrapResponse)
  },

  updateStorageQuota(userId: number, request: StorageQuotaUpdateRequest) {
    return apiClient
      .put<ResData<void>>(`/admin/users/${userId}/storage-quota`, request)
      .then(unwrapResponse)
  },

  getUserVideos(userId: number, page = 0, size = 20) {
    return apiClient
      .get<ResData<PageResponse<AdminVideoItem>>>(`/admin/users/${userId}/videos`, {
        params: { page, size },
      })
      .then(unwrapResponse)
  },

  getUserChannels(userId: number) {
    return apiClient
      .get<ResData<AdminChannelItem[]>>(`/admin/users/${userId}/channels`)
      .then(unwrapResponse)
  },

  getUserSubscription(userId: number) {
    return apiClient
      .get<ResData<AdminSubscriptionDetail>>(`/admin/users/${userId}/subscription`)
      .then(unwrapResponse)
  },

  getPublishQueue() {
    return apiClient
      .get<ResData<AdminPublishQueueSummary>>('/admin/publish-queue')
      .then(unwrapResponse)
  },

  getAccountDeletionJobs(limit = 100) {
    return apiClient
      .get<ResData<AdminAccountDeletionJob[]>>('/admin/account-deletion/jobs', { params: { limit } })
      .then(unwrapResponse)
  },

  retryAccountDeletion(jobId: number) {
    return apiClient
      .post<ResData<AdminAccountDeletionJob>>(`/admin/account-deletion/jobs/${jobId}/retry`)
      .then(unwrapResponse)
  },

  /**
   * 재시도를 모두 소진한 결제 웹훅을 조회한다.
   *
   * 서버가 원문 본문·서명을 빼고 멱등 키를 마스킹해 내려준다. 프론트에서 복원할 수 있는
   * 값이 아니며 복원하려 해서도 안 된다.
   */
  getDeadLetterWebhooks(limit = 50) {
    return apiClient
      .get<ResData<AdminDeadLetterWebhook[]>>('/admin/webhooks/dead-letters', { params: { limit } })
      .then(unwrapResponse)
  },

  /**
   * 한 건을 재시도 대기열로 되돌린다. **운영자의 명시적 조치다.**
   *
   * 본문을 보내지 않는다. 상태를 보낼 수 있게 하면 임의 상태 전이가 가능해진다.
   * 허용되는 전이는 서버가 정한 `DEAD_LETTER → FAILED` 하나뿐이다.
   */
  requeueDeadLetterWebhook(id: number) {
    return apiClient
      .post<ResData<AdminDeadLetterRequeueResult>>(`/admin/webhooks/dead-letters/${id}/requeue`)
      .then(unwrapResponse)
  },

  updateUserRole(userId: number, request: UpdateRoleRequest) {
    return apiClient
      .put<ResData<void>>(`/admin/users/${userId}/role`, request)
      .then(unwrapResponse)
  },

  deactivateUser(userId: number) {
    return apiClient
      .post<ResData<void>>(`/admin/users/${userId}/deactivate`)
      .then(unwrapResponse)
  },

  activateUser(userId: number) {
    return apiClient
      .post<ResData<void>>(`/admin/users/${userId}/activate`)
      .then(unwrapResponse)
  },
}
