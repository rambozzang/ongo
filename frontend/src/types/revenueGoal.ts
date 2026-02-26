export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'EXPIRED'

export interface RevenueGoal {
  id: number
  name: string
  targetAmount: number
  currentAmount: number
  currency: string
  period: string
  platform: string
  status: GoalStatus
  progress: number
  startDate: string
  endDate: string
}

export interface RevenueGoalMilestone {
  id: number
  goalId: number
  label: string
  targetAmount: number
  reached: boolean
  reachedAt?: string
}

export interface RevenueGoalSummary {
  totalGoals: number
  activeGoals: number
  avgProgress: number
  totalTarget: number
  totalCurrent: number
}
