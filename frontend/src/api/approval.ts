import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { WorkflowBoard, MyTasks, PendingReviews } from '@/types/approval'

export interface ApprovalListResponse {
  approvals: ApprovalResponse[]
  totalCount: number
}

export interface ApprovalResponse {
  id: number
  userId: number
  videoId: number
  videoTitle: string
  platforms: string
  scheduledAt: string | null
  requesterId: number
  requesterName: string
  reviewerId: number | null
  reviewerName: string | null
  status: string
  comment: string | null
  revisionNote: string | null
  requestedAt: string
  decidedAt: string | null
  createdAt: string | null
}

export interface ApprovalCommentResponse {
  id: number
  approvalId: number
  userId: number
  userName: string
  content: string
  field: string | null
  createdAt: string | null
}

export interface CreateApprovalRequest {
  videoId: number
  videoTitle: string
  platforms: string
  scheduledAt?: string
  reviewerId?: number
  reviewerName?: string
  comment?: string
}

export interface UpdateApprovalStatusRequest {
  status: string
  comment?: string
  revisionNote?: string
}

export interface CreateApprovalCommentRequest {
  content: string
  field?: string
}

export const approvalApi = {
  list(status?: string) {
    const params = status ? { status } : {}
    return apiClient
      .get<ResData<ApprovalListResponse>>('/approvals', { params })
      .then(unwrapResponse)
  },

  get(id: number) {
    return apiClient
      .get<ResData<ApprovalResponse>>(`/approvals/${id}`)
      .then(unwrapResponse)
  },

  create(request: CreateApprovalRequest) {
    return apiClient
      .post<ResData<ApprovalResponse>>('/approvals', request)
      .then(unwrapResponse)
  },

  updateStatus(id: number, request: UpdateApprovalStatusRequest) {
    return apiClient
      .put<ResData<ApprovalResponse>>(`/approvals/${id}/status`, request)
      .then(unwrapResponse)
  },

  getComments(id: number) {
    return apiClient
      .get<ResData<ApprovalCommentResponse[]>>(`/approvals/${id}/comments`)
      .then(unwrapResponse)
  },

  addComment(id: number, request: CreateApprovalCommentRequest) {
    return apiClient
      .post<ResData<ApprovalCommentResponse>>(`/approvals/${id}/comments`, request)
      .then(unwrapResponse)
  },

  // Approval Chain
  getChainSteps(id: number) {
    return apiClient
      .get<ResData<import('@/types/approval').ApprovalChainStep[]>>(`/approvals/${id}/chain`)
      .then(unwrapResponse)
  },

  createChain(id: number, request: import('@/types/approval').CreateApprovalChainRequest) {
    return apiClient
      .post<ResData<import('@/types/approval').ApprovalChainStep[]>>(`/approvals/${id}/chain`, request)
      .then(unwrapResponse)
  },

  approveStep(id: number, stepId: number, comment?: string) {
    return apiClient
      .post<ResData<import('@/types/approval').ApprovalChainStep>>(`/approvals/${id}/chain/${stepId}/approve`, { comment })
      .then(unwrapResponse)
  },

  rejectStep(id: number, stepId: number, comment?: string) {
    return apiClient
      .post<ResData<import('@/types/approval').ApprovalChainStep>>(`/approvals/${id}/chain/${stepId}/reject`, { comment })
      .then(unwrapResponse)
  },

  getSlaStatus(id: number) {
    return apiClient
      .get<ResData<import('@/types/approval').ApprovalChainSlaStatus[]>>(`/approvals/${id}/sla`)
      .then(unwrapResponse)
  },

  // Workflow Board
  workflowBoard() {
    return apiClient
      .get<ResData<WorkflowBoard>>('/approvals/workflow-board')
      .then(unwrapResponse)
  },

  myTasks() {
    return apiClient
      .get<ResData<MyTasks>>('/approvals/my-tasks')
      .then(unwrapResponse)
  },

  pendingReviews() {
    return apiClient
      .get<ResData<PendingReviews>>('/approvals/pending-reviews')
      .then(unwrapResponse)
  },
}
