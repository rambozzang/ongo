import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { MatchFilter, MatchResponse, CollabRequest } from '@/types/influencerMatch'

export const influencerMatchApi = {
  findMatches(filter?: MatchFilter) {
    return apiClient
      .post<ResData<MatchResponse>>('/influencer-match/find', filter ?? {})
      .then(unwrapResponse)
  },

  getCollabRequests() {
    return apiClient
      .get<ResData<{ requests: CollabRequest[] }>>('/influencer-match/collabs')
      .then(unwrapResponse)
      .then((res) => res.requests)
  },

  sendCollabRequest(influencerId: number, message: string, budget: number) {
    return apiClient
      .post<ResData<CollabRequest>>('/influencer-match/collabs', { influencerId, message, proposedBudget: budget })
      .then(unwrapResponse)
  },

  updateCollabStatus(id: number, status: string) {
    return apiClient
      .put<ResData<CollabRequest>>(`/influencer-match/collabs/${id}/status`, { status })
      .then(unwrapResponse)
  },
}
