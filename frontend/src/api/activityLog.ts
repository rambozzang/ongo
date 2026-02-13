import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface ActivityLogResponse {
  id: number
  userId: number
  action: string
  entityType: string | null
  entityId: number | null
  details: string
  ipAddress: string | null
  createdAt: string | null
}

export interface ActivityLogListResponse {
  logs: ActivityLogResponse[]
  totalElements: number
  page: number
  size: number
}

export const activityLogApi = {
  list(params?: { page?: number; size?: number; action?: string; entityType?: string; startDate?: string; endDate?: string }) {
    return apiClient.get<ResData<ActivityLogListResponse>>('/activity-logs', { params }).then(unwrapResponse)
  },
}
