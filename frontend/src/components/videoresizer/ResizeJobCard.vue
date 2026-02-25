<template>
  <div
    class="rounded-xl border bg-white p-4 transition-shadow hover:shadow-md dark:bg-gray-800"
    :class="statusBorderClass"
  >
    <div class="flex items-start gap-3">
      <!-- Thumbnail -->
      <div class="h-14 w-20 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
        <img
          v-if="job.thumbnailUrl"
          :src="job.thumbnailUrl"
          :alt="job.videoTitle"
          class="h-full w-full object-cover"
        />
        <div v-else class="flex h-full w-full items-center justify-center">
          <FilmIcon class="h-6 w-6 text-gray-400 dark:text-gray-500" />
        </div>
      </div>

      <!-- Content -->
      <div class="min-w-0 flex-1">
        <!-- Title & Platform Badge -->
        <div class="flex items-start justify-between gap-2">
          <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ job.videoTitle }}
          </h4>
          <span
            class="inline-flex flex-shrink-0 items-center rounded-full px-2 py-0.5 text-[10px] font-medium text-white"
            :style="{ backgroundColor: platformColor }"
          >
            {{ platformLabel }}
          </span>
        </div>

        <!-- Aspect Ratio Info -->
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ job.originalAspectRatio }} → {{ job.targetAspectRatio }}
        </p>

        <!-- Status -->
        <div class="mt-2 flex items-center gap-2">
          <span
            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold"
            :class="statusBadgeClass"
          >
            <span
              v-if="job.status === 'PROCESSING'"
              class="h-1.5 w-1.5 animate-pulse rounded-full bg-blue-500"
            />
            {{ $t(`videoResizer.status.${job.status}`) }}
          </span>
          <span class="text-[10px] text-gray-400 dark:text-gray-500">
            {{ formatTime(job.createdAt) }}
          </span>
        </div>

        <!-- Progress Bar (PROCESSING only) -->
        <div v-if="job.status === 'PROCESSING'" class="mt-2">
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full bg-blue-500 transition-all duration-500"
              :class="{ 'animate-pulse': job.progress < 100 }"
              :style="{ width: `${job.progress}%` }"
            />
          </div>
          <span class="mt-0.5 text-[10px] text-blue-600 dark:text-blue-400">{{ job.progress }}%</span>
        </div>
      </div>
    </div>

    <!-- Actions -->
    <div class="mt-3 flex items-center justify-end gap-2 border-t border-gray-100 pt-3 dark:border-gray-700">
      <button
        v-if="job.status === 'PENDING' || job.status === 'PROCESSING'"
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
        @click="emit('cancel', job.id)"
      >
        <XMarkIcon class="h-3.5 w-3.5" />
        {{ $t('videoResizer.cancel') }}
      </button>
      <button
        v-if="job.status === 'COMPLETED' && job.outputUrl"
        class="inline-flex items-center gap-1 rounded-lg bg-primary-600 px-2.5 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-700"
        @click="emit('download', job.id)"
      >
        <ArrowDownTrayIcon class="h-3.5 w-3.5" />
        {{ $t('videoResizer.download') }}
      </button>
      <button
        v-if="job.status === 'COMPLETED' || job.status === 'FAILED'"
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
        @click="emit('delete', job.id)"
      >
        <TrashIcon class="h-3.5 w-3.5" />
        {{ $t('videoResizer.delete') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { FilmIcon, XMarkIcon, ArrowDownTrayIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { ResizeJob, PlatformTarget } from '@/types/videoResizer'

useI18n({ useScope: 'global' })

const props = defineProps<{
  job: ResizeJob
}>()

const emit = defineEmits<{
  cancel: [id: number]
  download: [id: number]
  delete: [id: number]
}>()

const PLATFORM_COLORS: Record<PlatformTarget, string> = {
  YOUTUBE: '#FF0000',
  TIKTOK: '#000000',
  INSTAGRAM_REELS: '#E1306C',
  NAVER_CLIP: '#03C75A',
  YOUTUBE_SHORTS: '#FF0000',
}

const PLATFORM_LABELS: Record<PlatformTarget, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM_REELS: 'Reels',
  NAVER_CLIP: 'Naver Clip',
  YOUTUBE_SHORTS: 'Shorts',
}

const platformColor = computed(() => PLATFORM_COLORS[props.job.platform])
const platformLabel = computed(() => PLATFORM_LABELS[props.job.platform])

const statusBorderClass = computed(() => {
  switch (props.job.status) {
    case 'PENDING': return 'border-gray-200 dark:border-gray-700'
    case 'PROCESSING': return 'border-blue-200 dark:border-blue-800'
    case 'COMPLETED': return 'border-green-200 dark:border-green-800'
    case 'FAILED': return 'border-red-200 dark:border-red-800'
    default: return 'border-gray-200 dark:border-gray-700'
  }
})

const statusBadgeClass = computed(() => {
  switch (props.job.status) {
    case 'PENDING': return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
    case 'PROCESSING': return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
    case 'COMPLETED': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'FAILED': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    default: return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
})

function formatTime(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}.${month}.${day} ${hours}:${minutes}`
}
</script>
