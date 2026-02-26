import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  TeamMemberPerformance,
  TeamActivity,
  TeamPerformanceSummary,
} from '@/types/teamPerformance'

export const teamPerformanceApi = {
  getMembers() {
    return apiClient
      .get<ResData<TeamMemberPerformance[]>>('/team-performance/members')
      .then(unwrapResponse)
  },

  getMember(id: number) {
    return apiClient
      .get<ResData<TeamMemberPerformance>>(`/team-performance/members/${id}`)
      .then(unwrapResponse)
  },

  getActivities(limit = 20) {
    return apiClient
      .get<ResData<TeamActivity[]>>('/team-performance/activities', { params: { limit } })
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<TeamPerformanceSummary>>('/team-performance/summary')
      .then(unwrapResponse)
  },
}
