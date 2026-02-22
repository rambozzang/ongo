export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'revision_requested'
export type ApprovalRole = 'requester' | 'reviewer'

export interface ApprovalRequest {
  id: string
  videoId: number
  videoTitle: string
  videoThumbnail?: string
  platforms: string[]
  scheduledAt?: string
  requesterId: string
  requesterName: string
  requesterAvatar?: string
  reviewerId?: string
  reviewerName?: string
  status: ApprovalStatus
  comment?: string
  revisionNote?: string
  requestedAt: string
  decidedAt?: string
  changes?: ApprovalChange[]
}

export interface ApprovalChange {
  field: string
  label: string
  oldValue: string
  newValue: string
}

export interface ApprovalComment {
  id: string
  approvalId: string
  userId: string
  userName: string
  userAvatar?: string
  content: string
  field?: string // specific field this comment refers to
  createdAt: string
}

export interface ApprovalStats {
  pending: number
  approved: number
  rejected: number
  revisionRequested: number
  avgResponseTime: string // e.g., "2시간 30분"
}

// Approval Chain types
export interface ApprovalChainStep {
  id: number
  approvalId: number
  stepOrder: number
  approverId: number
  approverName: string
  status: 'PENDING' | 'WAITING' | 'APPROVED' | 'REJECTED'
  deadlineAt?: string
  approvedAt?: string
  comment?: string
  createdAt?: string
}

export interface ApprovalChainSlaStatus {
  stepId: number
  stepOrder: number
  approverName: string
  status: string
  deadlineAt?: string
  remainingMinutes?: number
  isOverdue: boolean
}

export interface CreateApprovalChainRequest {
  steps: { approverId: number; approverName: string; deadlineHours?: number }[]
  deadlineHours?: number
}

// Workflow Board types
export interface WorkflowBoard {
  columns: WorkflowColumn[]
  totalItems: number
  stats: WorkflowBoardStats
}

export interface WorkflowColumn {
  status: string
  statusLabel: string
  items: WorkflowItem[]
  count: number
}

export interface WorkflowItem {
  approvalId: number
  videoId: number
  videoTitle: string
  platforms: string[]
  requesterName: string
  reviewerName: string | null
  scheduledAt: string | null
  status: string
  requestedAt: string
  updatedAt: string | null
  chainSteps: WorkflowStep[] | null
}

export interface WorkflowStep {
  stepOrder: number
  stepName: string
  reviewerName: string | null
  status: string
  completedAt: string | null
}

export interface WorkflowBoardStats {
  totalPending: number
  totalInReview: number
  totalApproved: number
  totalRejected: number
  avgApprovalTimeHours: number | null
}

export interface MyTasks {
  assignedToMe: WorkflowItem[]
  requestedByMe: WorkflowItem[]
}

export interface PendingReviews {
  reviews: WorkflowItem[]
  overdueCount: number
}
