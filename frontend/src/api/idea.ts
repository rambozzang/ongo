import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface IdeaResponse {
  id: number
  title: string
  description: string | null
  status: string
  category: string | null
  tags: string[]
  priority: string
  source: string | null
  referenceUrl: string | null
  dueDate: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateIdeaRequest {
  title: string
  description?: string
  status?: string
  category?: string
  tags?: string[]
  priority?: string
  source?: string
  referenceUrl?: string
  dueDate?: string
}

export interface UpdateIdeaRequest {
  title?: string
  description?: string
  status?: string
  category?: string
  tags?: string[]
  priority?: string
  source?: string
  referenceUrl?: string
  dueDate?: string
}

export const ideaApi = {
  list(params?: { status?: string; category?: string; priority?: string }) {
    return apiClient.get<ResData<IdeaResponse[]>>('/ideas', { params }).then(unwrapResponse)
  },

  create(request: CreateIdeaRequest) {
    return apiClient.post<ResData<IdeaResponse>>('/ideas', request).then(unwrapResponse)
  },

  update(id: number, request: UpdateIdeaRequest) {
    return apiClient.put<ResData<IdeaResponse>>(`/ideas/${id}`, request).then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<void>>(`/ideas/${id}`).then(unwrapResponse)
  },

  changeStatus(id: number, status: string) {
    return apiClient.put<ResData<IdeaResponse>>(`/ideas/${id}/status`, { status }).then(unwrapResponse)
  },
}
