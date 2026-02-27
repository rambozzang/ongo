import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { FanReward, FanActivity, FanRewardSummary } from '@/types/fanReward'

export const fanRewardApi = {
  getRewards: () =>
    apiClient.get<ResData<FanReward[]>>('/fan-rewards').then(unwrapResponse),
  getReward: (id: number) =>
    apiClient.get<ResData<FanReward>>(`/fan-rewards/${id}`).then(unwrapResponse),
  createReward: (data: Partial<FanReward>) =>
    apiClient.post<ResData<FanReward>>('/fan-rewards', data).then(unwrapResponse),
  getActivities: () =>
    apiClient.get<ResData<FanActivity[]>>('/fan-rewards/activities').then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<FanRewardSummary>>('/fan-rewards/summary').then(unwrapResponse),
}

export default fanRewardApi
