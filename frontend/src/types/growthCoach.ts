export type GoalType = 'SUBSCRIBERS' | 'VIEWS' | 'ENGAGEMENT' | 'REVENUE' | 'UPLOADS'
export type ActionPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type ActionStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED'

export interface GrowthGoal {
  id: number
  type: GoalType
  targetValue: number
  currentValue: number
  deadline: string
  progress: number
}

export interface ActionItem {
  id: number
  title: string
  description: string
  priority: ActionPriority
  status: ActionStatus
  category: string
  estimatedImpact: string
  dueDate?: string
}

export interface WeeklyReport {
  id: number
  weekStart: string
  weekEnd: string
  summary: string
  highlights: string[]
  concerns: string[]
  subscriberGrowth: number
  viewsGrowth: number
  engagementChange: number
  topContent: { title: string; views: number; platform: string }[]
  actionItems: ActionItem[]
  overallScore: number
  generatedAt: string
}

export interface GrowthInsight {
  id: number
  title: string
  description: string
  impact: 'HIGH' | 'MEDIUM' | 'LOW'
  category: string
  actionable: boolean
  suggestedAction?: string
  createdAt: string
}

export interface CoachSession {
  goals: GrowthGoal[]
  currentReport: WeeklyReport | null
  pastReports: WeeklyReport[]
  insights: GrowthInsight[]
  overallGrowthRate: number
}
