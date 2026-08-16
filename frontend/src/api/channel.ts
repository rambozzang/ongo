import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Channel, ChannelConnectRequest } from '@/types/channel'
import type { Platform } from '@/types/channel'

export const channelApi = {
  list() {
    return apiClient
      .get<ResData<{ channels: Channel[]; maxAllowed: number; currentCount: number }>>('/channels')
      .then(unwrapResponse)
  },

  connect(platform: Platform, request: ChannelConnectRequest) {
    return apiClient
      .post<ResData<{ channel: Channel }>>(`/channels/connect/${platform.toLowerCase()}`, request)
      .then(unwrapResponse)
  },

  authorizationUrl(
    platform: Platform,
    params: { redirectUri: string; state: string; codeChallenge?: string },
  ) {
    return apiClient
      .get<ResData<{ authorizationUrl: string }>>(
        `/channels/oauth/${platform.toLowerCase()}/authorization-url`,
        { params },
      )
      .then(unwrapResponse)
  },

  disconnect(id: number) {
    return apiClient.delete<ResData<void>>(`/channels/${id}`).then(unwrapResponse)
  },

  sync(id: number) {
    return apiClient.post<ResData<Channel>>(`/channels/${id}/sync`).then(unwrapResponse)
  },
}
