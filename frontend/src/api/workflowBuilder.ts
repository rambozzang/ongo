import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  WfWorkflow,
  WfExecutionLog,
  CreateWfWorkflowRequest,
  UpdateWfWorkflowRequest,
} from '@/types/workflowBuilder'

export const workflowBuilderApi = {
  list() {
    return apiClient
      .get<ResData<WfWorkflow[]>>('/workflows')
      .then(unwrapResponse)
  },

  get(id: number) {
    return apiClient
      .get<ResData<WfWorkflow>>(`/workflows/${id}`)
      .then(unwrapResponse)
  },

  create(request: CreateWfWorkflowRequest) {
    return apiClient
      .post<ResData<WfWorkflow>>('/workflows', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateWfWorkflowRequest) {
    return apiClient
      .put<ResData<WfWorkflow>>(`/workflows/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient
      .delete<ResData<void>>(`/workflows/${id}`)
      .then(unwrapResponse)
  },

  toggle(id: number) {
    return apiClient
      .post<ResData<WfWorkflow>>(`/workflows/${id}/toggle`)
      .then(unwrapResponse)
  },

  testRun(id: number) {
    return apiClient
      .post<ResData<WfExecutionLog>>(`/workflows/${id}/test`)
      .then(unwrapResponse)
  },

  getExecutions(id: number) {
    return apiClient
      .get<ResData<WfExecutionLog[]>>(`/workflows/${id}/executions`)
      .then(unwrapResponse)
  },
}
