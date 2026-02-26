export interface AutomationRule {
  id: number
  name: string
  platform: string
  triggerType: 'SCHEDULE' | 'EVENT' | 'THRESHOLD' | 'KEYWORD'
  actionType: 'PUBLISH' | 'NOTIFY' | 'REPLY' | 'TAG' | 'ARCHIVE'
  condition: string
  isActive: boolean
  executionCount: number
  lastExecuted: string | null
  createdAt: string
}

export interface AutomationLog {
  id: number
  ruleId: number
  ruleName: string
  status: 'SUCCESS' | 'FAILED' | 'SKIPPED'
  message: string
  executedAt: string
}

export interface PlatformAutomationSummary {
  totalRules: number
  activeRules: number
  totalExecutions: number
  successRate: number
  topPlatform: string
}
