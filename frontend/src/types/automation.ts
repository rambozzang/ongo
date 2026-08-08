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
