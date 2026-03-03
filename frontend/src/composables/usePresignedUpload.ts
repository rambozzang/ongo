/**
 * Presigned URL 기반 직접 업로드 컴포저블.
 * 클라이언트 → S3/MinIO 직접 PUT (서버 대역폭 미사용).
 * 기존 Tus 업로드를 대체하며, XHR 기반 진행률 추적을 제공합니다.
 */

export interface PresignedUploadItem {
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

export interface PresignedUploadOptions {
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

export function usePresignedUpload(options: PresignedUploadOptions = {}) {
  let currentXhr: XMLHttpRequest | null = null

  function getBaseUrl(): string {
    if (options.getBaseUrl) return options.getBaseUrl()
    return (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE_URL || '/api/v1'
  }

  function getToken(): string {
    if (options.getToken) return options.getToken()
    return localStorage.getItem('accessToken') || ''
  }

  async function upload(item: PresignedUploadItem): Promise<void> {
    const baseUrl = getBaseUrl()
    const token = getToken()

    // Step 1: Init upload — videoId + presigned URL 발급
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
      const errorBody = await initResponse.json().catch(() => null)
      const message = errorBody?.message || `업로드 초기화 실패: ${initResponse.status}`
      throw new Error(message)
    }

    const initData = await initResponse.json()
    const videoId = initData.data?.id ?? initData.data?.videoId
    const presignedUrl = initData.data?.uploadUrl

    if (!videoId) {
      throw new Error('videoId를 받지 못했습니다')
    }
    if (!presignedUrl) {
      throw new Error('presigned URL을 받지 못했습니다')
    }

    // Step 2: PUT 파일을 presigned URL로 직접 업로드
    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      currentXhr = xhr

      // Speed tracking
      let lastCalcTime = Date.now()
      let lastCalcBytes = 0

      xhr.upload.onprogress = (e: ProgressEvent) => {
        if (!e.lengthComputable) return

        // Pause 체크
        if (options.shouldContinue && !options.shouldContinue(item.id)) {
          xhr.abort()
          return
        }

        const progress = Math.min(99, Math.round((e.loaded / e.total) * 100))
        options.onProgress?.(item.id, progress)

        // Speed 계산
        const now = Date.now()
        const timeSinceLastCalc = (now - lastCalcTime) / 1000
        if (timeSinceLastCalc >= 1) {
          const bytesPerSecond = (e.loaded - lastCalcBytes) / timeSinceLastCalc
          const remainingBytes = e.total - e.loaded
          const remainingSeconds = bytesPerSecond > 0 ? remainingBytes / bytesPerSecond : 0
          options.onSpeedUpdate?.(item.id, bytesPerSecond, remainingSeconds)
          lastCalcTime = now
          lastCalcBytes = e.loaded
        }
      }

      xhr.onload = () => {
        currentXhr = null
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve()
        } else {
          reject(new Error(`S3 업로드 실패: ${xhr.status}`))
        }
      }

      xhr.onerror = () => {
        currentXhr = null
        reject(new Error('네트워크 오류로 업로드 실패'))
      }

      xhr.onabort = () => {
        currentXhr = null
        // shouldContinue가 false인 경우 (일시정지) — 에러 아님
        if (options.shouldContinue && !options.shouldContinue(item.id)) {
          resolve()
          return
        }
        reject(new Error('업로드가 취소되었습니다'))
      }

      xhr.open('PUT', presignedUrl)
      xhr.setRequestHeader('Content-Type', item.file.type || 'video/mp4')
      xhr.send(item.file)
    })

    // Pause로 인한 중단인 경우 완료 처리 스킵
    if (options.shouldContinue && !options.shouldContinue(item.id)) {
      return
    }

    // Step 3: 백엔드에 업로드 완료 알림
    const confirmResponse = await fetch(`${baseUrl}/videos/${videoId}/upload/complete`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
    })

    if (!confirmResponse.ok) {
      const errorBody = await confirmResponse.json().catch(() => null)
      const message = errorBody?.message || `업로드 완료 확인 실패: ${confirmResponse.status}`
      throw new Error(message)
    }

    options.onProgress?.(item.id, 100)
    options.onComplete?.(item.id)
  }

  function abort(): void {
    currentXhr?.abort()
    currentXhr = null
  }

  return { upload, abort }
}
