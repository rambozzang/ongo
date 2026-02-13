import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface MilestoneResponse {
  id: number
  title: string
  targetValue: number
  isReached: boolean
  reachedAt: string | null
  createdAt: string
}

export interface GoalResponse {
  id: number
  title: string
  description: string | null
  metricType: string
  targetValue: number
  currentValue: number
  startDate: string
  endDate: string
  status: string
  milestones: MilestoneResponse[]
  createdAt: string
  updatedAt: string
}

export interface CreateGoalRequest {
  title: string
  description?: string
  metricType: string
  targetValue: number
  startDate: string
  endDate: string
}

export interface UpdateGoalRequest {
  title?: string
  description?: string
  metricType?: string
  targetValue?: number
  startDate?: string
  endDate?: string
  status?: string
}

export interface CreateMilestoneRequest {
  title: string
  targetValue: number
}

export const goalApi = {
  list() {
    return apiClient.get<ResData<GoalResponse[]>>('/goals').then(unwrapResponse)
  },

  create(request: CreateGoalRequest) {
    return apiClient.post<ResData<GoalResponse>>('/goals', request).then(unwrapResponse)
  },

  update(id: number, request: UpdateGoalRequest) {
    return apiClient.put<ResData<GoalResponse>>(`/goals/${id}`, request).then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/goals/${id}`).then(unwrapResponse)
  },

  addMilestone(goalId: number, request: CreateMilestoneRequest) {
    return apiClient.post<ResData<MilestoneResponse>>(`/goals/${goalId}/milestones`, request).then(unwrapResponse)
  },

  updateProgress(goalId: number, currentValue: number) {
    return apiClient.put<ResData<GoalResponse>>(`/goals/${goalId}/progress`, { currentValue }).then(unwrapResponse)
  },
}
