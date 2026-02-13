import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type { Payment } from '@/types/subscription'

export const paymentApi = {
  getHistory(params: PageRequest) {
    return apiClient
      .get<ResData<{ payments: Payment[]; totalCount: number; page: number; size: number }>>('/payments/history', { params })
      .then(unwrapResponse)
      .then((data): PageResponse<Payment> => ({
        content: data.payments ?? [],
        totalElements: data.totalCount ?? 0,
        totalPages: data.size > 0 ? Math.ceil((data.totalCount ?? 0) / data.size) : 0,
        page: data.page ?? 0,
        size: data.size ?? 20,
        hasNext: (data.page + 1) * data.size < (data.totalCount ?? 0),
        hasPrevious: (data.page ?? 0) > 0,
      }))
  },
}
