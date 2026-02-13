import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface NotificationResponse {
  id: number
  type: string
  title: string
  message: string
  isRead: boolean
  referenceType: string | null
  referenceId: number | null
  createdAt: string | null
}

export interface NotificationListResponse {
  notifications: NotificationResponse[]
  page: number
  size: number
}

export interface UnreadCountResponse {
  count: number
}

export const notificationApi = {
  list(params?: { page?: number; size?: number }) {
    return apiClient.get<ResData<NotificationListResponse>>('/notifications', { params }).then(unwrapResponse)
  },

  markAsRead(id: number) {
    return apiClient.put<ResData<void>>(`/notifications/${id}/read`).then(unwrapResponse)
  },

  markAllAsRead() {
    return apiClient.put<ResData<void>>('/notifications/read-all').then(unwrapResponse)
  },

  getUnreadCount() {
    return apiClient.get<ResData<UnreadCountResponse>>('/notifications/unread-count').then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/notifications/${id}`).then(unwrapResponse)
  },
}
