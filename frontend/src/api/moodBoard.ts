import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  MoodBoard,
  MoodBoardItem,
  CreateMoodBoardRequest,
  MoodBoardSummary,
} from '@/types/moodBoard'

export const moodBoardApi = {
  getBoards() {
    return apiClient
      .get<ResData<MoodBoard[]>>('/mood-boards')
      .then(unwrapResponse)
  },

  getBoard(id: number) {
    return apiClient
      .get<ResData<MoodBoard>>(`/mood-boards/${id}`)
      .then(unwrapResponse)
  },

  createBoard(request: CreateMoodBoardRequest) {
    return apiClient
      .post<ResData<MoodBoard>>('/mood-boards', request)
      .then(unwrapResponse)
  },

  getItems(boardId: number) {
    return apiClient
      .get<ResData<MoodBoardItem[]>>(`/mood-boards/${boardId}/items`)
      .then(unwrapResponse)
  },

  deleteBoard(id: number) {
    return apiClient
      .delete<ResData<void>>(`/mood-boards/${id}`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<MoodBoardSummary>>('/mood-boards/summary')
      .then(unwrapResponse)
  },
}
