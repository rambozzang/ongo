export type TriggerType = 'video_published' | 'views_threshold' | 'schedule_time' | 'comment_received' | 'subscriber_milestone'
export type ActionType = 'cross_post' | 'send_notification' | 'add_tag' | 'move_to_status' | 'generate_ai_metadata'
export type AutomationStatus = 'active' | 'paused' | 'error'

export interface AutomationTrigger {
  type: TriggerType
  config: Record<string, string | number | boolean>
}

export interface AutomationAction {
  type: ActionType
  config: Record<string, string | number | boolean>
}

export interface AutomationRule {
  id: number
  name: string
  description: string
  trigger: AutomationTrigger
  actions: AutomationAction[]
  status: AutomationStatus
  executionCount: number
  lastExecutedAt: string | null
  createdAt: string
  updatedAt: string
  isEnabled: boolean
}

export interface AutomationLog {
  id: number
  ruleId: number
  ruleName: string
  status: 'success' | 'failed'
  message: string
  executedAt: string
}

// ─── Workflow types ───

export type WorkflowTriggerType =
  | 'VIDEO_UPLOADED'
  | 'SCHEDULE_DUE'
  | 'COMMENT_RECEIVED'
  | 'ANALYTICS_MILESTONE'
  | 'CREDIT_LOW'
  | 'VIDEO_PUBLISHED'
  | 'SUBSCRIBER_MILESTONE'

export type WorkflowActionType =
  | 'SEND_NOTIFICATION'
  | 'AUTO_PUBLISH'
  | 'ADD_TAG'
  | 'GENERATE_METADATA'
  | 'CROSS_POST'
  | 'MOVE_STATUS'

export type ConditionGroupType = 'AND' | 'OR'

export type ConditionOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'REGEX'
  | 'IN'

export interface WorkflowCondition {
  id?: number
  groupType: ConditionGroupType
  field?: string
  operator?: ConditionOperator
  value?: string
  expression?: string
  nestedConditions?: WorkflowCondition[]
}

export interface WorkflowAction {
  id?: number
  actionType: WorkflowActionType
  config: Record<string, unknown>
  delayMinutes: number
  sortOrder: number
}

export interface Workflow {
  id: number
  name: string
  description: string | null
  triggerType: WorkflowTriggerType
  triggerConfig: Record<string, unknown>
  conditions: WorkflowCondition[]
  actions: WorkflowAction[]
  enabled: boolean
  executionCount: number
  lastExecutedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface WorkflowExecution {
  id: number
  workflowId: number
  triggerData: Record<string, unknown>
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  actionResults: ActionResult[]
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}

export interface ActionResult {
  actionType: string
  sortOrder: number
  status: string
  message: string | null
  executedAt: string
}

export interface CreateWorkflowRequest {
  name: string
  description?: string
  triggerType: WorkflowTriggerType
  triggerConfig?: Record<string, unknown>
  conditions?: WorkflowCondition[]
  actions?: { actionType: WorkflowActionType; config?: Record<string, unknown>; delayMinutes?: number }[]
  enabled?: boolean
}

export interface UpdateWorkflowRequest {
  name?: string
  description?: string
  triggerType?: WorkflowTriggerType
  triggerConfig?: Record<string, unknown>
  conditions?: WorkflowCondition[]
  actions?: { actionType: WorkflowActionType; config?: Record<string, unknown>; delayMinutes?: number }[]
}

export const WORKFLOW_TRIGGER_OPTIONS: { value: WorkflowTriggerType; label: string }[] = [
  { value: 'VIDEO_UPLOADED', label: '영상 업로드 시' },
  { value: 'VIDEO_PUBLISHED', label: '영상 게시 시' },
  { value: 'COMMENT_RECEIVED', label: '댓글 수신 시' },
  { value: 'ANALYTICS_MILESTONE', label: '성과 마일스톤 달성' },
  { value: 'SUBSCRIBER_MILESTONE', label: '구독자 마일스톤' },
  { value: 'SCHEDULE_DUE', label: '예약 시간 도래' },
  { value: 'CREDIT_LOW', label: '크레딧 부족' },
]

export const WORKFLOW_ACTION_OPTIONS: { value: WorkflowActionType; label: string }[] = [
  { value: 'SEND_NOTIFICATION', label: '알림 보내기' },
  { value: 'AUTO_PUBLISH', label: '자동 게시' },
  { value: 'ADD_TAG', label: '태그 추가' },
  { value: 'GENERATE_METADATA', label: 'AI 메타 생성' },
  { value: 'CROSS_POST', label: '크로스 포스팅' },
  { value: 'MOVE_STATUS', label: '상태 변경' },
]

export const CONDITION_OPERATOR_OPTIONS: { value: ConditionOperator; label: string }[] = [
  { value: 'EQUALS', label: '같음 (=)' },
  { value: 'NOT_EQUALS', label: '같지 않음 (!=)' },
  { value: 'GT', label: '초과 (>)' },
  { value: 'GTE', label: '이상 (>=)' },
  { value: 'LT', label: '미만 (<)' },
  { value: 'LTE', label: '이하 (<=)' },
  { value: 'CONTAINS', label: '포함' },
  { value: 'NOT_CONTAINS', label: '미포함' },
  { value: 'REGEX', label: '정규식' },
  { value: 'IN', label: '목록에 포함' },
]
