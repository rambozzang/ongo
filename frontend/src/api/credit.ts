import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type { CreditBalance, CreditTransaction, PurchaseCreditRequest } from '@/types/credit'

export const creditApi = {
  getBalance() {
    return apiClient.get<ResData<CreditBalance>>('/credits').then(unwrapResponse)
  },

  getTransactions(params: PageRequest) {
    return apiClient
      .get<ResData<PageResponse<CreditTransaction>>>('/credits/transactions', { params })
      .then(unwrapResponse)
  },

  purchase(request: PurchaseCreditRequest) {
    return apiClient
      .post<ResData<CreditBalance>>('/credits/purchase', request)
      .then(unwrapResponse)
  },
}
