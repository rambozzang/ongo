import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  AudienceOverlapResult,
  OverlapSegment,
  AudienceOverlapSummary,
} from '@/types/audienceOverlap'

export const audienceOverlapApi = {
  getResults() {
    return apiClient
      .get<ResData<AudienceOverlapResult[]>>('/audience-overlap')
      .then(unwrapResponse)
  },

  getSegments() {
    return apiClient
      .get<ResData<OverlapSegment[]>>('/audience-overlap/segments')
      .then(unwrapResponse)
  },

  analyze(platformA: string, platformB: string) {
    return apiClient
      .post<ResData<AudienceOverlapResult>>('/audience-overlap/analyze', { platformA, platformB })
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<AudienceOverlapSummary>>('/audience-overlap/summary')
      .then(unwrapResponse)
  },
}
