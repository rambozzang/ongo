import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  CoachSession,
  GrowthGoal,
  WeeklyReport,
  ActionItem,
} from '@/types/growthCoach'

export const growthCoachApi = {
  getSession() {
    return apiClient
      .get<ResData<CoachSession>>('/ai/growth-coach/session')
      .then(unwrapResponse)
  },

  setGoal(goal: Omit<GrowthGoal, 'id' | 'progress' | 'currentValue'>) {
    return apiClient
      .post<ResData<GrowthGoal>>('/ai/growth-coach/goals', goal)
      .then(unwrapResponse)
  },

  updateGoal(id: number, updates: Partial<GrowthGoal>) {
    return apiClient
      .put<ResData<GrowthGoal>>(`/ai/growth-coach/goals/${id}`, updates)
      .then(unwrapResponse)
  },

  deleteGoal(id: number) {
    return apiClient
      .delete<ResData<void>>(`/ai/growth-coach/goals/${id}`)
      .then(unwrapResponse)
  },

  generateReport() {
    return apiClient
      .post<ResData<WeeklyReport>>('/ai/growth-coach/reports/generate')
      .then(unwrapResponse)
  },

  getReports() {
    return apiClient
      .get<ResData<{ reports: WeeklyReport[] }>>('/ai/growth-coach/reports')
      .then(unwrapResponse)
      .then((res) => res.reports)
  },

  updateActionItem(reportId: number, actionId: number, updates: Partial<ActionItem>) {
    return apiClient
      .put<ResData<ActionItem>>(`/ai/growth-coach/reports/${reportId}/actions/${actionId}`, updates)
      .then(unwrapResponse)
  },
}
