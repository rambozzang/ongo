import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  HashtagAnalysisRequest,
  HashtagAnalysisResponse,
  HashtagSet,
} from '@/types/hashtagStrategy'

export const hashtagStrategyApi = {
  analyze(request: HashtagAnalysisRequest) {
    return apiClient
      .post<ResData<HashtagAnalysisResponse>>('/ai/hashtag-strategy/analyze', request)
      .then(unwrapResponse)
  },

  getSavedSets() {
    return apiClient
      .get<ResData<{ sets: HashtagSet[] }>>('/ai/hashtag-strategy/sets')
      .then(unwrapResponse)
      .then((res) => res.sets)
  },

  saveSet(set: HashtagSet) {
    return apiClient
      .post<ResData<HashtagSet>>('/ai/hashtag-strategy/sets', set)
      .then(unwrapResponse)
  },

  deleteSet(id: number) {
    return apiClient
      .delete<ResData<void>>(`/ai/hashtag-strategy/sets/${id}`)
      .then(unwrapResponse)
  },
}
