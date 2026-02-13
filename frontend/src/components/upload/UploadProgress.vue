<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6">
    <!-- File info -->
    <div class="mb-4 flex items-center gap-3">
      <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
        <FilmIcon class="h-5 w-5 text-primary-600" />
      </div>
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ fileName }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ formatSize(progress.bytesTotal) }}</p>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-if="uploading"
          class="rounded-lg p-2 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
          title="일시정지"
          aria-label="업로드 일시정지"
          @click="emit('pause')"
        >
          <PauseIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <button
          v-else-if="!completed && !error"
          class="rounded-lg p-2 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
          title="재개"
          aria-label="업로드 재개"
          @click="emit('resume')"
        >
          <PlayIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <button
          class="rounded-lg p-2 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-red-500"
          title="취소"
          aria-label="업로드 취소"
          @click="emit('cancel')"
        >
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>
    </div>

    <!-- Progress bar -->
    <div class="mb-3">
      <div
        class="h-2.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700"
        role="progressbar"
        :aria-valuenow="progress.percentage"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-label="`${fileName} 업로드 진행률: ${progress.percentage}%`"
      >
        <div
          class="h-full rounded-full transition-all duration-300"
          :class="error ? 'bg-red-500' : completed ? 'bg-green-500' : 'bg-primary-600'"
          :style="{ width: `${progress.percentage}%` }"
        />
      </div>
    </div>

    <!-- Stats -->
    <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
      <div class="flex items-center gap-3">
        <span class="font-medium" :class="error ? 'text-red-600' : completed ? 'text-green-600' : 'text-primary-600'">
          {{ progress.percentage }}%
        </span>
        <span v-if="uploading">{{ formatSize(progress.speed) }}/s</span>
        <span v-if="uploading && progress.remainingSeconds > 0">
          {{ formatTime(progress.remainingSeconds) }} 남음
        </span>
      </div>
      <div>
        <span v-if="error" class="text-red-600">업로드 실패</span>
        <span v-else-if="completed" class="text-green-600">
          <CheckCircleIcon class="mr-1 inline h-4 w-4" />
          완료
        </span>
        <span v-else>
          {{ formatSize(progress.bytesUploaded) }} / {{ formatSize(progress.bytesTotal) }}
        </span>
      </div>
    </div>

    <!-- Error message -->
    <div v-if="error" class="mt-3 rounded-lg bg-red-50 dark:bg-red-900/20 p-3 text-sm text-red-700 dark:text-red-400">
      {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  FilmIcon,
  PauseIcon,
  PlayIcon,
  XMarkIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import type { UploadProgress } from '@/types/video'

defineProps<{
  fileName: string
  progress: UploadProgress
  uploading: boolean
  completed: boolean
  error: string | null
}>()

const emit = defineEmits<{
  pause: []
  resume: []
  cancel: []
}>()

function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i > 1 ? 1 : 0)} ${units[i]}`
}

function formatTime(seconds: number): string {
  if (seconds < 60) return `${seconds}초`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${h}시간 ${m}분`
}
</script>
