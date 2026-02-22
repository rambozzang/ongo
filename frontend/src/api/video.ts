import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type {
  Video,
  VideoCreateRequest,
  VideoPublishRequest,
  VideoListFilter,
  VideoMediaInfo,
  VideoSubtitle,
  ThumbnailResult,
  ContentImage,
  OptimizationCheckRequest,
  OptimizationCheckResponse,
  VideoResize,
  VideoTranslation,
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

  optimizationCheck(request: OptimizationCheckRequest) {
    return apiClient
      .post<ResData<OptimizationCheckResponse>>('/videos/optimization-check', request)
      .then(unwrapResponse)
  },

  // Media Info
  getMediaInfo(videoId: number) {
    return apiClient
      .get<ResData<VideoMediaInfo>>(`/videos/${videoId}/media-info`)
      .then(unwrapResponse)
  },

  // Thumbnails
  getThumbnails(videoId: number) {
    return apiClient
      .get<ResData<ThumbnailResult>>(`/videos/${videoId}/thumbnails`)
      .then(unwrapResponse)
  },

  selectThumbnail(videoId: number, index: number) {
    return apiClient
      .post<ResData<void>>(`/videos/${videoId}/thumbnails/select`, { index })
      .then(unwrapResponse)
  },

  uploadCustomThumbnail(videoId: number, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient
      .post<ResData<{ url: string }>>(`/videos/${videoId}/thumbnails/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then(unwrapResponse)
  },

  // Captions
  getCaptions(videoId: number) {
    return apiClient
      .get<ResData<VideoSubtitle[]>>(`/videos/${videoId}/captions`)
      .then(unwrapResponse)
  },

  generateCaption(videoId: number, language: string = 'ko') {
    return apiClient
      .post<ResData<void>>(`/videos/${videoId}/captions/generate`, { language })
      .then(unwrapResponse)
  },

  updateCaption(videoId: number, language: string, content: string) {
    return apiClient
      .put<ResData<VideoSubtitle>>(`/videos/${videoId}/captions`, { language, content })
      .then(unwrapResponse)
  },

  // Content Images
  uploadImages(videoId: number, files: File[]) {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    return apiClient
      .post<ResData<ContentImage[]>>(`/videos/${videoId}/images`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000,
      })
      .then(unwrapResponse)
  },

  getImages(videoId: number) {
    return apiClient
      .get<ResData<ContentImage[]>>(`/videos/${videoId}/images`)
      .then(unwrapResponse)
  },

  reorderImages(videoId: number, imageIds: number[]) {
    return apiClient
      .put<ResData<void>>(`/videos/${videoId}/images/reorder`, { imageIds })
      .then(unwrapResponse)
  },

  // Resize
  requestResize(videoId: number, aspectRatios: string[]) {
    return apiClient
      .post<ResData<VideoResize[]>>(`/videos/${videoId}/resize`, { aspectRatios })
      .then(unwrapResponse)
  },

  getResizes(videoId: number) {
    return apiClient
      .get<ResData<VideoResize[]>>(`/videos/${videoId}/resizes`)
      .then(unwrapResponse)
  },

  // Translations
  getTranslations(videoId: number) {
    return apiClient
      .get<ResData<VideoTranslation[]>>(`/videos/${videoId}/translations`)
      .then(unwrapResponse)
  },

  requestTranslation(videoId: number, languages: string[]) {
    return apiClient
      .post<ResData<VideoTranslation[]>>(`/videos/${videoId}/translations`, { languages })
      .then(unwrapResponse)
  },

  updateTranslation(videoId: number, translationId: number, data: { title?: string; description?: string }) {
    return apiClient
      .put<ResData<VideoTranslation>>(`/videos/${videoId}/translations/${translationId}`, data)
      .then(unwrapResponse)
  },

  deleteTranslation(videoId: number, translationId: number) {
    return apiClient
      .delete<ResData<void>>(`/videos/${videoId}/translations/${translationId}`)
      .then(unwrapResponse)
  },
}
