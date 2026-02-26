import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { FanReward, FanActivity, FanRewardSummary } from '@/types/fanReward'

export const fanRewardApi = {
  getRewards: () =>
    apiClient.get<ResData<FanReward[]>>('/api/v1/fan-rewards').then(unwrapResponse),
  getReward: (id: number) =>
    apiClient.get<ResData<FanReward>>(`/api/v1/fan-rewards/${id}`).then(unwrapResponse),
  createReward: (data: Partial<FanReward>) =>
    apiClient.post<ResData<FanReward>>('/api/v1/fan-rewards', data).then(unwrapResponse),
  getActivities: () =>
    apiClient.get<ResData<FanActivity[]>>('/api/v1/fan-rewards/activities').then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<FanRewardSummary>>('/api/v1/fan-rewards/summary').then(unwrapResponse),
}

export default fanRewardApi
