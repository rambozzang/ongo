import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MediaType, UploadProgress, ContentImage, Visibility } from '@/types/video'
import { videoApi } from '@/api/video'

export interface UploadMetadata {
  title: string
  description: string
  tags: string[]
  category: string
  visibility: Visibility
  thumbnailUrl: string
}

function createEmptyMetadata(): UploadMetadata {
  return { title: '', description: '', tags: [], category: '', visibility: 'PUBLIC', thumbnailUrl: '' }
}

const IMAGE_MIMES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'image/heic',
]

function isImageFile(file: File): boolean {
  return IMAGE_MIMES.includes(file.type)
}

export const useUploadStore = defineStore('upload', () => {
  const file = ref<File | null>(null)
  const imageFiles = ref<File[]>([])
  const mediaType = ref<MediaType>('VIDEO')
  const videoId = ref<number | null>(null)
  const contentImages = ref<ContentImage[]>([])
  const progress = ref<UploadProgress>({
    bytesUploaded: 0,
    bytesTotal: 0,
    percentage: 0,
    speed: 0,
    remainingSeconds: 0,
  })
  const uploading = ref(false)
  const uploadError = ref<string | null>(null)
  const uploadUrl = ref<string | null>(null)
  const xhrUpload = ref<XMLHttpRequest | null>(null)

  // Session state — persists across route navigation
  const step = ref(1)
  const metadata = ref<UploadMetadata>(createEmptyMetadata())

  const isUploading = computed(() => uploading.value)
  const isImage = computed(() => mediaType.value === 'IMAGE')
  const hasActiveSession = computed(() => file.value !== null || imageFiles.value.length > 0)

  let lastTimestamp = 0
  let lastBytesUploaded = 0

  async function startUpload(selectedFile: File) {
    if (isImageFile(selectedFile)) {
      return startImageUpload([selectedFile])
    }

    file.value = selectedFile
    mediaType.value = 'VIDEO'
    uploading.value = true
    uploadError.value = null
    lastTimestamp = Date.now()
    lastBytesUploaded = 0

    const baseUrl = (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE_URL || '/api/v1'
    const token = localStorage.getItem('accessToken')

    try {
      // Step 1: Init upload — videoId + presigned URL 발급
      const initResponse = await fetch(`${baseUrl}/videos/upload/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          filename: selectedFile.name,
          fileSize: selectedFile.size,
          contentType: selectedFile.type || 'video/mp4',
        }),
      })

      if (!initResponse.ok) {
        throw new Error(`업로드 초기화 실패: ${initResponse.status}`)
      }

      const initData = await initResponse.json()
      videoId.value = initData.data?.videoId
      const presignedUrl = initData.data?.uploadUrl

      if (!videoId.value) {
        throw new Error('videoId를 받지 못했습니다')
      }
      if (!presignedUrl) {
        throw new Error('presigned URL을 받지 못했습니다')
      }

      // Step 2: PUT 파일을 presigned URL로 직접 업로드
      const xhr = new XMLHttpRequest()
      xhrUpload.value = xhr

      xhr.upload.onprogress = (e: ProgressEvent) => {
        if (!e.lengthComputable) return

        const now = Date.now()
        const elapsed = (now - lastTimestamp) / 1000
        const bytesDelta = e.loaded - lastBytesUploaded

        const speed = elapsed > 0 ? bytesDelta / elapsed : 0
        const remaining = speed > 0 ? (e.total - e.loaded) / speed : 0

        progress.value = {
          bytesUploaded: e.loaded,
          bytesTotal: e.total,
          percentage: Math.round((e.loaded / e.total) * 100),
          speed,
          remainingSeconds: Math.round(remaining),
        }

        lastTimestamp = now
        lastBytesUploaded = e.loaded
      }

      xhr.onload = async () => {
        xhrUpload.value = null

        if (xhr.status >= 200 && xhr.status < 300) {
          // Step 3: 백엔드에 업로드 완료 알림
          try {
            const confirmRes = await fetch(`${baseUrl}/videos/${videoId.value}/upload/complete`, {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
              },
            })
            if (!confirmRes.ok) {
              const body = await confirmRes.json().catch(() => null)
              throw new Error(body?.message || `업로드 완료 확인 실패: ${confirmRes.status}`)
            }
            uploading.value = false
            uploadUrl.value = presignedUrl
            progress.value.percentage = 100
          } catch (err) {
            uploading.value = false
            uploadError.value = err instanceof Error ? err.message : '업로드 완료 확인 실패'
          }
        } else {
          uploading.value = false
          uploadError.value = `S3 업로드 실패: ${xhr.status}`
        }
      }

      xhr.onerror = () => {
        xhrUpload.value = null
        uploading.value = false
        uploadError.value = '네트워크 오류로 업로드 실패'
      }

      xhr.onabort = () => {
        xhrUpload.value = null
        uploading.value = false
      }

      xhr.open('PUT', presignedUrl)
      xhr.setRequestHeader('Content-Type', selectedFile.type || 'video/mp4')
      xhr.send(selectedFile)
    } catch (error) {
      uploading.value = false
      uploadError.value = error instanceof Error ? error.message : '업로드 실패'
    }
  }

  async function startImageUpload(files: File[]) {
    mediaType.value = 'IMAGE'
    imageFiles.value = files
    file.value = files[0]
    uploading.value = false
    uploadError.value = null
    progress.value = {
      bytesUploaded: 0,
      bytesTotal: 0,
      percentage: 100,
      speed: 0,
      remainingSeconds: 0,
    }
  }

  async function uploadImagesToServer(targetVideoId: number): Promise<ContentImage[]> {
    if (imageFiles.value.length === 0) return []

    uploading.value = true
    uploadError.value = null
    videoId.value = targetVideoId

    try {
      const totalSize = imageFiles.value.reduce((sum, f) => sum + f.size, 0)
      progress.value = {
        bytesUploaded: 0,
        bytesTotal: totalSize,
        percentage: 0,
        speed: 0,
        remainingSeconds: 0,
      }

      const result = await videoApi.uploadImages(targetVideoId, imageFiles.value)
      contentImages.value = result

      progress.value.percentage = 100
      progress.value.bytesUploaded = totalSize
      uploading.value = false
      return result
    } catch (error) {
      uploading.value = false
      uploadError.value = error instanceof Error ? error.message : '이미지 업로드 실패'
      throw error
    }
  }

  function pauseUpload() {
    xhrUpload.value?.abort()
    uploading.value = false
  }

  function resumeUpload() {
    // Presigned URL은 이어받기 불가 — 처음부터 재시작 (새 Video 레코드 생성)
    if (file.value && !uploading.value) {
      videoId.value = null
      uploadError.value = null
      startUpload(file.value)
    }
  }

  function resetUpload() {
    xhrUpload.value?.abort()
    file.value = null
    imageFiles.value = []
    mediaType.value = 'VIDEO'
    videoId.value = null
    contentImages.value = []
    uploading.value = false
    uploadError.value = null
    uploadUrl.value = null
    xhrUpload.value = null
    progress.value = {
      bytesUploaded: 0,
      bytesTotal: 0,
      percentage: 0,
      speed: 0,
      remainingSeconds: 0,
    }
    step.value = 1
    metadata.value = createEmptyMetadata()
  }

  return {
    file,
    imageFiles,
    mediaType,
    videoId,
    contentImages,
    progress,
    uploading,
    isUploading,
    isImage,
    uploadError,
    uploadUrl,
    step,
    metadata,
    hasActiveSession,
    startUpload,
    startImageUpload,
    uploadImagesToServer,
    pauseUpload,
    resumeUpload,
    resetUpload,
  }
})
