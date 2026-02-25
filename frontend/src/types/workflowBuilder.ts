// ─── Workflow Builder Types ───

export type WfNodeType = 'trigger' | 'condition' | 'action'

export type WfTriggerKind =
  | 'VIDEO_UPLOADED'
  | 'VIDEO_PUBLISHED'
  | 'COMMENT_RECEIVED'
  | 'SCHEDULE_DUE'
  | 'ANALYTICS_MILESTONE'
  | 'SUBSCRIBER_MILESTONE'
  | 'CREDIT_LOW'

export type WfConditionKind =
  | 'PLATFORM'
  | 'TAG_MATCH'
  | 'TIME_RANGE'
  | 'VIEW_COUNT'
  | 'DURATION'
  | 'LANGUAGE'

export type WfActionKind =
  | 'SEND_NOTIFICATION'
  | 'AUTO_PUBLISH'
  | 'CROSS_POST'
  | 'GENERATE_METADATA'
  | 'ADD_TAG'
  | 'MOVE_STATUS'
  | 'AI_THUMBNAIL'

export type WfNodeKind = WfTriggerKind | WfConditionKind | WfActionKind

export interface WfPort {
  id: string
  type: 'input' | 'output'
  nodeId: string
}

export interface WfConnection {
  id: string
  sourceNodeId: string
  sourcePortId: string
  targetNodeId: string
  targetPortId: string
}

export interface WfNodePosition {
  x: number
  y: number
}

export interface WfNode {
  id: string
  type: WfNodeType
  kind: WfNodeKind
  label: string
  position: WfNodePosition
  config: Record<string, unknown>
  ports: WfPort[]
}

export interface WfWorkflow {
  id: number
  name: string
  description: string
  nodes: WfNode[]
  connections: WfConnection[]
  isEnabled: boolean
  executionCount: number
  lastExecutedAt: string | null
  createdAt: string
  updatedAt: string
}

export type WfWorkflowStatus = 'active' | 'inactive' | 'error'

export interface WfNodeDefinition {
  kind: WfNodeKind
  type: WfNodeType
  label: string
  icon: string
  description: string
  defaultConfig: Record<string, unknown>
}

export interface WfTemplate {
  id: string
  name: string
  description: string
  nodes: Omit<WfNode, 'id'>[]
  connections: Omit<WfConnection, 'id'>[]
}

// API types
export interface CreateWfWorkflowRequest {
  name: string
  description?: string
  nodes: Omit<WfNode, 'id'>[]
  connections: Omit<WfConnection, 'id'>[]
  isEnabled?: boolean
}

export interface UpdateWfWorkflowRequest {
  name?: string
  description?: string
  nodes?: Omit<WfNode, 'id'>[]
  connections?: Omit<WfConnection, 'id'>[]
  isEnabled?: boolean
}

export interface WfExecutionLog {
  id: number
  workflowId: number
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  triggerData: Record<string, unknown>
  results: WfActionResult[]
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}

export interface WfActionResult {
  nodeId: string
  actionKind: WfActionKind
  status: 'success' | 'failed' | 'skipped'
  message: string | null
  executedAt: string
}
