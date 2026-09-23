export type UploadQueueItemStatus = 'pending' | 'uploading' | 'paused' | 'completed' | 'failed'

export interface UploadQueueItem {
  id: string
  file: File
  fileName: string
  fileSize: number
  mimeType: string
  progress: number // 0-100
  status: UploadQueueItemStatus
  platforms: string[]
  error?: string
  /**
   * 서버가 내려준 안정 코드(`PLAN_LIMIT_EXCEEDED` 등). 문구가 아니라 이 값으로
   * "업그레이드로 풀리는 실패인가" 를 판단한다.
   */
  errorCode?: string
  addedAt: string
  startedAt?: string
  completedAt?: string
}

export interface UploadQueueStats {
  total: number
  completed: number
  failed: number
  uploading: number
  pending: number
  totalSize: number
  uploadedSize: number
}
