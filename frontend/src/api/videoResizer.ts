import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ResizeJob,
  VideoForResize,
  CreateResizeRequest,
  CreateResizeResponse,
} from '@/types/videoResizer'

export const videoResizerApi = {
  getVideos() {
    return apiClient
      .get<ResData<VideoForResize[]>>('/video-resizer/videos')
      .then(unwrapResponse)
  },

  getJobs(videoId?: number) {
    return apiClient
      .get<ResData<ResizeJob[]>>('/video-resizer/jobs', { params: { videoId } })
      .then(unwrapResponse)
  },

  createResize(request: CreateResizeRequest) {
    return apiClient
      .post<ResData<CreateResizeResponse>>('/video-resizer/resize', request)
      .then(unwrapResponse)
  },

  getJob(jobId: number) {
    return apiClient
      .get<ResData<ResizeJob>>(`/video-resizer/jobs/${jobId}`)
      .then(unwrapResponse)
  },

  cancelJob(jobId: number) {
    return apiClient
      .post<ResData<void>>(`/video-resizer/jobs/${jobId}/cancel`)
      .then(unwrapResponse)
  },

  deleteJob(jobId: number) {
    return apiClient
      .delete<ResData<void>>(`/video-resizer/jobs/${jobId}`)
      .then(unwrapResponse)
  },

  downloadOutput(jobId: number) {
    return apiClient
      .get<Blob>(`/video-resizer/jobs/${jobId}/download`, { responseType: 'blob' })
  },
}
