/** Values accepted by the backend automation contract. */
export type TriggerType =
  | 'VIDEO_UPLOADED'
  | 'SCHEDULE_DUE'
  | 'COMMENT_RECEIVED'
  | 'ANALYTICS_MILESTONE'
  | 'CREDIT_LOW'
  | 'VIEWS_MILESTONE'
  | 'VIRAL_DETECTED'
  | 'ENGAGEMENT_DROP'

export type ActionType = 'SEND_NOTIFICATION' | 'AUTO_PUBLISH' | 'ADD_TAG' | 'GENERATE_METADATA'
export type AutomationStatus = 'active' | 'paused' | 'error'

export type AutomationConfigValue = string | number | boolean | string[] | number[]

export interface AutomationTrigger {
  type: TriggerType
  config: Record<string, AutomationConfigValue>
}

export interface AutomationAction {
  type: ActionType
  config: Record<string, AutomationConfigValue>
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
