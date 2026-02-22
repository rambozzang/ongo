import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { AudienceProfile, AudienceSegment } from '@/types/audience'

export const audienceApi = {
  getProfiles(sortBy = 'engagement_score', page = 0, size = 20) {
    return apiClient
      .get<ResData<{ profiles: AudienceProfile[]; total: number }>>('/audience/profiles', { params: { sortBy, page, size } })
      .then(unwrapResponse)
  },

  getProfile(profileId: number) {
    return apiClient
      .get<ResData<AudienceProfile>>(`/audience/profiles/${profileId}`)
      .then(unwrapResponse)
  },

  updateTags(profileId: number, tags: string[]) {
    return apiClient
      .put<ResData<AudienceProfile>>(`/audience/profiles/${profileId}/tags`, { tags })
      .then(unwrapResponse)
  },

  getSegments() {
    return apiClient
      .get<ResData<AudienceSegment[]>>('/audience/segments')
      .then(unwrapResponse)
  },

  createSegment(data: { name: string; description?: string; conditions?: string; autoUpdate?: boolean }) {
    return apiClient
      .post<ResData<AudienceSegment>>('/audience/segments', data)
      .then(unwrapResponse)
  },

  updateSegment(segmentId: number, data: { name?: string; description?: string; conditions?: string }) {
    return apiClient
      .put<ResData<AudienceSegment>>(`/audience/segments/${segmentId}`, data)
      .then(unwrapResponse)
  },

  deleteSegment(segmentId: number) {
    return apiClient
      .delete<ResData<void>>(`/audience/segments/${segmentId}`)
      .then(unwrapResponse)
  },
}
