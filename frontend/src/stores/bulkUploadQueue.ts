import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UploadQueueItem, UploadQueueStats } from '@/types/uploadQueue'

const MAX_CONCURRENT = 3

export const useBulkUploadQueueStore = defineStore('bulkUploadQueue', () => {
  const items = ref<UploadQueueItem[]>([])
  const uploadIntervals = new Map<string, number>()

  // --- Getters ---

  const stats = computed<UploadQueueStats>(() => {
    const total = items.value.length
    const completed = items.value.filter((i) => i.status === 'completed').length
    const failed = items.value.filter((i) => i.status === 'failed').length
    const uploading = items.value.filter((i) => i.status === 'uploading').length
    const pending = items.value.filter((i) => i.status === 'pending').length
    const totalSize = items.value.reduce((sum, i) => sum + i.fileSize, 0)
    const uploadedSize = items.value.reduce((sum, i) => {
      return sum + Math.round((i.fileSize * i.progress) / 100)
    }, 0)

    return { total, completed, failed, uploading, pending, totalSize, uploadedSize }
  })

  const activeUploads = computed(() => {
    return items.value.filter((i) => i.status === 'uploading')
  })

  const hasActiveUploads = computed(() => {
    return activeUploads.value.length > 0
  })

  const overallProgress = computed(() => {
    if (items.value.length === 0) return 0
    const total = items.value.reduce((sum, i) => sum + i.progress, 0)
    return Math.round(total / items.value.length)
  })

  // --- Actions ---

  function addFiles(files: File[], platforms: string[] = ['YOUTUBE']) {
    for (const file of files) {
      const item: UploadQueueItem = {
        id: crypto.randomUUID(),
        file,
        fileName: file.name,
        fileSize: file.size,
        mimeType: file.type,
        progress: 0,
        status: 'pending',
        platforms: [...platforms],
        addedAt: new Date().toISOString(),
      }
      items.value.push(item)
    }
  }

  function removeItem(id: string) {
    const intervalId = uploadIntervals.get(id)
    if (intervalId) {
      clearInterval(intervalId)
      uploadIntervals.delete(id)
    }
    const index = items.value.findIndex((i) => i.id === id)
    if (index !== -1) {
      items.value.splice(index, 1)
    }
  }

  function pauseItem(id: string) {
    const item = items.value.find((i) => i.id === id)
    if (item && item.status === 'uploading') {
      item.status = 'paused'
      const intervalId = uploadIntervals.get(id)
      if (intervalId) {
        clearInterval(intervalId)
        uploadIntervals.delete(id)
      }
    }
  }

  function resumeItem(id: string) {
    const item = items.value.find((i) => i.id === id)
    if (item && item.status === 'paused') {
      item.status = 'pending'
      processQueue()
    }
  }

  function retryItem(id: string) {
    const item = items.value.find((i) => i.id === id)
    if (item && item.status === 'failed') {
      item.status = 'pending'
      item.progress = 0
      item.error = undefined
      processQueue()
    }
  }

  function reorderItem(id: string, newIndex: number) {
    const currentIndex = items.value.findIndex((i) => i.id === id)
    if (currentIndex === -1 || newIndex < 0 || newIndex >= items.value.length) return
    if (currentIndex === newIndex) return

    const arr = [...items.value]
    const [removed] = arr.splice(currentIndex, 1)
    arr.splice(newIndex, 0, removed)
    items.value = arr
  }

  function pauseAll() {
    items.value.forEach((item) => {
      if (item.status === 'uploading') {
        pauseItem(item.id)
      }
    })
  }

  function resumeAll() {
    items.value.forEach((item) => {
      if (item.status === 'paused') {
        item.status = 'pending'
      }
    })
    processQueue()
  }

  function clearCompleted() {
    items.value = items.value.filter((i) => i.status !== 'completed')
  }

  function cancelAll() {
    uploadIntervals.forEach((intervalId) => clearInterval(intervalId))
    uploadIntervals.clear()
    items.value = []
  }

  function startAll() {
    processQueue()
  }

  // --- Internal: real upload via Tus protocol ---

  function processQueue() {
    const currentlyUploading = items.value.filter((i) => i.status === 'uploading').length
    const slotsAvailable = MAX_CONCURRENT - currentlyUploading
    if (slotsAvailable <= 0) return

    const pendingItems = items.value.filter((i) => i.status === 'pending')
    for (let i = 0; i < Math.min(slotsAvailable, pendingItems.length); i++) {
      tusUpload(pendingItems[i].id)
    }
  }

  async function tusUpload(id: string) {
    const item = items.value.find((i) => i.id === id)
    if (!item) return

    item.status = 'uploading'
    item.startedAt = new Date().toISOString()
    item.error = undefined

    const baseUrl = (import.meta as any).env?.VITE_API_BASE_URL || '/api/v1'
    const token = localStorage.getItem('access_token') || ''

    try {
      // Step 1: Init upload
      const initResponse = await fetch(`${baseUrl}/videos/upload/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: JSON.stringify({
          filename: item.fileName,
          fileSize: item.fileSize,
          contentType: item.mimeType || 'video/mp4',
        }),
      })

      if (!initResponse.ok) {
        throw new Error(`Upload init failed: ${initResponse.status}`)
      }

      const initData = await initResponse.json()
      const videoId = initData.data?.id ?? initData.data?.videoId

      if (!videoId) {
        throw new Error('No videoId returned from init')
      }

      // Step 2: Create Tus session
      const tusUrl = `${baseUrl}/videos/upload/tus/${videoId}`
      const createResponse = await fetch(tusUrl, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Upload-Length': String(item.fileSize),
          'Tus-Resumable': '1.0.0',
          'Content-Type': 'application/offset+octet-stream',
        },
      })

      if (!createResponse.ok) {
        throw new Error(`Tus create failed: ${createResponse.status}`)
      }

      // Step 3: Upload in chunks
      const CHUNK_SIZE = 5 * 1024 * 1024 // 5MB
      let offset = 0

      while (offset < item.fileSize) {
        const currentItem = items.value.find((i) => i.id === id)
        if (!currentItem || currentItem.status !== 'uploading') return

        const chunk = item.file.slice(offset, offset + CHUNK_SIZE)
        const patchResponse = await fetch(tusUrl, {
          method: 'PATCH',
          headers: {
            'Authorization': `Bearer ${token}`,
            'Upload-Offset': String(offset),
            'Content-Type': 'application/offset+octet-stream',
            'Tus-Resumable': '1.0.0',
          },
          body: chunk,
        })

        if (!patchResponse.ok) {
          throw new Error(`Tus patch failed: ${patchResponse.status}`)
        }

        offset += chunk.size
        currentItem.progress = Math.min(100, Math.round((offset / item.fileSize) * 100))
      }

      // Upload complete
      item.progress = 100
      item.status = 'completed'
      item.completedAt = new Date().toISOString()
      processQueue()
    } catch (error: any) {
      item.status = 'failed'
      item.error = error.message || '업로드 중 오류가 발생했습니다.'
      processQueue()
    }
  }

  return {
    // State
    items,

    // Getters
    stats,
    activeUploads,
    hasActiveUploads,
    overallProgress,

    // Actions
    addFiles,
    removeItem,
    pauseItem,
    resumeItem,
    retryItem,
    reorderItem,
    pauseAll,
    resumeAll,
    clearCompleted,
    cancelAll,
    startAll,
  }
})
