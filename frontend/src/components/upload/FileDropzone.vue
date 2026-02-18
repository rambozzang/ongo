<template>
  <div>
    <div
      class="relative rounded-xl border-2 transition-all duration-300"
      :class="[
        dropState === 'idle' && 'border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800/50 hover:border-gray-400 dark:hover:border-gray-500',
        dropState === 'hover' && 'border-solid border-primary-500 bg-primary-50 dark:bg-primary-900/20 scale-[1.01] border-pulse',
        dropState === 'success' && 'border-solid border-green-500 bg-green-50 dark:bg-green-900/20',
        disabled ? 'pointer-events-none opacity-50' : 'cursor-pointer',
      ]"
      @dragover.prevent="onDragOver"
      @dragenter.prevent="onDragEnter"
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
      @click="openFilePicker"
    >
      <div class="flex flex-col items-center justify-center py-16">
        <!-- Upload Icon (default & hover) -->
        <ArrowUpTrayIcon
          v-if="dropState !== 'success'"
          class="mb-4 h-12 w-12 transition-all duration-300"
          :class="[
            dropState === 'idle' && 'text-gray-400 dark:text-gray-500',
            dropState === 'hover' && 'text-primary-500 bounce-up',
          ]"
        />

        <!-- Success Icon -->
        <CheckCircleIcon
          v-else
          class="mb-4 h-12 w-12 text-green-500 check-draw"
        />

        <!-- Text -->
        <p
          class="mb-2 text-base font-medium transition-colors duration-300"
          :class="[
            dropState === 'idle' && 'text-gray-700 dark:text-gray-300',
            dropState === 'hover' && 'text-primary-600 dark:text-primary-400',
            dropState === 'success' && 'text-green-600 dark:text-green-400',
          ]"
        >
          {{
            dropState === 'hover'
              ? t('upload.dropzone.dropHere')
              : dropState === 'success'
                ? t('upload.dropzone.fileAdded')
                : t('upload.dropzone.dragDrop')
          }}
        </p>

        <p
          v-if="dropState === 'idle'"
          class="text-sm text-gray-500 dark:text-gray-400"
        >
          {{ t('upload.dropzone.selectFile') }} • {{ t('upload.dropzone.formatHint') }}
        </p>
      </div>

      <input
        ref="fileInput"
        type="file"
        class="hidden"
        multiple
        :aria-label="t('upload.dropzone.ariaLabel')"
        :accept="ALLOWED_EXTENSIONS.join(',')"
        @change="onFileSelect"
      />
    </div>

    <!-- File Preview Card (single video file) -->
    <div
      v-if="selectedFile && !isSelectedImage"
      class="mt-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 transition-all duration-300"
    >
      <div class="flex items-start gap-4">
        <!-- Thumbnail -->
        <div
          class="relative flex-shrink-0 w-24 h-16 rounded bg-gray-100 dark:bg-gray-700 overflow-hidden"
        >
          <video
            v-if="previewUrl"
            :src="previewUrl"
            class="w-full h-full object-cover"
            muted
            @loadeddata="captureFrame"
          />
          <div
            v-else
            class="w-full h-full flex items-center justify-center"
          >
            <VideoCameraIcon class="w-8 h-8 text-gray-400 dark:text-gray-500" />
          </div>
        </div>

        <!-- File Info -->
        <div class="flex-1 min-w-0">
          <p
            class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate"
            :title="selectedFile.name"
          >
            {{ selectedFile.name }}
          </p>
          <div class="mt-1 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
            <span>{{ formatFileSize(selectedFile.size) }}</span>
            <span>&bull;</span>
            <span>{{ getFileExtension(selectedFile.name).toUpperCase() }}</span>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex-shrink-0 flex gap-2">
          <button
            type="button"
            class="px-3 py-1.5 text-xs font-medium text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-md transition-colors"
            @click.stop="openFilePicker"
          >
            {{ t('upload.dropzone.change') }}
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-md transition-colors"
            @click.stop="clearFile"
          >
            {{ t('upload.dropzone.remove') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Image Preview Card (single image file) -->
    <div
      v-if="selectedFile && isSelectedImage"
      class="mt-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 transition-all duration-300"
    >
      <div class="flex items-start gap-4">
        <div
          class="relative flex-shrink-0 w-24 h-16 rounded bg-gray-100 dark:bg-gray-700 overflow-hidden"
        >
          <img
            v-if="previewUrl"
            :src="previewUrl"
            :alt="selectedFile.name"
            class="w-full h-full object-cover"
          />
          <div
            v-else
            class="w-full h-full flex items-center justify-center"
          >
            <PhotoIcon class="w-8 h-8 text-gray-400 dark:text-gray-500" />
          </div>
        </div>

        <div class="flex-1 min-w-0">
          <p
            class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate"
            :title="selectedFile.name"
          >
            {{ selectedFile.name }}
          </p>
          <div class="mt-1 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
            <span>{{ formatFileSize(selectedFile.size) }}</span>
            <span>&bull;</span>
            <span>{{ getFileExtension(selectedFile.name).toUpperCase() }}</span>
          </div>
        </div>

        <div class="flex-shrink-0 flex gap-2">
          <button
            type="button"
            class="px-3 py-1.5 text-xs font-medium text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-md transition-colors"
            @click.stop="openFilePicker"
          >
            {{ t('upload.dropzone.change') }}
          </button>
          <button
            type="button"
            class="px-3 py-1.5 text-xs font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-md transition-colors"
            @click.stop="clearFile"
          >
            {{ t('upload.dropzone.remove') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { ArrowUpTrayIcon, CheckCircleIcon, VideoCameraIcon, PhotoIcon } from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import type { MediaType } from '@/types/video'

const { t } = useLocale()

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
const MAX_IMAGE_COUNT = 10

function detectMediaType(file: File): MediaType {
  if (IMAGE_MIMES.includes(file.type)) return 'IMAGE'
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (IMAGE_EXTENSIONS.includes(ext)) return 'IMAGE'
  return 'VIDEO'
}

function getMaxSize(file: File): number {
  return detectMediaType(file) === 'IMAGE' ? IMAGE_MAX_SIZE : VIDEO_MAX_SIZE
}

const emit = defineEmits<{
  select: [file: File]
  selectMultiple: [files: File[]]
  selectImages: [files: File[]]
  error: [message: string]
}>()

defineProps<{
  disabled?: boolean
}>()

type DropState = 'idle' | 'hover' | 'success'

const dropState = ref<DropState>('idle')
const dragCounter = ref(0)
const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const previewUrl = ref<string | null>(null)
const isSelectedImage = computed(() => selectedFile.value ? detectMediaType(selectedFile.value) === 'IMAGE' : false)

function openFilePicker() {
  fileInput.value?.click()
}

function onDragEnter() {
  dragCounter.value++
  dropState.value = 'hover'
}

function onDragOver() {
  if (dropState.value !== 'hover') {
    dropState.value = 'hover'
  }
}

function onDragLeave() {
  dragCounter.value--
  if (dragCounter.value === 0) {
    dropState.value = 'idle'
  }
}

function onDrop(event: DragEvent) {
  dragCounter.value = 0
  dropState.value = 'success'

  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    handleFiles(files)
  }

  setTimeout(() => {
    if (dropState.value === 'success') {
      dropState.value = 'idle'
    }
  }, 1500)
}

function onFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (files && files.length > 0) {
    handleFiles(files)
  }
  input.value = ''
}

function handleFiles(fileList: FileList) {
  const filesArray = Array.from(fileList)

  // Check if all files are images (story/carousel mode)
  const allImages = filesArray.every((f) => detectMediaType(f) === 'IMAGE')

  if (allImages && filesArray.length > 1) {
    // Multiple images - story mode
    if (filesArray.length > MAX_IMAGE_COUNT) {
      emit('error', t('video.image.maxFiles', { max: MAX_IMAGE_COUNT }))
      return
    }

    const validFiles: File[] = []
    const errors: string[] = []

    for (const file of filesArray) {
      const ext = '.' + file.name.split('.').pop()?.toLowerCase()
      if (!ALLOWED_EXTENSIONS.includes(ext)) {
        errors.push(`${file.name}: ${t('upload.error.unsupportedFormat')}`)
        continue
      }
      if (!ALLOWED_MIMES.includes(file.type) && file.type !== '') {
        errors.push(`${file.name}: ${t('upload.error.invalidFile')}`)
        continue
      }
      if (file.size > IMAGE_MAX_SIZE) {
        errors.push(`${file.name}: ${t('upload.error.fileTooLarge', { limit: '50MB' })}`)
        continue
      }
      validFiles.push(file)
    }

    if (errors.length > 0) {
      emit('error', errors.join('\n'))
    }
    if (validFiles.length > 0) {
      emit('selectImages', validFiles)
    }
    return
  }

  // Single file
  if (filesArray.length === 1) {
    validateAndEmit(filesArray[0])
  } else {
    // Multiple video files: emit selectMultiple event
    const validFiles: File[] = []
    const errors: string[] = []

    for (const file of filesArray) {
      const ext = '.' + file.name.split('.').pop()?.toLowerCase()
      if (!ALLOWED_EXTENSIONS.includes(ext)) {
        errors.push(`${file.name}: ${t('upload.error.unsupportedFormat')}`)
        continue
      }
      if (!ALLOWED_MIMES.includes(file.type) && file.type !== '') {
        errors.push(`${file.name}: ${t('upload.error.invalidFile')}`)
        continue
      }
      const maxSize = getMaxSize(file)
      if (file.size > maxSize) {
        const limitLabel = detectMediaType(file) === 'IMAGE' ? '50MB' : '2GB'
        errors.push(`${file.name}: ${t('upload.error.fileTooLarge', { limit: limitLabel })}`)
        continue
      }
      validFiles.push(file)
    }

    if (errors.length > 0) {
      emit('error', errors.join('\n'))
    }
    if (validFiles.length > 0) {
      emit('selectMultiple', validFiles)
    }
  }
}

function validateAndEmit(file: File) {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    emit('error', t('upload.error.unsupportedFormatDetail', { formats: ALLOWED_EXTENSIONS.join(', ') }))
    return
  }

  if (!ALLOWED_MIMES.includes(file.type) && file.type !== '') {
    emit('error', t('upload.error.invalidFileDetail'))
    return
  }

  const maxSize = getMaxSize(file)
  if (file.size > maxSize) {
    const limitLabel = detectMediaType(file) === 'IMAGE' ? '50MB' : '2GB'
    emit('error', t('upload.error.fileTooLargeDetail', { limit: limitLabel }))
    return
  }

  selectedFile.value = file

  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = URL.createObjectURL(file)

  // For images, also emit selectImages with single file for consistency
  if (detectMediaType(file) === 'IMAGE') {
    emit('selectImages', [file])
  } else {
    emit('select', file)
  }
}

function clearFile() {
  selectedFile.value = null
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }
  dropState.value = 'idle'
  dragCounter.value = 0
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

onUnmounted(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})

function captureFrame(_event: Event) {
  // Frame capture happens automatically when video loads
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

function getFileExtension(filename: string): string {
  return filename.split('.').pop()?.toLowerCase() || ''
}
</script>

<style scoped>
@keyframes border-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(139, 92, 246, 0.4);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.1);
  }
}

.border-pulse {
  animation: border-pulse 2s ease-in-out infinite;
}

@keyframes bounce-up {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.bounce-up {
  animation: bounce-up 0.6s ease-in-out infinite;
}

@keyframes check-draw {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.check-draw {
  animation: check-draw 0.4s ease-out;
}
</style>
