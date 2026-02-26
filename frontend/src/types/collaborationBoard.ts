export type BoardColumnType = 'IDEA' | 'SCRIPTING' | 'FILMING' | 'EDITING' | 'REVIEW' | 'SCHEDULED' | 'PUBLISHED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED'

export interface BoardTask {
  id: number
  title: string
  description?: string
  column: BoardColumnType
  priority: TaskPriority
  status: TaskStatus
  assigneeId?: number
  assigneeName?: string
  assigneeAvatar?: string
  dueDate?: string
  tags: string[]
  attachments: number
  comments: number
  videoId?: string
  orderIndex: number
  createdAt: string
  updatedAt: string
}

export interface BoardColumn {
  type: BoardColumnType
  label: string
  color: string
  tasks: BoardTask[]
  taskLimit?: number
}

export interface BoardActivity {
  id: number
  userId: number
  userName: string
  action: 'CREATED' | 'MOVED' | 'ASSIGNED' | 'COMMENTED' | 'COMPLETED'
  taskId: number
  taskTitle: string
  fromColumn?: string
  toColumn?: string
  timestamp: string
}

export interface BoardSummary {
  totalTasks: number
  completedTasks: number
  overdueTasks: number
  inProgressTasks: number
  avgCompletionTime: number
  teamMembers: number
  recentActivities: BoardActivity[]
}

export interface CreateBoardTaskRequest {
  title: string
  description?: string
  column: BoardColumnType
  priority: TaskPriority
  assigneeId?: number
  dueDate?: string
  tags?: string[]
}

export interface MoveBoardTaskRequest {
  taskId: number
  toColumn: BoardColumnType
  orderIndex: number
}
