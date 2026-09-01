import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type {
  Video,
  VideoCreateRequest,
  VideoPublishRequest,
  VideoListFilter,
  ContentImage,
  OptimizationCheckRequest,
  OptimizationCheckResponse,
  VideoTranslation,
  VideoFeedResponse,
  PlatformUploadCapability,
  VideoDownloadAvailability,
  VideoDeletionResult,
} from '@/types/video'

export const videoApi = {
  getImportAvailability() {
    return apiClient
      .get<ResData<VideoDownloadAvailability>>('/videos/import-url/availability')
      .then(unwrapResponse)
  },

  importUrl(request: { url: string; title?: string }) {
    return apiClient
      .post<ResData<{ videoId: number; title: string; provider: string; fileUrl?: string | null }>>(
        '/videos/import-url',
        request,
        { timeout: 1_500_000 },
      )
      .then(unwrapResponse)
  },

  getUploadCapabilities() {
    return apiClient
      .get<ResData<PlatformUploadCapability[]>>('/videos/stream-publish/capabilities')
      .then(unwrapResponse)
  },

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

  generate(request: {
    type: 'image-text-slides'
    output: 'vertical' | 'horizontal'
    customParams: { prompt: string; title?: string; tags?: string[] }
  }) {
    return apiClient
      .post<ResData<Array<{ id: string; path: string }>>>('/videos/generate', request, { timeout: 240000 })
      .then(unwrapResponse)
  },

  update(id: number, request: Partial<VideoCreateRequest>) {
    return apiClient.put<ResData<Video>>(`/videos/${id}`, request).then(unwrapResponse)
  },

  publish(id: number, request: VideoPublishRequest) {
    return apiClient.post<ResData<Video>>(`/videos/${id}/publish`, request).then(unwrapResponse)
  },

  retry(id: number, platform: string) {
    return apiClient.post<ResData<void>>(`/videos/${id}/retry/${platform}`).then(unwrapResponse)
  },

  retryUpload(id: number, uploadId: number) {
    return apiClient.post<ResData<void>>(`/videos/${id}/uploads/${uploadId}/retry`).then(unwrapResponse)
  },

  recheck(id: number, platform: string) {
    return apiClient.post<ResData<void>>(`/videos/${id}/recheck/${platform}`).then(unwrapResponse)
  },

  recheckUpload(id: number, uploadId: number) {
    return apiClient.post<ResData<void>>(`/videos/${id}/uploads/${uploadId}/recheck`).then(unwrapResponse)
  },

  recycle(id: number, request: {
    title: string
    description?: string
    tags: string[]
    category?: string
    platforms: Array<{
      platform: string
      channelId?: number
      title?: string
      description?: string
      tags?: string[]
      scheduledAt?: string
    }>
  }) {
    return apiClient.post<ResData<{ videoId: number; uploads: Array<{ platform: string; status: string; errorMessage?: string }> }>>(
      `/videos/${id}/recycle`,
      request,
    ).then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<VideoDeletionResult>>(`/videos/${id}`).then(unwrapResponse)
  },

  confirmUpload(videoId: number) {
    return apiClient.post<ResData<void>>(`/videos/${videoId}/upload/complete`).then(unwrapResponse)
  },

  optimizationCheck(request: OptimizationCheckRequest) {
    return apiClient
      .post<ResData<OptimizationCheckResponse>>('/videos/optimization-check', request)
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

  /**
   * 라이브러리의 영상 에셋으로 **편집 가능한 영상 초안**을 만든다.
   *
   * 서버가 오브젝트를 새 영상 전용 경로로 복사하므로 원본 에셋은 그대로 남는다 —
   * 나중에 에셋을 정리해도 이 초안은 깨지지 않는다.
   */
  createFromAsset(assetId: number) {
    return apiClient
      .post<ResData<{ videoId: number }>>(`/videos/from-asset/${assetId}`)
      .then(unwrapResponse)
  },

  /**
   * 플랫폼 피드.
   *
   * **숫자 페이지 이동은 서버가 지원하지 않는다.** 각 플랫폼 커서가 독립적이라 "N 번째
   * 페이지" 를 만들 수 없다. 이어보려면 이전 응답의 `nextPageTokens` 를
   * `channelToken=<채널ID>:<토큰>` 형태로 돌려준다.
   */
  feed(params: { platform?: string; size?: number; sort?: string; channelToken?: string[] }) {
    return apiClient
      .get<ResData<VideoFeedResponse>>('/videos/feed', { params })
      .then(unwrapResponse)
  },
}
