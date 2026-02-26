import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RevenueGoal, RevenueGoalMilestone, RevenueGoalSummary } from '@/types/revenueGoal'

export const revenueGoalApi = {
  getGoals: () =>
    apiClient.get<ResData<RevenueGoal[]>>('/revenue-goals').then(unwrapResponse),

  getGoal: (id: number) =>
    apiClient.get<ResData<RevenueGoal>>(`/revenue-goals/${id}`).then(unwrapResponse),

  getMilestones: (goalId: number) =>
    apiClient.get<ResData<RevenueGoalMilestone[]>>(`/revenue-goals/${goalId}/milestones`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<RevenueGoalSummary>>('/revenue-goals/summary').then(unwrapResponse),

  createGoal: (goal: Omit<RevenueGoal, 'id' | 'progress' | 'currentAmount'>) =>
    apiClient.post<ResData<RevenueGoal>>('/revenue-goals', goal).then(unwrapResponse),

  deleteGoal: (id: number) =>
    apiClient.delete<ResData<void>>(`/revenue-goals/${id}`).then(unwrapResponse),
}
