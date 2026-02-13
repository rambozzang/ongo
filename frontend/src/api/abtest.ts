import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ABTestListResponse,
  ABTestResponse,
  ABTestStatisticsResponse,
  CreateABTestRequest,
  UpdateABTestRequest,
} from '@/types/abtest'

export const abtestApi = {
  list() {
    return apiClient
      .get<ResData<ABTestListResponse>>('/ab-tests')
      .then(unwrapResponse)
  },

  get(id: number) {
    return apiClient
      .get<ResData<ABTestResponse>>(`/ab-tests/${id}`)
      .then(unwrapResponse)
  },

  create(request: CreateABTestRequest) {
    return apiClient
      .post<ResData<ABTestResponse>>('/ab-tests', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateABTestRequest) {
    return apiClient
      .put<ResData<ABTestResponse>>(`/ab-tests/${id}`, request)
      .then(unwrapResponse)
  },

  remove(id: number) {
    return apiClient
      .delete<ResData<null>>(`/ab-tests/${id}`)
      .then(unwrapResponse)
  },

  start(id: number) {
    return apiClient
      .post<ResData<ABTestResponse>>(`/ab-tests/${id}/start`)
      .then(unwrapResponse)
  },

  stop(id: number) {
    return apiClient
      .post<ResData<ABTestResponse>>(`/ab-tests/${id}/stop`)
      .then(unwrapResponse)
  },

  statistics(id: number) {
    return apiClient
      .get<ResData<ABTestStatisticsResponse>>(`/ab-tests/${id}/statistics`)
      .then(unwrapResponse)
  },
}
