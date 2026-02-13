import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Workflow,
  WorkflowExecution,
  CreateWorkflowRequest,
  UpdateWorkflowRequest,
} from '@/types/automation'

export interface AutomationRuleResponse {
  id: number
  name: string
  description: string | null
  triggerType: string
  triggerConfig: Record<string, unknown>
  actionType: string
  actionConfig: Record<string, unknown>
  isActive: boolean
  lastTriggeredAt: string | null
  executionCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface CreateAutomationRuleRequest {
  name: string
  description?: string
  triggerType: string
  triggerConfig?: Record<string, unknown>
  actionType: string
  actionConfig?: Record<string, unknown>
  isActive?: boolean
}

export interface UpdateAutomationRuleRequest {
  name?: string
  description?: string
  triggerType?: string
  triggerConfig?: Record<string, unknown>
  actionType?: string
  actionConfig?: Record<string, unknown>
}

export const automationApi = {
  list() {
    return apiClient
      .get<ResData<AutomationRuleResponse[]>>('/automation/rules')
      .then(unwrapResponse)
  },

  create(request: CreateAutomationRuleRequest) {
    return apiClient
      .post<ResData<AutomationRuleResponse>>('/automation/rules', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateAutomationRuleRequest) {
    return apiClient
      .put<ResData<AutomationRuleResponse>>(`/automation/rules/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient
      .delete<ResData<void>>(`/automation/rules/${id}`)
      .then(unwrapResponse)
  },

  toggle(id: number) {
    return apiClient
      .put<ResData<AutomationRuleResponse>>(`/automation/rules/${id}/toggle`)
      .then(unwrapResponse)
  },

  // ─── Workflow API ───

  listWorkflows() {
    return apiClient
      .get<ResData<Workflow[]>>('/automation/workflows')
      .then(unwrapResponse)
  },

  getWorkflow(id: number) {
    return apiClient
      .get<ResData<Workflow>>(`/automation/workflows/${id}`)
      .then(unwrapResponse)
  },

  createWorkflow(request: CreateWorkflowRequest) {
    return apiClient
      .post<ResData<Workflow>>('/automation/workflows', request)
      .then(unwrapResponse)
  },

  updateWorkflow(id: number, request: UpdateWorkflowRequest) {
    return apiClient
      .put<ResData<Workflow>>(`/automation/workflows/${id}`, request)
      .then(unwrapResponse)
  },

  deleteWorkflow(id: number) {
    return apiClient
      .delete<ResData<void>>(`/automation/workflows/${id}`)
      .then(unwrapResponse)
  },

  toggleWorkflow(id: number) {
    return apiClient
      .post<ResData<Workflow>>(`/automation/workflows/${id}/toggle`)
      .then(unwrapResponse)
  },

  getWorkflowExecutions(id: number) {
    return apiClient
      .get<ResData<WorkflowExecution[]>>(`/automation/workflows/${id}/executions`)
      .then(unwrapResponse)
  },
}
