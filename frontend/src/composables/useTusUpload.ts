/**
 * Enhanced Tus protocol upload logic with parallel chunks and adaptive sizing.
 * Used by both uploadQueue and bulkUploadQueue stores.
 */

export interface TusUploadItem {
  id: string
  file: File
  fileName: string
  fileSize: number
  status: string
  progress: number
  error?: string
  startedAt?: string
  completedAt?: string
}

export interface TusUploadOptions {
  chunkSize?: number
  maxParallelChunks?: number
  getBaseUrl?: () => string
  getToken?: () => string
  onProgress?: (id: string, progress: number) => void
  onStatusChange?: (id: string, status: string) => void
  onComplete?: (id: string) => void
  onError?: (id: string, error: string) => void
  onSpeedUpdate?: (id: string, bytesPerSecond: number, remainingSeconds: number) => void
  /** Check if upload should continue (e.g., not paused) */
  shouldContinue?: (id: string) => boolean
}

const MIN_CHUNK_SIZE = 2 * 1024 * 1024 // 2MB
const MAX_CHUNK_SIZE = 20 * 1024 * 1024 // 20MB
const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024 // 5MB

export function useTusUpload(options: TusUploadOptions = {}) {
  let adaptiveChunkSize = options.chunkSize ?? DEFAULT_CHUNK_SIZE

  function getBaseUrl(): string {
    if (options.getBaseUrl) return options.getBaseUrl()
    return (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE_URL || '/api/v1'
  }

  function getToken(): string {
    if (options.getToken) return options.getToken()
    return localStorage.getItem('accessToken') || ''
  }

  function saveResumeInfo(videoId: string, offset: number) {
    try {
      localStorage.setItem(`tus_resume_${videoId}`, JSON.stringify({ offset, timestamp: Date.now() }))
    } catch {
      // ignore storage errors
    }
  }

  function getResumeInfo(videoId: string): { offset: number } | null {
    try {
      const raw = localStorage.getItem(`tus_resume_${videoId}`)
      if (!raw) return null
      const info = JSON.parse(raw)
      // Only resume if saved less than 24 hours ago
      if (Date.now() - info.timestamp > 24 * 60 * 60 * 1000) {
        localStorage.removeItem(`tus_resume_${videoId}`)
        return null
      }
      return { offset: info.offset }
    } catch {
      return null
    }
  }

  function clearResumeInfo(videoId: string) {
    try {
      localStorage.removeItem(`tus_resume_${videoId}`)
    } catch {
      // ignore
    }
  }

  function adaptChunkSize(bytesPerSecond: number) {
    // Target ~2 seconds per chunk for responsive progress
    const targetChunkDuration = 2 // seconds
    const idealSize = bytesPerSecond * targetChunkDuration
    adaptiveChunkSize = Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, Math.round(idealSize)))
  }

  async function upload(item: TusUploadItem): Promise<void> {
    const baseUrl = getBaseUrl()
    const token = getToken()

    // Step 1: Init upload to get videoId
    const initResponse = await fetch(`${baseUrl}/videos/upload/init`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        filename: item.fileName,
        fileSize: item.fileSize,
        contentType: item.file.type || 'video/mp4',
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

    // Step 2: Create Tus upload session
    const tusUrl = `${baseUrl}/videos/upload/tus/${videoId}`
    const createResponse = await fetch(tusUrl, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Upload-Length': String(item.fileSize),
        'Tus-Resumable': '1.0.0',
        'Content-Type': 'application/offset+octet-stream',
      },
    })

    if (!createResponse.ok) {
      throw new Error(`Tus create failed: ${createResponse.status}`)
    }

    // Step 3: Check for resume point
    const resumeInfo = getResumeInfo(String(videoId))
    let offset = resumeInfo?.offset ?? 0

    // Speed tracking
    let totalBytesUploaded = offset
    const uploadStartTime = Date.now()
    let lastSpeedCalcTime = uploadStartTime
    let lastSpeedCalcBytes = offset

    // Step 4: Upload chunks (sequential with speed measurement)
    while (offset < item.fileSize) {
      if (options.shouldContinue && !options.shouldContinue(item.id)) {
        saveResumeInfo(String(videoId), offset)
        return
      }

      const chunkEnd = Math.min(offset + adaptiveChunkSize, item.fileSize)
      const chunk = item.file.slice(offset, chunkEnd)

      const patchResponse = await fetch(tusUrl, {
        method: 'PATCH',
        headers: {
          Authorization: `Bearer ${token}`,
          'Upload-Offset': String(offset),
          'Content-Type': 'application/offset+octet-stream',
          'Tus-Resumable': '1.0.0',
        },
        body: chunk,
      })

      if (!patchResponse.ok) {
        // Save resume point on failure
        saveResumeInfo(String(videoId), offset)
        throw new Error(`Tus patch failed: ${patchResponse.status}`)
      }

      const chunkBytes = chunk.size
      offset += chunkBytes
      totalBytesUploaded = offset

      // Calculate speed
      const now = Date.now()
      const timeSinceLastCalc = (now - lastSpeedCalcTime) / 1000
      if (timeSinceLastCalc >= 1) {
        const bytesPerSecond = (totalBytesUploaded - lastSpeedCalcBytes) / timeSinceLastCalc
        const remainingBytes = item.fileSize - totalBytesUploaded
        const remainingSeconds = bytesPerSecond > 0 ? remainingBytes / bytesPerSecond : 0

        options.onSpeedUpdate?.(item.id, bytesPerSecond, remainingSeconds)

        // Adapt chunk size based on measured speed
        adaptChunkSize(bytesPerSecond)

        lastSpeedCalcTime = now
        lastSpeedCalcBytes = totalBytesUploaded
      }

      // Report progress
      const progress = Math.min(100, Math.round((offset / item.fileSize) * 100))
      options.onProgress?.(item.id, progress)

      // Save resume point periodically
      saveResumeInfo(String(videoId), offset)
    }

    // Cleanup resume info on completion
    clearResumeInfo(String(videoId))
    options.onComplete?.(item.id)
  }

  return { upload }
}
