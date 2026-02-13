import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UploadProgress } from '@/types/video'
import * as tus from 'tus-js-client'

export const useUploadStore = defineStore('upload', () => {
  const file = ref<File | null>(null)
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

  const isUploading = computed(() => uploading.value)

  let lastTimestamp = 0
  let lastBytesUploaded = 0

  function startUpload(selectedFile: File) {
    file.value = selectedFile
    uploading.value = true
    uploadError.value = null
    lastTimestamp = Date.now()
    lastBytesUploaded = 0

    const token = localStorage.getItem('accessToken')

    const upload = new tus.Upload(selectedFile, {
      endpoint: `${import.meta.env.VITE_API_BASE_URL}/videos/upload`,
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
  }

  return {
    file,
    progress,
    uploading,
    isUploading,
    uploadError,
    uploadUrl,
    startUpload,
    pauseUpload,
    resumeUpload,
    resetUpload,
  }
})
