import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { TemplateListResponse, TemplateResponse, CreateTemplateRequest, UpdateTemplateRequest } from '@/types/template'

export const templatesApi = {
  list(params?: { page?: number; size?: number }) {
    return apiClient
      .get<ResData<TemplateListResponse>>('/templates', { params })
      .then(unwrapResponse)
  },

  create(request: CreateTemplateRequest) {
    return apiClient
      .post<ResData<TemplateResponse>>('/templates', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateTemplateRequest) {
    return apiClient
      .put<ResData<TemplateResponse>>(`/templates/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/templates/${id}`).then(unwrapResponse)
  },

  use(id: number) {
    return apiClient
      .post<ResData<TemplateResponse>>(`/templates/${id}/use`)
      .then(unwrapResponse)
  },
}
