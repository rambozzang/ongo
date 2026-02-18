import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AdminUserListItem,
  AdminUserDetail,
  AdminVideoItem,
  AdminChannelItem,
  AdminSubscriptionDetail,
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
