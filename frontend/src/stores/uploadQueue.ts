import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Platform } from '@/types/channel'
import type { MediaType, PlatformPublishConfig } from '@/types/video'
import { usePresignedUpload } from '@/composables/usePresignedUpload'
import { useNotificationStore } from '@/stores/notification'

/**
 * `needs-config` — 파일 전송은 끝났지만 게시할 플랫폼 설정이 없어 어디에도 게시되지
 * 않은 상태. 다중 파일을 한 번에 넣는 경로는 플랫폼을 고를 기회가 없어 이 상태로 끝난다.
 * 예전에는 이 경우도 `completed` 로 칠해서, 실제로는 아무 데도 안 올라갔는데 사용자는
 * 게시된 줄 알았다.
 */
export type QueueItemStatus =
  | 'queued'
  | 'uploading'
  | 'processing'
  | 'completed'
  | 'needs-config'
  | 'failed'
  | 'paused'

export interface PlatformProgress {
  status: 'pending' | 'uploading' | 'processing' | 'completed' | 'failed'
  progress: number
}

export interface QueueItem {
  id: string
  file: File
  fileName: string
  fileSize: number
  title: string
  mediaType: MediaType
  platforms: Platform[]
  status: QueueItemStatus
  progress: number
  error?: string
  addedAt: string
  startedAt?: string
  completedAt?: string
  thumbnail?: string
  platformProgress: Record<Platform, PlatformProgress>
  metadata?: {
    title: string
    description: string
    tags: string[]
    category?: string
  }
  platformConfigs?: PlatformPublishConfig[]
  /** 업로드로 생성된 영상 id. `needs-config` 상태에서 상세 화면으로 안내할 때 쓴다. */
  videoId?: number
}

const VIDEO_EXTENSIONS = ['.mp4', '.mov', '.avi', '.mkv', '.webm']
const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.heic']
const ALLOWED_EXTENSIONS = [...VIDEO_EXTENSIONS, ...IMAGE_EXTENSIONS]
const VIDEO_MIMES = [
  'video/mp4',
  'video/quicktime',
  'video/x-msvideo',
  'video/x-matroska',
  'video/webm',
]
const IMAGE_MIMES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'image/heic',
]
const ALLOWED_MIMES = [...VIDEO_MIMES, ...IMAGE_MIMES]
const VIDEO_MAX_SIZE = 2 * 1024 * 1024 * 1024 // 2GB
const IMAGE_MAX_SIZE = 50 * 1024 * 1024 // 50MB
const MAX_CONCURRENT = 2

function detectMediaType(file: File): MediaType {
  if (IMAGE_MIMES.includes(file.type)) return 'IMAGE'
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (IMAGE_EXTENSIONS.includes(ext)) return 'IMAGE'
  return 'VIDEO'
}

export const useUploadQueueStore = defineStore('uploadQueue', () => {
  const queue = ref<QueueItem[]>([])
  const uploadIntervals = new Map<string, number>()
  /**
   * 진행 중인 업로드의 중단 핸들러. 큐에서 항목을 빼는 것만으로는 XHR 이 멈추지 않아
   * 취소한 영상이 그대로 플랫폼에 게시되던 문제가 있었다.
   */
  const abortHandlers = new Map<string, () => void>()
  const isProcessing = ref(false)

  const totalProgress = computed(() => {
    if (queue.value.length === 0) return 0
    const total = queue.value.reduce((sum, item) => sum + item.progress, 0)
    return Math.round(total / queue.value.length)
  })

  const queuedCount = computed(() => {
    return queue.value.filter((item) => item.status === 'queued').length
  })

  const uploadingCount = computed(() => {
    return queue.value.filter((item) => item.status === 'uploading' || item.status === 'processing').length
  })

  const completedCount = computed(() => {
    return queue.value.filter((item) => item.status === 'completed').length
  })

  const failedCount = computed(() => {
    return queue.value.filter((item) => item.status === 'failed').length
  })

  const overallProgress = computed(() => totalProgress.value)

  // Legacy computed properties for backward compatibility
  const activeCount = computed(() => uploadingCount.value)
  const pendingCount = computed(() => queuedCount.value)
  const isUploading = computed(() => uploadingCount.value > 0)

  /**
   * Generate thumbnail from file (video or image)
   */
  async function generateThumbnail(file: File): Promise<string | undefined> {
    const mediaType = detectMediaType(file)

    if (mediaType === 'IMAGE') {
      return new Promise((resolve) => {
        const url = URL.createObjectURL(file)
        const img = new Image()
        img.onload = () => {
          const canvas = document.createElement('canvas')
          const maxDim = 200
          const scale = Math.min(maxDim / img.width, maxDim / img.height, 1)
          canvas.width = img.width * scale
          canvas.height = img.height * scale
          const ctx = canvas.getContext('2d')
          if (ctx) {
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
            URL.revokeObjectURL(url)
            resolve(canvas.toDataURL('image/jpeg', 0.7))
          } else {
            URL.revokeObjectURL(url)
            resolve(undefined)
          }
        }
        img.onerror = () => {
          URL.revokeObjectURL(url)
          resolve(undefined)
        }
        img.src = url
      })
    }

    return new Promise((resolve) => {
      const video = document.createElement('video')
      const canvas = document.createElement('canvas')
      const url = URL.createObjectURL(file)

      video.preload = 'metadata'
      video.muted = true
      video.playsInline = true

      video.onloadeddata = () => {
        // Seek to 1 second or 10% of duration, whichever is smaller
        const seekTime = Math.min(1, video.duration * 0.1)
        video.currentTime = seekTime
      }

      video.onseeked = () => {
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        const ctx = canvas.getContext('2d')
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
          const thumbnail = canvas.toDataURL('image/jpeg', 0.7)
          URL.revokeObjectURL(url)
          resolve(thumbnail)
        } else {
          URL.revokeObjectURL(url)
          resolve(undefined)
        }
      }

      video.onerror = () => {
        URL.revokeObjectURL(url)
        resolve(undefined)
      }

      video.src = url
    })
  }

  /**
   * Add item to queue with full metadata
   */
  function addToQueue(item: Omit<QueueItem, 'id' | 'addedAt' | 'progress' | 'status' | 'platformProgress' | 'mediaType'> & { mediaType?: MediaType }) {
    const platformProgress: Record<string, PlatformProgress> = {}
    item.platforms.forEach((platform) => {
      platformProgress[platform] = {
        status: 'pending',
        progress: 0,
      }
    })

    const queueItem: QueueItem = {
      ...item,
      mediaType: item.mediaType ?? detectMediaType(item.file),
      id: crypto.randomUUID(),
      status: 'queued',
      progress: 0,
      addedAt: new Date().toISOString(),
      platformProgress: platformProgress as Record<Platform, PlatformProgress>,
    }

    queue.value.push(queueItem)
  }

  /**
   * Validate file and add to queue (backward compatibility)
   */
  async function addFiles(files: File[]) {
    for (const file of files) {
      // Validate extension
      const ext = '.' + file.name.split('.').pop()?.toLowerCase()
      if (!ALLOWED_EXTENSIONS.includes(ext)) {
        useNotificationStore().error(`지원하지 않는 파일 형식입니다: ${file.name}`)
        continue
      }

      // Validate MIME type
      if (!ALLOWED_MIMES.includes(file.type) && file.type !== '') {
        useNotificationStore().error(`유효하지 않은 파일 형식입니다: ${file.name}`)
        continue
      }

      // Validate size based on media type
      const fileMediaType = detectMediaType(file)
      const maxSize = fileMediaType === 'IMAGE' ? IMAGE_MAX_SIZE : VIDEO_MAX_SIZE
      if (file.size > maxSize) {
        const limitLabel = fileMediaType === 'IMAGE' ? '50MB' : '2GB'
        useNotificationStore().error(`파일 크기가 ${limitLabel}를 초과합니다: ${file.name}`)
        continue
      }

      // Generate thumbnail
      const thumbnail = await generateThumbnail(file)

      // Add to queue
      const item: QueueItem = {
        id: crypto.randomUUID(),
        file,
        fileName: file.name,
        fileSize: file.size,
        title: file.name.replace(/\.[^/.]+$/, ''),
        mediaType: fileMediaType,
        platforms: ['YOUTUBE'] as Platform[],
        status: 'queued',
        progress: 0,
        addedAt: new Date().toISOString(),
        thumbnail,
        platformProgress: {
          YOUTUBE: { status: 'pending', progress: 0 },
        } as Record<Platform, PlatformProgress>,
      }

      queue.value.push(item)
    }
  }

  /**
   * Remove item from queue
   */
  function removeFromQueue(id: string) {
    const index = queue.value.findIndex((item) => item.id === id)
    if (index !== -1) {
      // 진행 중이면 실제로 중단한다. 목록에서 빼기만 하면 업로드가 끝까지 진행돼
      // 사용자가 취소한 영상이 플랫폼에 게시된다.
      abortHandlers.get(id)?.()
      abortHandlers.delete(id)

      // Clear interval if exists
      const intervalId = uploadIntervals.get(id)
      if (intervalId) {
        clearInterval(intervalId)
        uploadIntervals.delete(id)
      }
      queue.value.splice(index, 1)
    }
  }

  /**
   * Legacy alias
   */
  function removeItem(id: string) {
    removeFromQueue(id)
  }

  /**
   * Retry failed item
   */
  function retryItem(id: string) {
    const item = queue.value.find((item) => item.id === id)
    if (item && item.status === 'failed') {
      item.status = 'queued'
      item.progress = 0
      item.error = undefined
    }
  }

  /**
   * Clear all completed items
   */
  function clearCompleted() {
    queue.value = queue.value.filter((item) => item.status !== 'completed')
  }

  /**
   * Clear entire queue
   */
  function clearAll() {
    // 진행 중인 업로드를 실제로 중단한다. 큐만 비우면 백그라운드 XHR 이 계속 돌아
    // 화면에서 사라진 영상이 그대로 게시된다.
    abortHandlers.forEach((abortUpload) => abortUpload())
    abortHandlers.clear()

    // Clear all intervals
    uploadIntervals.forEach((intervalId) => clearInterval(intervalId))
    uploadIntervals.clear()
    queue.value = []
  }

  /**
   * Pause uploading item
   */
  function pauseItem(id: string) {
    const item = queue.value.find((item) => item.id === id)
    if (item && item.status === 'uploading') {
      item.status = 'paused'
      const intervalId = uploadIntervals.get(id)
      if (intervalId) {
        clearInterval(intervalId)
        uploadIntervals.delete(id)
      }
    }
  }

  /**
   * Resume paused item
   */
  function resumeItem(id: string) {
    const item = queue.value.find((item) => item.id === id)
    if (item && item.status === 'paused') {
      item.status = 'queued'
      if (isProcessing.value) {
        startNextPending()
      }
    }
  }

  /**
   * Reorder queue items
   */
  function reorderQueue(fromIndex: number, toIndex: number) {
    if (fromIndex < 0 || fromIndex >= queue.value.length || toIndex < 0 || toIndex >= queue.value.length) {
      return
    }
    const items = [...queue.value]
    const [removed] = items.splice(fromIndex, 1)
    items.splice(toIndex, 0, removed)
    queue.value = items
  }

  /**
   * Pause all uploading items
   */
  function pauseAll() {
    queue.value.forEach((item) => {
      if (item.status === 'uploading' || item.status === 'processing') {
        pauseItem(item.id)
      }
    })
    isProcessing.value = false
  }

  /**
   * Resume all paused items
   */
  function resumeAll() {
    queue.value.forEach((item) => {
      if (item.status === 'paused') {
        item.status = 'queued'
      }
    })
    startProcessing()
  }

  /**
   * Upload file via Presigned URL
   */
  async function presignedUpload(id: string) {
    const item = queue.value.find((item) => item.id === id)
    if (!item) return

    item.status = 'uploading'
    item.progress = 0
    item.error = undefined
    item.startedAt = new Date().toISOString()

    // Update platform progress
    Object.keys(item.platformProgress).forEach((platform) => {
      item.platformProgress[platform as Platform].status = 'uploading'
      item.platformProgress[platform as Platform].progress = 0
    })

    const { upload, abort } = usePresignedUpload({
      shouldContinue: (itemId) => {
        const current = queue.value.find((i) => i.id === itemId)
        // 큐에서 사라진 항목(취소됨)은 중단한다. 예전에는 undefined !== 'paused' 가 참이라
        // 취소한 업로드가 끝까지 진행되어 그대로 게시됐다.
        if (!current) return false
        return current.status !== 'paused'
      },
      onProgress: (itemId, progress) => {
        const current = queue.value.find((i) => i.id === itemId)
        if (!current) return
        current.progress = progress
        Object.keys(current.platformProgress).forEach((platform) => {
          current.platformProgress[platform as Platform].progress = progress
        })
        // Switch to processing at 80%
        if (progress >= 80 && current.status === 'uploading') {
          current.status = 'processing'
          Object.keys(current.platformProgress).forEach((platform) => {
            current.platformProgress[platform as Platform].status = 'processing'
          })
        }
      },
    })

    abortHandlers.set(id, abort)

    try {
      const uploadedVideoId = await upload(item)
      if (uploadedVideoId === null) return

      item.progress = 100
      item.completedAt = new Date().toISOString()
      item.videoId = uploadedVideoId ?? undefined

      // 게시 설정이 없으면 파일만 올라갔을 뿐 어떤 플랫폼에도 게시되지 않았다.
      // 이를 완료로 표시하면 사용자가 게시된 줄 오해한다.
      const published = (item.platformConfigs?.length ?? 0) > 0
      item.status = published ? 'completed' : 'needs-config'

      Object.keys(item.platformProgress).forEach((platform) => {
        item.platformProgress[platform as Platform].status = published ? 'completed' : 'pending'
        item.platformProgress[platform as Platform].progress = published ? 100 : 0
      })

      startNextPending()
    } catch (error: unknown) {
      item.status = 'failed'
      item.error = error instanceof Error ? error.message : '업로드 중 오류가 발생했습니다.'
      startNextPending()
    } finally {
      abortHandlers.delete(id)
    }
  }

  /**
   * Start next pending item if concurrent limit allows
   */
  function startNextPending() {
    if (activeCount.value >= MAX_CONCURRENT) return

    const nextItem = queue.value.find((item) => item.status === 'queued')
    if (nextItem) {
      presignedUpload(nextItem.id)
    }
  }

  /**
   * Start processing queue
   */
  function startProcessing() {
    isProcessing.value = true
    const queued = queue.value.filter((item) => item.status === 'queued')
    const slotsAvailable = MAX_CONCURRENT - activeCount.value

    for (let i = 0; i < Math.min(slotsAvailable, queued.length); i++) {
      presignedUpload(queued[i].id)
    }
  }

  /**
   * Start uploading pending items (respects concurrent limit) - legacy
   */
  function startUpload() {
    startProcessing()
  }

  return {
    // State
    queue,
    isProcessing,

    // Computed
    totalProgress,
    queuedCount,
    uploadingCount,
    completedCount,
    failedCount,
    overallProgress,

    // Legacy computed (backward compatibility)
    activeCount,
    pendingCount,
    isUploading,

    // Actions
    addToQueue,
    addFiles,
    removeFromQueue,
    removeItem, // legacy
    retryItem,
    clearCompleted,
    clearAll,
    pauseItem,
    resumeItem,
    reorderQueue,
    pauseAll,
    resumeAll,
    startProcessing,
    presignedUpload,
    startUpload, // legacy
  }
})
