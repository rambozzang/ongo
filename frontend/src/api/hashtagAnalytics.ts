import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  HashtagPerformance,
  HashtagTrend,
  HashtagRecommendation,
  HashtagGroup,
  HashtagAnalyticsSummary,
  AnalyzeHashtagsRequest,
} from '@/types/hashtagAnalytics'

export const hashtagAnalyticsApi = {
  getPerformances(platform?: string) {
    return apiClient
      .get<ResData<HashtagPerformance[]>>('/hashtag-analytics', { params: { platform } })
      .then(unwrapResponse)
  },

  getTrends(hashtag: string, days = 30) {
    return apiClient
      .get<ResData<HashtagTrend[]>>(`/hashtag-analytics/trends`, { params: { hashtag, days } })
      .then(unwrapResponse)
  },

  getRecommendations(topic: string, platform: string) {
    return apiClient
      .get<ResData<HashtagRecommendation[]>>('/hashtag-analytics/recommendations', {
        params: { topic, platform },
      })
      .then(unwrapResponse)
  },

  analyzeHashtags(request: AnalyzeHashtagsRequest) {
    return apiClient
      .post<ResData<HashtagRecommendation[]>>('/hashtag-analytics/analyze', request)
      .then(unwrapResponse)
  },

  getGroups() {
    return apiClient
      .get<ResData<HashtagGroup[]>>('/hashtag-analytics/groups')
      .then(unwrapResponse)
  },

  createGroup(name: string, hashtags: string[], platform: string) {
    return apiClient
      .post<ResData<HashtagGroup>>('/hashtag-analytics/groups', { name, hashtags, platform })
      .then(unwrapResponse)
  },

  deleteGroup(id: number) {
    return apiClient
      .delete<ResData<void>>(`/hashtag-analytics/groups/${id}`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<HashtagAnalyticsSummary>>('/hashtag-analytics/summary')
      .then(unwrapResponse)
  },
}
