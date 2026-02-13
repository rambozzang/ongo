import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { WatermarkListResponse, WatermarkResponse, CreateWatermarkRequest, UpdateWatermarkRequest } from '@/types/watermark'

export const watermarkApi = {
  list() {
    return apiClient
      .get<ResData<WatermarkListResponse>>('/watermarks')
      .then(unwrapResponse)
  },

  create(request: CreateWatermarkRequest) {
    return apiClient
      .post<ResData<WatermarkResponse>>('/watermarks', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateWatermarkRequest) {
    return apiClient
      .put<ResData<WatermarkResponse>>(`/watermarks/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/watermarks/${id}`).then(unwrapResponse)
  },

  setDefault(id: number) {
    return apiClient
      .put<ResData<WatermarkResponse>>(`/watermarks/${id}/default`)
      .then(unwrapResponse)
  },
}
