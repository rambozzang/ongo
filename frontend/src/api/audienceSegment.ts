import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AudienceSegment,
  SegmentInsight,
  SegmentComparison,
  CreateSegmentRequest,
} from '@/types/audienceSegment'

export const audienceSegmentApi = {
  getAll: () =>
    apiClient.get<ResData<AudienceSegment[]>>('/audience-segments').then(unwrapResponse),

  getById: (id: number) =>
    apiClient.get<ResData<AudienceSegment>>(`/audience-segments/${id}`).then(unwrapResponse),

  create: (request: CreateSegmentRequest) =>
    apiClient.post<ResData<AudienceSegment>>('/audience-segments', request).then(unwrapResponse),

  delete: (id: number) =>
    apiClient.delete<ResData<void>>(`/audience-segments/${id}`).then(unwrapResponse),

  getInsight: (segmentId: number) =>
    apiClient.get<ResData<SegmentInsight>>(`/audience-segments/${segmentId}/insight`).then(unwrapResponse),

  compare: (segmentIds: number[]) =>
    apiClient.post<ResData<SegmentComparison>>('/audience-segments/compare', { segmentIds }).then(unwrapResponse),
}
