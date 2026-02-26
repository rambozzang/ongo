import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { FundingSummary, FundingTransaction, FundingGoal, FundingFilter } from '@/types/fanFunding'

export const fanFundingApi = {
  getSummary: (period?: string) =>
    apiClient.get<ResData<FundingSummary>>('/fan-funding/summary', { params: { period } }).then(unwrapResponse),

  getTransactions: (filter?: FundingFilter) =>
    apiClient.get<ResData<FundingTransaction[]>>('/fan-funding/transactions', { params: filter }).then(unwrapResponse),

  getGoals: () =>
    apiClient.get<ResData<FundingGoal[]>>('/fan-funding/goals').then(unwrapResponse),

  createGoal: (goal: Omit<FundingGoal, 'id' | 'currentAmount'>) =>
    apiClient.post<ResData<FundingGoal>>('/fan-funding/goals', goal).then(unwrapResponse),

  updateGoal: (id: number, goal: Partial<FundingGoal>) =>
    apiClient.put<ResData<FundingGoal>>(`/fan-funding/goals/${id}`, goal).then(unwrapResponse),

  deleteGoal: (id: number) =>
    apiClient.delete<ResData<void>>(`/fan-funding/goals/${id}`).then(unwrapResponse),
}
