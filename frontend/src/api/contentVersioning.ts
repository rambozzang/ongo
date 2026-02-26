import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ContentVersion,
  ContentVersionGroup,
  ContentVersioningSummary,
} from '@/types/contentVersioning'

export const contentVersioningApi = {
  getGroups() {
    return apiClient
      .get<ResData<ContentVersionGroup[]>>('/content-versioning')
      .then(unwrapResponse)
  },

  getVersions(contentId: number) {
    return apiClient
      .get<ResData<ContentVersion[]>>(`/content-versioning/${contentId}/versions`)
      .then(unwrapResponse)
  },

  getVersion(contentId: number, versionId: number) {
    return apiClient
      .get<ResData<ContentVersion>>(`/content-versioning/${contentId}/versions/${versionId}`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<ContentVersioningSummary>>('/content-versioning/summary')
      .then(unwrapResponse)
  },
}
