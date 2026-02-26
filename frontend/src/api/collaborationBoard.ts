import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  BoardTask,
  BoardColumn,
  BoardActivity,
  BoardSummary,
  CreateBoardTaskRequest,
  MoveBoardTaskRequest,
} from '@/types/collaborationBoard'

export const collaborationBoardApi = {
  getBoard: () =>
    apiClient.get<ResData<BoardColumn[]>>('/board/columns').then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<BoardSummary>>('/board/summary').then(unwrapResponse),

  createTask: (request: CreateBoardTaskRequest) =>
    apiClient.post<ResData<BoardTask>>('/board/tasks', request).then(unwrapResponse),

  updateTask: (id: number, data: Partial<BoardTask>) =>
    apiClient.put<ResData<BoardTask>>(`/board/tasks/${id}`, data).then(unwrapResponse),

  moveTask: (request: MoveBoardTaskRequest) =>
    apiClient.post<ResData<BoardTask>>('/board/tasks/move', request).then(unwrapResponse),

  deleteTask: (id: number) =>
    apiClient.delete<ResData<void>>(`/board/tasks/${id}`).then(unwrapResponse),

  getActivities: (limit?: number) =>
    apiClient.get<ResData<BoardActivity[]>>('/board/activities', { params: { limit } }).then(unwrapResponse),
}
