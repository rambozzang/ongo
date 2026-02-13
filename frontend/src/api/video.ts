import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type {
  Video,
  VideoCreateRequest,
  VideoPublishRequest,
  VideoListFilter,
  OptimizationCheckRequest,
  OptimizationCheckResponse,
} from '@/types/video'

export const videoApi = {
  list(filter: VideoListFilter & PageRequest) {
    return apiClient
      .get<ResData<PageResponse<Video>>>('/videos', { params: filter })
      .then(unwrapResponse)
  },

  get(id: number) {
    return apiClient.get<ResData<Video>>(`/videos/${id}`).then(unwrapResponse)
  },

  create(request: VideoCreateRequest) {
    return apiClient.post<ResData<Video>>('/videos', request).then(unwrapResponse)
  },

  update(id: number, request: Partial<VideoCreateRequest>) {
    return apiClient.put<ResData<Video>>(`/videos/${id}`, request).then(unwrapResponse)
  },

  publish(id: number, request: VideoPublishRequest) {
    return apiClient.post<ResData<Video>>(`/videos/${id}/publish`, request).then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/videos/${id}`).then(unwrapResponse)
  },

  retryTranscode(videoId: number, platform: string) {
    return apiClient
      .post<ResData<void>>(`/videos/${videoId}/transcode/retry/${platform}`)
      .then(unwrapResponse)
  },

  optimizationCheck(request: OptimizationCheckRequest) {
    return apiClient
      .post<ResData<OptimizationCheckResponse>>('/videos/optimization-check', request)
      .then(unwrapResponse)
  },
}
