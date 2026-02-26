import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  BrandSafetyCheck,
  BrandSafetyRule,
  BrandSafetySummary,
  RunSafetyCheckRequest,
} from '@/types/brandSafety'

export const brandSafetyApi = {
  getChecks(status?: string) {
    return apiClient
      .get<ResData<BrandSafetyCheck[]>>('/brand-safety/checks', { params: { status } })
      .then(unwrapResponse)
  },

  getCheck(id: number) {
    return apiClient
      .get<ResData<BrandSafetyCheck>>(`/brand-safety/checks/${id}`)
      .then(unwrapResponse)
  },

  runCheck(request: RunSafetyCheckRequest) {
    return apiClient
      .post<ResData<BrandSafetyCheck>>('/brand-safety/checks', request)
      .then(unwrapResponse)
  },

  getRules() {
    return apiClient
      .get<ResData<BrandSafetyRule[]>>('/brand-safety/rules')
      .then(unwrapResponse)
  },

  toggleRule(id: number, isEnabled: boolean) {
    return apiClient
      .patch<ResData<BrandSafetyRule>>(`/brand-safety/rules/${id}`, { isEnabled })
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<BrandSafetySummary>>('/brand-safety/summary')
      .then(unwrapResponse)
  },
}
