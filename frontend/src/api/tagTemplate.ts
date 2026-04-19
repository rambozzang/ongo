import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { TagTemplate, TagTemplateCreateRequest, TagTemplateUpdateRequest } from '@/types/tagTemplate'

export const tagTemplateApi = {
  list() {
    return apiClient
      .get<ResData<TagTemplate[]>>('/tag-templates')
      .then(unwrapResponse)
  },

  create(request: TagTemplateCreateRequest) {
    return apiClient
      .post<ResData<TagTemplate>>('/tag-templates', request)
      .then(unwrapResponse)
  },

  update(id: number, request: TagTemplateUpdateRequest) {
    return apiClient
      .put<ResData<TagTemplate>>(`/tag-templates/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient
      .delete<ResData<void>>(`/tag-templates/${id}`)
      .then(unwrapResponse)
  },
}
