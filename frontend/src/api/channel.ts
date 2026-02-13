import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Channel, ChannelConnectRequest } from '@/types/channel'
import type { Platform } from '@/types/channel'

export const channelApi = {
  list() {
    return apiClient
      .get<ResData<{ channels: Channel[]; maxAllowed: number; currentCount: number }>>('/channels')
      .then(unwrapResponse)
      .then((data) => data.channels)
  },

  connect(platform: Platform, request: ChannelConnectRequest) {
    return apiClient
      .post<ResData<Channel>>(`/channels/connect/${platform.toLowerCase()}`, request)
      .then(unwrapResponse)
  },

  disconnect(id: number) {
    return apiClient.delete<ResData<void>>(`/channels/${id}`).then(unwrapResponse)
  },

  sync(id: number) {
    return apiClient.post<ResData<Channel>>(`/channels/${id}/sync`).then(unwrapResponse)
  },
}
