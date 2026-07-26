<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6">
    <!-- File info -->
    <div class="flex items-center gap-3" :class="variant === 'progress' ? 'mb-4' : ''">
      <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
        <FilmIcon class="h-5 w-5 text-primary-600" />
      </div>
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ fileName }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          {{ formatSize(progress.bytesTotal) }}
          <span v-if="variant === 'selected'" class="text-primary-600 dark:text-primary-400">
            · {{ t('upload.fileSelected') }}
          </span>
        </p>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-if="pausable && uploading"
          class="rounded-lg p-2 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
          :title="t('upload.progress.pause')"
          :aria-label="t('upload.progress.pause')"
          @click="emit('pause')"
        >
          <PauseIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <button
          v-else-if="pausable && !completed && !error"
          class="rounded-lg p-2 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
          :title="t('upload.progress.resume')"
          :aria-label="t('upload.progress.resume')"
          @click="emit('resume')"
        >
          <PlayIcon class="h-5 w-5" aria-hidden="true" />
        </button>
        <button
          v-if="cancellable"
          class="rounded-lg p-2 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-red-500"
          :title="t('action.cancel')"
          :aria-label="t('action.cancel')"
          @click="emit('cancel')"
        >
          <XMarkIcon class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
    </div>

    <!-- Progress bar + stats (실제 업로드 중에만 노출) -->
    <template v-if="variant === 'progress'">
      <div class="mb-3">
        <div
          class="h-2.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700"
          role="progressbar"
          :aria-valuenow="progress.percentage"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-label="t('upload.progress.ariaLabel', { fileName, percentage: progress.percentage })"
        >
          <div
            class="h-full rounded-full transition-all duration-300"
            :class="error ? 'bg-red-500' : completed ? 'bg-green-500' : 'bg-primary-600'"
            :style="{ width: `${progress.percentage}%` }"
          />
        </div>
      </div>

      <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
        <div class="flex items-center gap-3">
          <span class="font-medium" :class="error ? 'text-red-600' : completed ? 'text-green-600' : 'text-primary-600'">
            {{ progress.percentage }}%
          </span>
          <span v-if="uploading && progress.speed > 0">{{ formatSize(progress.speed) }}/s</span>
          <span v-if="uploading && progress.remainingSeconds > 0">
            {{ t('upload.progress.remaining', { time: formatTime(progress.remainingSeconds) }) }}
          </span>
        </div>
        <div>
          <span v-if="error" class="text-red-600">{{ t('status.failed') }}</span>
          <span v-else-if="completed" class="text-green-600">
            <CheckCircleIcon class="mr-1 inline h-4 w-4" />
            {{ t('status.completed') }}
          </span>
          <span v-else>
            {{ formatSize(progress.bytesUploaded) }} / {{ formatSize(progress.bytesTotal) }}
          </span>
        </div>
      </div>
    </template>

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
import { useLocale } from '@/composables/useLocale'

/**
 * variant
 * - selected: 파일 선택 완료 상태 (진행률 바 없음)
 * - progress: 실제 업로드 진행률 표시
 */
withDefaults(
  defineProps<{
    fileName: string
    progress: UploadProgress
    uploading?: boolean
    completed?: boolean
    error?: string | null
    variant?: 'selected' | 'progress'
    pausable?: boolean
    cancellable?: boolean
  }>(),
  {
    uploading: false,
    completed: false,
    error: null,
    variant: 'progress',
    pausable: false,
    cancellable: true,
  },
)

const emit = defineEmits<{
  pause: []
  resume: []
  cancel: []
}>()

const { t } = useLocale()

function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i > 1 ? 1 : 0)} ${units[i]}`
}

function formatTime(seconds: number): string {
  if (seconds < 60) return t('upload.progress.seconds', { n: seconds })
  if (seconds < 3600) {
    return t('upload.progress.minutesSeconds', { m: Math.floor(seconds / 60), s: seconds % 60 })
  }
  return t('upload.progress.hoursMinutes', {
    h: Math.floor(seconds / 3600),
    m: Math.floor((seconds % 3600) / 60),
  })
}
</script>
