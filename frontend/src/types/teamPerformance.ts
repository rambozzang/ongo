export interface TeamMemberPerformance {
  id: number
  name: string
  email: string
  role: string
  avatar?: string
  tasksCompleted: number
  tasksAssigned: number
  completionRate: number
  avgCompletionTime: number
  contentProduced: number
  onTimeRate: number
  rating: number
  streak: number
  lastActiveAt: string
}

export interface TeamActivity {
  id: number
  memberId: number
  memberName: string
  action: string
  target: string
  timestamp: string
}

export interface TeamPerformanceSummary {
  totalMembers: number
  avgCompletionRate: number
  totalTasksCompleted: number
  totalContentProduced: number
  topPerformer: string
}
