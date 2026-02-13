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
