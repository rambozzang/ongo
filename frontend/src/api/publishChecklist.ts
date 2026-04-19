import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface ChecklistItem {
  key: string
  passed: boolean
  suggestion: string | null
}

export interface PublishChecklistResponse {
  videoId: number
  ready: boolean
  score: number
  items: ChecklistItem[]
}

export const publishChecklistApi = {
  check(videoId: number) {
    return apiClient
      .post<ResData<PublishChecklistResponse>>(`/videos/${videoId}/publish-checklist`)
      .then(unwrapResponse)
  },
}
