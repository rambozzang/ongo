import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Clip,
  Caption,
  Thumbnail,
  VideoSummary,
  CreateClipRequest,
  CreateClipResponse,
  GenerateCaptionRequest,
  GenerateCaptionResponse,
  UpdateCaptionRequest,
  GenerateThumbnailRequest,
  GenerateThumbnailResponse,
  ContentStudioHistory,
} from '@/types/contentStudio'

export const contentStudioApi = {
  // 영상 목록 (클립 생성용)
  getVideos() {
    return apiClient
      .get<ResData<VideoSummary[]>>('/content-studio/videos')
      .then(unwrapResponse)
  },

  // 클립 생성
  createClip(request: CreateClipRequest) {
    return apiClient
      .post<ResData<CreateClipResponse>>('/content-studio/clips', request)
      .then(unwrapResponse)
  },

  // 클립 목록
  getClips(videoId?: number) {
    return apiClient
      .get<ResData<Clip[]>>('/content-studio/clips', { params: { videoId } })
      .then(unwrapResponse)
  },

  // 클립 삭제
  deleteClip(clipId: number) {
    return apiClient
      .delete<ResData<void>>(`/content-studio/clips/${clipId}`)
      .then(unwrapResponse)
  },

  // 자막 생성
  generateCaption(request: GenerateCaptionRequest) {
    return apiClient
      .post<ResData<GenerateCaptionResponse>>('/content-studio/captions', request)
      .then(unwrapResponse)
  },

  // 자막 목록
  getCaptions(videoId?: number) {
    return apiClient
      .get<ResData<Caption[]>>('/content-studio/captions', { params: { videoId } })
      .then(unwrapResponse)
  },

  // 자막 수정
  updateCaption(captionId: number, request: UpdateCaptionRequest) {
    return apiClient
      .put<ResData<Caption>>(`/content-studio/captions/${captionId}`, request)
      .then(unwrapResponse)
  },

  // 자막 삭제
  deleteCaption(captionId: number) {
    return apiClient
      .delete<ResData<void>>(`/content-studio/captions/${captionId}`)
      .then(unwrapResponse)
  },

  // 썸네일 생성
  generateThumbnail(request: GenerateThumbnailRequest) {
    return apiClient
      .post<ResData<GenerateThumbnailResponse>>('/content-studio/thumbnails', request)
      .then(unwrapResponse)
  },

  // 썸네일 목록
  getThumbnails(videoId?: number) {
    return apiClient
      .get<ResData<Thumbnail[]>>('/content-studio/thumbnails', { params: { videoId } })
      .then(unwrapResponse)
  },

  // 썸네일 삭제
  deleteThumbnail(thumbnailId: number) {
    return apiClient
      .delete<ResData<void>>(`/content-studio/thumbnails/${thumbnailId}`)
      .then(unwrapResponse)
  },

  // 히스토리
  getHistory() {
    return apiClient
      .get<ResData<ContentStudioHistory[]>>('/content-studio/history')
      .then(unwrapResponse)
  },
}
