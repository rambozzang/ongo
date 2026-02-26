import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CreatorMilestone,
  MilestoneTimeline,
  MilestoneReward,
  CreatorMilestoneSummary,
  CreateMilestoneRequest,
} from '@/types/creatorMilestone'

export const creatorMilestoneApi = {
  getMilestones(status?: string) {
    return apiClient
      .get<ResData<CreatorMilestone[]>>('/creator-milestones', { params: { status } })
      .then(unwrapResponse)
  },

  getMilestone(id: number) {
    return apiClient
      .get<ResData<CreatorMilestone>>(`/creator-milestones/${id}`)
      .then(unwrapResponse)
  },

  createMilestone(request: CreateMilestoneRequest) {
    return apiClient
      .post<ResData<CreatorMilestone>>('/creator-milestones', request)
      .then(unwrapResponse)
  },

  deleteMilestone(id: number) {
    return apiClient
      .delete<ResData<void>>(`/creator-milestones/${id}`)
      .then(unwrapResponse)
  },

  getTimeline(milestoneId: number) {
    return apiClient
      .get<ResData<MilestoneTimeline[]>>(`/creator-milestones/${milestoneId}/timeline`)
      .then(unwrapResponse)
  },

  getRewards(milestoneId: number) {
    return apiClient
      .get<ResData<MilestoneReward[]>>(`/creator-milestones/${milestoneId}/rewards`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<CreatorMilestoneSummary>>('/creator-milestones/summary')
      .then(unwrapResponse)
  },
}
