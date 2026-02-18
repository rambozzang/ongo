import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MediaType, UploadProgress, ContentImage, Visibility } from '@/types/video'
import { videoApi } from '@/api/video'
import * as tus from 'tus-js-client'

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
  const tusUpload = ref<tus.Upload | null>(null)

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
      // Step 1: Init upload to get videoId and tus endpoint
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

      if (!videoId.value) {
        throw new Error('videoId를 받지 못했습니다')
      }

      // Step 2: Create Tus upload session (POST to register metadata on server)
      const tusEndpoint = `${baseUrl}/videos/upload/tus/${videoId.value}`
      const createResponse = await fetch(tusEndpoint, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Tus-Resumable': '1.0.0',
          'Upload-Length': String(selectedFile.size),
          'Upload-Metadata': `filename ${btoa(selectedFile.name)},filetype ${btoa(selectedFile.type || 'video/mp4')}`,
        },
      })

      if (!createResponse.ok) {
        throw new Error(`Tus 세션 생성 실패: ${createResponse.status}`)
      }

      // Step 3: Start tus-js-client with uploadUrl (skips creation, goes straight to PATCH)
      const upload = new tus.Upload(selectedFile, {
        uploadUrl: tusEndpoint,
        retryDelays: [0, 1000, 3000, 5000],
        chunkSize: 5 * 1024 * 1024,
        metadata: {
          filename: selectedFile.name,
          filetype: selectedFile.type,
        },
        headers: {
          Authorization: `Bearer ${token}`,
        },
        onError(error) {
          uploading.value = false
          uploadError.value = error.message
        },
        onProgress(bytesUploaded, bytesTotal) {
          const now = Date.now()
          const elapsed = (now - lastTimestamp) / 1000
          const bytesDelta = bytesUploaded - lastBytesUploaded

          const speed = elapsed > 0 ? bytesDelta / elapsed : 0
          const remaining = speed > 0 ? (bytesTotal - bytesUploaded) / speed : 0

          progress.value = {
            bytesUploaded,
            bytesTotal,
            percentage: Math.round((bytesUploaded / bytesTotal) * 100),
            speed,
            remainingSeconds: Math.round(remaining),
          }

          lastTimestamp = now
          lastBytesUploaded = bytesUploaded
        },
        onSuccess() {
          uploading.value = false
          uploadUrl.value = upload.url ?? null
          progress.value.percentage = 100
        },
      })

      tusUpload.value = upload
      upload.start()
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
    tusUpload.value?.abort()
    uploading.value = false
  }

  function resumeUpload() {
    if (tusUpload.value) {
      uploading.value = true
      tusUpload.value.start()
    }
  }

  function resetUpload() {
    tusUpload.value?.abort()
    file.value = null
    imageFiles.value = []
    mediaType.value = 'VIDEO'
    videoId.value = null
    contentImages.value = []
    uploading.value = false
    uploadError.value = null
    uploadUrl.value = null
    tusUpload.value = null
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
