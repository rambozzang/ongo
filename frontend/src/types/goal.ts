export type GoalType = 'subscribers' | 'views' | 'uploads' | 'revenue' | 'engagement' | 'custom'
export type GoalStatus = 'active' | 'completed' | 'failed' | 'paused'
export type GoalPeriod = 'weekly' | 'monthly' | 'quarterly' | 'yearly' | 'custom'

export interface Goal {
  id: number
  title: string
  description: string
  type: GoalType
  status: GoalStatus
  period: GoalPeriod
  targetValue: number
  currentValue: number
  unit: string
  startDate: string
  endDate: string
  milestones: Milestone[]
  createdAt: string
  completedAt: string | null
}

export interface Milestone {
  id: number
  title: string
  targetValue: number
  isCompleted: boolean
  completedAt: string | null
}
