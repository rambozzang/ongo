import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Creator, CollabRequest, NetworkSummary, NetworkFilter } from '@/types/creatorNetwork'

export const creatorNetworkApi = {
  getSummary: () =>
    apiClient.get<ResData<NetworkSummary>>('/creator-network/summary').then(unwrapResponse),

  getCreators: (filter?: NetworkFilter) =>
    apiClient.get<ResData<Creator[]>>('/creator-network/creators', { params: filter }).then(unwrapResponse),

  getCreator: (id: number) =>
    apiClient.get<ResData<Creator>>(`/creator-network/creators/${id}`).then(unwrapResponse),

  connect: (creatorId: number) =>
    apiClient.post<ResData<void>>(`/creator-network/creators/${creatorId}/connect`).then(unwrapResponse),

  disconnect: (creatorId: number) =>
    apiClient.delete<ResData<void>>(`/creator-network/creators/${creatorId}/connect`).then(unwrapResponse),

  getCollabRequests: () =>
    apiClient.get<ResData<CollabRequest[]>>('/creator-network/collabs').then(unwrapResponse),

  sendCollabRequest: (request: { receiverId: number; message: string; collabType: string }) =>
    apiClient.post<ResData<CollabRequest>>('/creator-network/collabs', request).then(unwrapResponse),

  respondCollabRequest: (id: number, accept: boolean) =>
    apiClient.post<ResData<CollabRequest>>(`/creator-network/collabs/${id}/respond`, { accept }).then(unwrapResponse),
}
