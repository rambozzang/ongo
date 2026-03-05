import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { collaborationBoardApi } from '@/api/collaborationBoard'
import type { BoardColumn, BoardSummary, CreateBoardTaskRequest, BoardColumnType } from '@/types/collaborationBoard'

export const useCollaborationBoardStore = defineStore('collaborationBoard', () => {
  const columns = ref<BoardColumn[]>([])
  const summary = ref<BoardSummary | null>(null)
  const isLoading = ref(false)

  const totalTasks = computed(() => columns.value.reduce((sum, col) => sum + col.tasks.length, 0))

  async function fetchBoard() {
    isLoading.value = true
    try {
      columns.value = await collaborationBoardApi.getBoard()
    } catch {
      columns.value = []
    } finally {
      isLoading.value = false
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await collaborationBoardApi.getSummary()
    } catch {
      summary.value = null
    }
  }

  async function createTask(request: CreateBoardTaskRequest) {
    const created = await collaborationBoardApi.createTask(request)
    const col = columns.value.find((c) => c.type === request.column)
    if (col) col.tasks.push(created)
  }

  async function moveTask(taskId: number, toColumn: BoardColumnType) {
    try { await collaborationBoardApi.moveTask({ taskId, toColumn, orderIndex: 0 }) } catch { /* ignore */ }
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
    try { await collaborationBoardApi.deleteTask(id) } catch { /* ignore */ }
    for (const col of columns.value) {
      col.tasks = col.tasks.filter((t) => t.id !== id)
    }
  }

  return { columns, summary, isLoading, totalTasks, fetchBoard, fetchSummary, createTask, moveTask, deleteTask }
})
