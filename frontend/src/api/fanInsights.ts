import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  FanDemographic,
  FanBehavior,
  FanSegment,
  FanInsightsSummary,
} from '@/types/fanInsights'

export const fanInsightsApi = {
  getDemographics(platform?: string) {
    return apiClient
      .get<ResData<FanDemographic[]>>('/fan-insights/demographics', { params: { platform } })
      .then(unwrapResponse)
  },

  getBehaviors(platform?: string) {
    return apiClient
      .get<ResData<FanBehavior[]>>('/fan-insights/behaviors', { params: { platform } })
      .then(unwrapResponse)
  },

  getSegments() {
    return apiClient
      .get<ResData<FanSegment[]>>('/fan-insights/segments')
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<FanInsightsSummary>>('/fan-insights/summary')
      .then(unwrapResponse)
  },
}
