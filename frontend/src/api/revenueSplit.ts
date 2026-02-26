import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  RevenueSplit,
  RevenueSplitSummary,
  CreateRevenueSplitRequest,
} from '@/types/revenueSplit'

export const revenueSplitApi = {
  getSplits(status?: string) {
    return apiClient
      .get<ResData<RevenueSplit[]>>('/revenue-splits', { params: { status } })
      .then(unwrapResponse)
  },

  getSplit(id: number) {
    return apiClient
      .get<ResData<RevenueSplit>>(`/revenue-splits/${id}`)
      .then(unwrapResponse)
  },

  createSplit(request: CreateRevenueSplitRequest) {
    return apiClient
      .post<ResData<RevenueSplit>>('/revenue-splits', request)
      .then(unwrapResponse)
  },

  updateSplit(id: number, request: Partial<CreateRevenueSplitRequest>) {
    return apiClient
      .put<ResData<RevenueSplit>>(`/revenue-splits/${id}`, request)
      .then(unwrapResponse)
  },

  deleteSplit(id: number) {
    return apiClient
      .delete<ResData<void>>(`/revenue-splits/${id}`)
      .then(unwrapResponse)
  },

  approveSplit(id: number) {
    return apiClient
      .post<ResData<RevenueSplit>>(`/revenue-splits/${id}/approve`)
      .then(unwrapResponse)
  },

  distributeSplit(id: number) {
    return apiClient
      .post<ResData<RevenueSplit>>(`/revenue-splits/${id}/distribute`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<RevenueSplitSummary>>('/revenue-splits/summary')
      .then(unwrapResponse)
  },
}
