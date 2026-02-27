import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { SubtitleJob, SubtitleSegment, SubtitleGeneratorSummary } from '@/types/subtitleGenerator'

export const subtitleGeneratorApi = {
  getJobs: (status?: string) =>
    apiClient.get<ResData<SubtitleJob[]>>('/subtitle-generator', { params: { status } }).then(unwrapResponse),
  getJob: (id: number) =>
    apiClient.get<ResData<SubtitleJob>>(`/subtitle-generator/${id}`).then(unwrapResponse),
  generate: (videoId: number, language: string) =>
    apiClient.post<ResData<SubtitleJob>>('/subtitle-generator', { videoId, language }).then(unwrapResponse),
  getSegments: (jobId: number) =>
    apiClient.get<ResData<SubtitleSegment[]>>(`/subtitle-generator/${jobId}/segments`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<SubtitleGeneratorSummary>>('/subtitle-generator/summary').then(unwrapResponse),
}

export default subtitleGeneratorApi
