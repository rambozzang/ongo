import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest } from '@/types/workspace'

export const workspaceApi = {
  list() {
    return apiClient.get<ResData<Workspace[]>>('/workspaces').then(unwrapResponse)
  },
  get(id: number) {
    return apiClient.get<ResData<Workspace>>(`/workspaces/${id}`).then(unwrapResponse)
  },
  create(request: CreateWorkspaceRequest) {
    return apiClient.post<ResData<Workspace>>('/workspaces', request).then(unwrapResponse)
  },
  update(id: number, request: UpdateWorkspaceRequest) {
    return apiClient.put<ResData<Workspace>>(`/workspaces/${id}`, request).then(unwrapResponse)
  },
  remove(id: number) {
    return apiClient.delete<ResData<void>>(`/workspaces/${id}`).then(unwrapResponse)
  },
}
