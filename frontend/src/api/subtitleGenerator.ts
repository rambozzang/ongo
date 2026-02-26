import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { SubtitleJob, SubtitleSegment, SubtitleGeneratorSummary } from '@/types/subtitleGenerator'

export const subtitleGeneratorApi = {
  getJobs: (status?: string) =>
    apiClient.get<ResData<SubtitleJob[]>>('/api/v1/subtitle-generator', { params: { status } }).then(unwrapResponse),
  getJob: (id: number) =>
    apiClient.get<ResData<SubtitleJob>>(`/api/v1/subtitle-generator/${id}`).then(unwrapResponse),
  generate: (videoId: number, language: string) =>
    apiClient.post<ResData<SubtitleJob>>('/api/v1/subtitle-generator', { videoId, language }).then(unwrapResponse),
  getSegments: (jobId: number) =>
    apiClient.get<ResData<SubtitleSegment[]>>(`/api/v1/subtitle-generator/${jobId}/segments`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<SubtitleGeneratorSummary>>('/api/v1/subtitle-generator/summary').then(unwrapResponse),
}

export default subtitleGeneratorApi
