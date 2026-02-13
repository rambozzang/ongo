<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  ArrowUpTrayIcon,
  CheckCircleIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { formatFileSize } from '@/utils/format'

const MAX_FILES = 50
const ACCEPTED_TYPES = 'video/*,image/*,audio/*'

const ALL_PLATFORMS: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']

const emit = defineEmits<{
  addToQueue: [files: File[], platforms: string[]]
}>()

type DropState = 'idle' | 'hover' | 'success'

const dropState = ref<DropState>('idle')
const dragCounter = ref(0)
const fileInput = ref<HTMLInputElement>()
const selectedFiles = ref<File[]>([])
const selectedPlatforms = ref<string[]>(['YOUTUBE'])

const fileCountText = computed(() => {
  const count = selectedFiles.value.length
  if (count === 0) return ''
  return `${count}개 파일 선택됨`
})

const totalSize = computed(() => {
  return selectedFiles.value.reduce((sum, f) => sum + f.size, 0)
})

const canAdd = computed(() => {
  return selectedFiles.value.length > 0 && selectedPlatforms.value.length > 0
})

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
  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    handleFiles(files)
    dropState.value = 'success'
    setTimeout(() => {
      if (dropState.value === 'success') {
        dropState.value = 'idle'
      }
    }, 1500)
  } else {
    dropState.value = 'idle'
  }
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
  const filesArray = Array.from(fileList).slice(0, MAX_FILES)
  // Filter for accepted types
  const validFiles = filesArray.filter((f) => {
    return f.type.startsWith('video/') || f.type.startsWith('image/') || f.type.startsWith('audio/')
  })
  selectedFiles.value = validFiles
}

function removeFile(index: number) {
  selectedFiles.value.splice(index, 1)
}

function clearFiles() {
  selectedFiles.value = []
}

function togglePlatform(platform: string) {
  const idx = selectedPlatforms.value.indexOf(platform)
  if (idx >= 0) {
    selectedPlatforms.value.splice(idx, 1)
  } else {
    selectedPlatforms.value.push(platform)
  }
}

function handleAddToQueue() {
  if (!canAdd.value) return
  emit('addToQueue', [...selectedFiles.value], [...selectedPlatforms.value])
  selectedFiles.value = []
}
</script>

<template>
  <div class="space-y-4">
    <!-- Drop zone area -->
    <div
      class="relative cursor-pointer rounded-xl border-2 transition-all duration-300"
      :class="[
        dropState === 'idle' && 'border-dashed border-gray-300 bg-gray-50 hover:border-gray-400 dark:border-gray-600 dark:bg-gray-800/50 dark:hover:border-gray-500',
        dropState === 'hover' && 'scale-[1.01] border-solid border-primary-500 bg-primary-50 border-pulse dark:bg-primary-900/20',
        dropState === 'success' && 'border-solid border-green-500 bg-green-50 dark:bg-green-900/20',
      ]"
      @dragover.prevent="onDragOver"
      @dragenter.prevent="onDragEnter"
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
      @click="openFilePicker"
    >
      <div class="flex flex-col items-center justify-center py-12">
        <!-- Icon -->
        <ArrowUpTrayIcon
          v-if="dropState !== 'success'"
          class="mb-4 h-12 w-12 transition-all duration-300"
          :class="[
            dropState === 'idle' && 'text-gray-400 dark:text-gray-500',
            dropState === 'hover' && 'text-primary-500 bounce-up',
          ]"
        />
        <CheckCircleIcon
          v-else
          class="mb-4 h-12 w-12 text-green-500"
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
              ? '여기에 놓으세요!'
              : dropState === 'success'
                ? '파일이 선택되었습니다!'
                : '파일을 드래그하거나 클릭하여 선택'
          }}
        </p>
        <p
          v-if="dropState === 'idle'"
          class="text-sm text-gray-500 dark:text-gray-400"
        >
          동영상, 이미지, 오디오 파일 지원 (최대 {{ MAX_FILES }}개)
        </p>
      </div>

      <input
        ref="fileInput"
        type="file"
        class="hidden"
        multiple
        :accept="ACCEPTED_TYPES"
        @change="onFileSelect"
      />
    </div>

    <!-- Selected files list -->
    <div
      v-if="selectedFiles.length > 0"
      class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800"
    >
      <!-- File list header -->
      <div class="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-700">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ fileCountText }}
          </span>
          <span class="text-xs text-gray-500 dark:text-gray-400">
            ({{ formatFileSize(totalSize) }})
          </span>
        </div>
        <button
          class="text-xs font-medium text-red-600 transition-colors hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
          @click.stop="clearFiles"
        >
          전체 해제
        </button>
      </div>

      <!-- File items (scrollable) -->
      <div class="max-h-[200px] overflow-y-auto">
        <div
          v-for="(file, index) in selectedFiles"
          :key="index"
          class="flex items-center justify-between border-b border-gray-100 px-4 py-2 last:border-0 dark:border-gray-700"
        >
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm text-gray-700 dark:text-gray-300" :title="file.name">
              {{ file.name }}
            </p>
            <p class="text-xs text-gray-400 dark:text-gray-500">
              {{ formatFileSize(file.size) }}
            </p>
          </div>
          <button
            class="ml-2 flex-shrink-0 rounded p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-500 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-red-400"
            @click.stop="removeFile(index)"
          >
            <XMarkIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>

    <!-- Platform selection -->
    <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <label class="mb-3 block text-sm font-medium text-gray-700 dark:text-gray-300">
        게시 플랫폼 선택
      </label>
      <div class="flex flex-wrap gap-3">
        <label
          v-for="platform in ALL_PLATFORMS"
          :key="platform"
          class="inline-flex cursor-pointer items-center gap-2 rounded-lg border-2 px-4 py-2.5 transition-all"
          :class="
            selectedPlatforms.includes(platform)
              ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
              : 'border-gray-200 hover:border-gray-300 dark:border-gray-600 dark:hover:border-gray-500'
          "
        >
          <input
            type="checkbox"
            class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700"
            :checked="selectedPlatforms.includes(platform)"
            @change="togglePlatform(platform)"
          />
          <div
            class="flex h-6 w-6 items-center justify-center rounded"
            :style="{ backgroundColor: PLATFORM_CONFIG[platform].color + '1A' }"
          >
            <span class="text-xs font-bold" :style="{ color: PLATFORM_CONFIG[platform].color }">
              {{ PLATFORM_CONFIG[platform].label[0] }}
            </span>
          </div>
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ PLATFORM_CONFIG[platform].label }}
          </span>
        </label>
      </div>
    </div>

    <!-- Add to queue button -->
    <button
      :disabled="!canAdd"
      class="w-full rounded-xl px-6 py-3 text-base font-semibold shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-gray-900"
      :class="
        canAdd
          ? 'bg-primary-600 text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-400'
          : 'bg-gray-300 text-gray-500 dark:bg-gray-700 dark:text-gray-500'
      "
      @click="handleAddToQueue"
    >
      큐에 추가
      <span v-if="selectedFiles.length > 0" class="ml-1">
        ({{ selectedFiles.length }}개)
      </span>
    </button>
  </div>
</template>

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

@media (prefers-reduced-motion: reduce) {
  .border-pulse,
  .bounce-up {
    animation: none !important;
  }
}
</style>
