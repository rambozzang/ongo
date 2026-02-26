import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  PlatformHealthScore,
  HealthIssue,
  PlatformHealthSummary,
} from '@/types/platformHealth'

export const platformHealthApi = {
  getScores() {
    return apiClient
      .get<ResData<PlatformHealthScore[]>>('/platform-health')
      .then(unwrapResponse)
  },

  getScore(platform: string) {
    return apiClient
      .get<ResData<PlatformHealthScore>>(`/platform-health/${platform}`)
      .then(unwrapResponse)
  },

  getIssues(healthScoreId: number) {
    return apiClient
      .get<ResData<HealthIssue[]>>(`/platform-health/${healthScoreId}/issues`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<PlatformHealthSummary>>('/platform-health/summary')
      .then(unwrapResponse)
  },
}
