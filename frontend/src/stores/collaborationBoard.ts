import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { collaborationBoardApi } from '@/api/collaborationBoard'
import type { BoardColumn, BoardTask, BoardSummary, CreateBoardTaskRequest, BoardColumnType } from '@/types/collaborationBoard'

function generateMockColumns(): BoardColumn[] {
  return [
    { type: 'IDEA', label: '아이디어', color: '#8B5CF6', tasks: [{ id: 1, title: '숏폼 챌린지 시리즈', column: 'IDEA', priority: 'MEDIUM', status: 'TODO', assigneeName: '김편집', tags: ['숏폼', '챌린지'], attachments: 0, comments: 2, orderIndex: 0, createdAt: '2025-02-20', updatedAt: '2025-02-20' }] },
    { type: 'SCRIPTING', label: '대본 작성', color: '#3B82F6', tasks: [{ id: 2, title: '아이폰 16 리뷰', column: 'SCRIPTING', priority: 'HIGH', status: 'IN_PROGRESS', assigneeName: '이작가', tags: ['리뷰', '테크'], attachments: 1, comments: 5, orderIndex: 0, createdAt: '2025-02-18', updatedAt: '2025-02-24' }] },
    { type: 'FILMING', label: '촬영', color: '#10B981', tasks: [] },
    { type: 'EDITING', label: '편집', color: '#F97316', tasks: [{ id: 3, title: '봄 패션 하울', column: 'EDITING', priority: 'HIGH', status: 'IN_PROGRESS', assigneeName: '박편집', dueDate: '2025-02-28', tags: ['패션'], attachments: 3, comments: 8, orderIndex: 0, createdAt: '2025-02-15', updatedAt: '2025-02-25' }] },
    { type: 'REVIEW', label: '검토', color: '#EAB308', tasks: [] },
    { type: 'SCHEDULED', label: '예약됨', color: '#14B8A6', tasks: [{ id: 4, title: '집밥 레시피 #12', column: 'SCHEDULED', priority: 'LOW', status: 'DONE', assigneeName: '최영상', tags: ['음식', '레시피'], attachments: 2, comments: 3, orderIndex: 0, createdAt: '2025-02-10', updatedAt: '2025-02-25' }] },
    { type: 'PUBLISHED', label: '발행 완료', color: '#6B7280', tasks: [] },
  ]
}

function generateMockSummary(): BoardSummary {
  return { totalTasks: 15, completedTasks: 8, overdueTasks: 2, inProgressTasks: 4, avgCompletionTime: 5.2, teamMembers: 4, recentActivities: [{ id: 1, userId: 1, userName: '김편집', action: 'MOVED', taskId: 3, taskTitle: '봄 패션 하울', fromColumn: '촬영', toColumn: '편집', timestamp: '2025-02-25T14:30:00' }] }
}

export const useCollaborationBoardStore = defineStore('collaborationBoard', () => {
  const columns = ref<BoardColumn[]>([])
  const summary = ref<BoardSummary | null>(null)
  const isLoading = ref(false)

  const totalTasks = computed(() => columns.value.reduce((sum, col) => sum + col.tasks.length, 0))

  async function fetchBoard() {
    isLoading.value = true
    try { columns.value = await collaborationBoardApi.getBoard() } catch { columns.value = generateMockColumns() } finally { isLoading.value = false }
  }

  async function fetchSummary() {
    try { summary.value = await collaborationBoardApi.getSummary() } catch { summary.value = generateMockSummary() }
  }

  async function createTask(request: CreateBoardTaskRequest) {
    try {
      const created = await collaborationBoardApi.createTask(request)
      const col = columns.value.find((c) => c.type === request.column)
      if (col) col.tasks.push(created)
    } catch {
      const mock: BoardTask = { id: Date.now(), title: request.title, description: request.description, column: request.column, priority: request.priority, status: 'TODO', assigneeName: undefined, tags: request.tags ?? [], attachments: 0, comments: 0, orderIndex: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
      const col = columns.value.find((c) => c.type === request.column)
      if (col) col.tasks.push(mock)
    }
  }

  async function moveTask(taskId: number, toColumn: BoardColumnType) {
    try { await collaborationBoardApi.moveTask({ taskId, toColumn, orderIndex: 0 }) } catch { /* fallback */ }
    for (const col of columns.value) {
      const idx = col.tasks.findIndex((t) => t.id === taskId)
      if (idx !== -1) {
        const [task] = col.tasks.splice(idx, 1)
        task.column = toColumn
        const targetCol = columns.value.find((c) => c.type === toColumn)
        if (targetCol) targetCol.tasks.push(task)
        break
      }
    }
  }

  async function deleteTask(id: number) {
    try { await collaborationBoardApi.deleteTask(id) } catch { /* fallback */ }
    for (const col of columns.value) {
      col.tasks = col.tasks.filter((t) => t.id !== id)
    }
  }

  return { columns, summary, isLoading, totalTasks, fetchBoard, fetchSummary, createTask, moveTask, deleteTask }
})
