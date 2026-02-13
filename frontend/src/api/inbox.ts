import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface InboxMessageResponse {
  id: number
  platform: string | null
  senderName: string
  senderAvatarUrl: string | null
  messageType: string
  content: string
  isRead: boolean
  isStarred: boolean
  videoId: number | null
  receivedAt: string | null
  createdAt: string | null
}

export interface InboxListResponse {
  messages: InboxMessageResponse[]
  totalElements: number
  page: number
  size: number
}

export interface UnreadCountResponse {
  count: number
}

export const inboxApi = {
  listMessages(params?: { page?: number; size?: number; platform?: string; isRead?: boolean; type?: string }) {
    return apiClient.get<ResData<InboxListResponse>>('/inbox', { params }).then(unwrapResponse)
  },

  markAsRead(id: number) {
    return apiClient.put<ResData<void>>(`/inbox/${id}/read`).then(unwrapResponse)
  },

  markAllAsRead() {
    return apiClient.put<ResData<void>>('/inbox/read-all').then(unwrapResponse)
  },

  toggleStar(id: number) {
    return apiClient.put<ResData<InboxMessageResponse>>(`/inbox/${id}/star`).then(unwrapResponse)
  },

  getUnreadCount() {
    return apiClient.get<ResData<UnreadCountResponse>>('/inbox/unread-count').then(unwrapResponse)
  },
}
