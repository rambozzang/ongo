import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  SubtitleTrack,
  VideoForSubtitle,
  GenerateSubtitleRequest,
  GenerateSubtitleResponse,
  ExportSubtitleRequest,
  UpdateCueRequest,
} from '@/types/subtitleEditor'

export const subtitleEditorApi = {
  getVideos() {
    return apiClient
      .get<ResData<VideoForSubtitle[]>>('/subtitle-editor/videos')
      .then(unwrapResponse)
  },

  getTracks(videoId?: number) {
    return apiClient
      .get<ResData<SubtitleTrack[]>>('/subtitle-editor/tracks', { params: { videoId } })
      .then(unwrapResponse)
  },

  getTrack(trackId: number) {
    return apiClient
      .get<ResData<SubtitleTrack>>(`/subtitle-editor/tracks/${trackId}`)
      .then(unwrapResponse)
  },

  generateSubtitles(request: GenerateSubtitleRequest) {
    return apiClient
      .post<ResData<GenerateSubtitleResponse>>('/subtitle-editor/generate', request)
      .then(unwrapResponse)
  },

  updateCue(request: UpdateCueRequest) {
    return apiClient
      .put<ResData<SubtitleTrack>>(`/subtitle-editor/tracks/${request.trackId}/cues/${request.cueId}`, request)
      .then(unwrapResponse)
  },

  deleteCue(trackId: number, cueId: string) {
    return apiClient
      .delete<ResData<void>>(`/subtitle-editor/tracks/${trackId}/cues/${cueId}`)
      .then(unwrapResponse)
  },

  exportSubtitles(request: ExportSubtitleRequest) {
    return apiClient
      .post<Blob>(`/subtitle-editor/tracks/${request.trackId}/export`, request, { responseType: 'blob' })
  },

  deleteTrack(trackId: number) {
    return apiClient
      .delete<ResData<void>>(`/subtitle-editor/tracks/${trackId}`)
      .then(unwrapResponse)
  },
}
