import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { LiveStream, StreamChat, LiveStreamSummary } from '@/types/liveStream'

export const liveStreamApi = {
  getStreams: (status?: string) =>
    apiClient.get<ResData<LiveStream[]>>('/api/v1/live-streams', { params: { status } }).then(unwrapResponse),
  getStream: (id: number) =>
    apiClient.get<ResData<LiveStream>>(`/api/v1/live-streams/${id}`).then(unwrapResponse),
  create: (data: { title: string; platform: string; scheduledAt: string }) =>
    apiClient.post<ResData<LiveStream>>('/api/v1/live-streams', data).then(unwrapResponse),
  endStream: (id: number) =>
    apiClient.put<ResData<LiveStream>>(`/api/v1/live-streams/${id}/end`).then(unwrapResponse),
  getChats: (streamId: number) =>
    apiClient.get<ResData<StreamChat[]>>(`/api/v1/live-streams/${streamId}/chats`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<LiveStreamSummary>>('/api/v1/live-streams/summary').then(unwrapResponse),
}

export default liveStreamApi
