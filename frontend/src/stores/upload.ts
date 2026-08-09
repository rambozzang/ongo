import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MediaType, UploadProgress, ContentImage, Visibility, PlatformPublishConfig } from '@/types/video'
import { videoApi } from '@/api/video'
import { usePresignedUpload } from '@/composables/usePresignedUpload'

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
  // 게시 중인 업로드를 중단할 수 있는지 — streamPublish/cloudPublish가 핸들러를 등록한다
  const canAbort = ref(false)

  // Session state — persists across route navigation
  const step = ref(1)
  const metadata = ref<UploadMetadata>(createEmptyMetadata())

  const isUploading = computed(() => uploading.value)
  const isImage = computed(() => mediaType.value === 'IMAGE')
  const hasActiveSession = computed(() => file.value !== null || imageFiles.value.length > 0)

  let lastTimestamp = 0
  let lastBytesUploaded = 0

  // 진행 중인 업로드 취소 핸들러 — 경로별(stream/cloud)로 등록·해제한다
  let abortHandler: (() => void) | null = null

  function registerAbort(handler: (() => void) | null) {
    abortHandler = handler
    canAbort.value = handler !== null
  }

  function abortPublish() {
    abortHandler?.()
  }

  async function startUpload(selectedFile: File) {
    if (isImageFile(selectedFile)) {
      return startImageUpload([selectedFile])
    }

    file.value = selectedFile
    mediaType.value = 'VIDEO'
    uploading.value = false
    uploadError.value = null
    // 실제 업로드는 3단계 stream-publish/cloud-publish에서 수행 — 여기서는 선택된 파일 크기만 기록
    progress.value = {
      bytesUploaded: 0,
      bytesTotal: selectedFile.size,
      percentage: 0,
      speed: 0,
      remainingSeconds: 0,
    }
  }

  async function startImageUpload(files: File[]) {
    mediaType.value = 'IMAGE'
    imageFiles.value = files
    file.value = files[0]
    uploading.value = false
    uploadError.value = null
    // 실제 업로드는 uploadImagesToServer에서 수행 — 여기서는 선택된 파일 크기만 기록
    progress.value = {
      bytesUploaded: 0,
      bytesTotal: files.reduce((sum, f) => sum + f.size, 0),
      percentage: 0,
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

  async function streamPublish(
    targetFile: File,
    publishMetadata: UploadMetadata,
    platformConfigs: PlatformPublishConfig[],
  ): Promise<{ videoId: number }> {
    uploading.value = true
    uploadError.value = null
    progress.value = { bytesUploaded: 0, bytesTotal: targetFile.size, percentage: 0, speed: 0, remainingSeconds: 0 }
    lastTimestamp = Date.now()
    lastBytesUploaded = 0

    const baseUrl = (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE_URL || '/api/v1'
    const token = localStorage.getItem('accessToken')

    const metadataJson = JSON.stringify({
      title: publishMetadata.title,
      description: publishMetadata.description || null,
      tags: publishMetadata.tags,
      category: publishMetadata.category || null,
      thumbnailUrl: publishMetadata.thumbnailUrl || null,
      platforms: platformConfigs.map(pc => ({
        platform: pc.platform,
        title: pc.title,
        description: pc.description,
        tags: pc.tags,
        visibility: pc.visibility,
        scheduledAt: (pc as PlatformPublishConfig & { scheduledAt?: string }).scheduledAt || null,
      })),
    })

    const formData = new FormData()
    formData.append('metadata', new Blob([metadataJson], { type: 'application/json' }))
    formData.append('file', targetFile)

    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      registerAbort(() => xhr.abort())

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

      xhr.onload = () => {
        uploading.value = false
        registerAbort(null)
        if (xhr.status === 202 || xhr.status === 200) {
          try {
            const data = JSON.parse(xhr.responseText)
            const vid = data?.data?.videoId
            if (vid) {
              videoId.value = vid
              progress.value.percentage = 100
              resolve({ videoId: vid })
            } else {
              reject(new Error('videoId를 받지 못했습니다'))
            }
          } catch {
            reject(new Error('응답 파싱 실패'))
          }
        } else {
          try {
            const body = JSON.parse(xhr.responseText)
            reject(new Error(body?.message || body?.error || `업로드 실패: ${xhr.status}`))
          } catch {
            reject(new Error(`업로드 실패: ${xhr.status}`))
          }
        }
      }

      xhr.onerror = () => {
        uploading.value = false
        registerAbort(null)
        reject(new Error('네트워크 오류'))
      }

      xhr.onabort = () => {
        uploading.value = false
        registerAbort(null)
        reject(new Error('업로드가 취소되었습니다'))
      }

      xhr.open('POST', `${baseUrl}/videos/stream-publish`)
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      xhr.send(formData)
    })
  }

  async function cloudPublish(
    targetFile: File,
    publishMetadata: UploadMetadata,
    platformConfigs: PlatformPublishConfig[],
  ): Promise<{ videoId: number }> {
    uploading.value = true
    uploadError.value = null
    progress.value = { bytesUploaded: 0, bytesTotal: targetFile.size, percentage: 0, speed: 0, remainingSeconds: 0 }

    const { upload, abort } = usePresignedUpload({
      onProgress: (_id, percentage) => {
        progress.value = {
          ...progress.value,
          percentage,
          bytesUploaded: Math.round(targetFile.size * percentage / 100),
        }
      },
      onSpeedUpdate: (_id, bytesPerSecond, remainingSeconds) => {
        progress.value = {
          ...progress.value,
          speed: bytesPerSecond,
          remainingSeconds: Math.round(remainingSeconds),
        }
      },
    })
    registerAbort(abort)

    try {
      const id = crypto.randomUUID()
      const resultId = await upload({
        id,
        file: targetFile,
        fileName: targetFile.name,
        fileSize: targetFile.size,
        status: 'uploading',
        progress: 0,
        metadata: {
          title: publishMetadata.title,
          description: publishMetadata.description || undefined,
          tags: publishMetadata.tags,
          category: publishMetadata.category || undefined,
        },
        platformConfigs,
      })
      if (resultId === null) throw new Error('업로드가 취소되었습니다.')
      videoId.value = resultId
      progress.value = {
        ...progress.value,
        bytesUploaded: targetFile.size,
        percentage: 100,
        speed: 0,
        remainingSeconds: 0,
      }
      return { videoId: resultId }
    } catch (error) {
      uploadError.value = error instanceof Error ? error.message : '업로드에 실패했습니다.'
      throw error
    } finally {
      uploading.value = false
      registerAbort(null)
    }
  }

  function resetUpload() {
    file.value = null
    imageFiles.value = []
    mediaType.value = 'VIDEO'
    videoId.value = null
    contentImages.value = []
    uploading.value = false
    uploadError.value = null
    registerAbort(null)
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
    canAbort,
    step,
    metadata,
    hasActiveSession,
    startUpload,
    startImageUpload,
    uploadImagesToServer,
    streamPublish,
    cloudPublish,
    abortPublish,
    resetUpload,
  }
})
