<script setup lang="ts">
import { computed } from 'vue'
import {
  FilmIcon,
  PhotoIcon,
  MusicalNoteIcon,
  PauseIcon,
  PlayIcon,
  ArrowPathIcon,
  ChevronUpIcon,
  ChevronDownIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import type { UploadQueueItem } from '@/types/uploadQueue'
import type { Platform } from '@/types/channel'
import { formatFileSize } from '@/utils/format'

const props = defineProps<{
  item: UploadQueueItem
  index: number
  total: number
}>()

const emit = defineEmits<{
  pause: [id: string]
  resume: [id: string]
  retry: [id: string]
  remove: [id: string]
  moveUp: [id: string]
  moveDown: [id: string]
}>()

const fileIcon = computed(() => {
  const mime = props.item.mimeType
  if (mime.startsWith('video/')) return FilmIcon
  if (mime.startsWith('image/')) return PhotoIcon
  if (mime.startsWith('audio/')) return MusicalNoteIcon
  return FilmIcon
})

const truncatedName = computed(() => {
  const name = props.item.fileName
  if (name.length <= 30) return name
  const ext = name.lastIndexOf('.')
  if (ext === -1) return name.slice(0, 27) + '...'
  const extension = name.slice(ext)
  const baseName = name.slice(0, ext)
  if (baseName.length <= 25) return name
  return baseName.slice(0, 25) + '...' + extension
})

const statusText = computed(() => {
  const map: Record<string, string> = {
    pending: '대기 중',
    uploading: '업로드 중',
    paused: '일시정지',
    completed: '완료',
    failed: '실패',
  }
  return map[props.item.status] || props.item.status
})

const statusBadgeClass = computed(() => {
  const map: Record<string, string> = {
    pending: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300',
    uploading: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    paused: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
    completed: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    failed: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  }
  return map[props.item.status] || ''
})

const progressBarClass = computed(() => {
  const map: Record<string, string> = {
    pending: 'bg-gray-400',
    uploading: 'bg-primary-500',
    paused: 'bg-amber-500',
    completed: 'bg-green-500',
    failed: 'bg-red-500',
  }
  return map[props.item.status] || 'bg-primary-500'
})

const canMoveUp = computed(() => props.item.status === 'pending' && props.index > 0)
const canMoveDown = computed(() => props.item.status === 'pending' && props.index < props.total - 1)
const canRemove = computed(() => props.item.status !== 'completed')
</script>

<template>
  <div
    class="rounded-lg border border-gray-200 bg-white p-4 transition-all hover:shadow-sm dark:border-gray-700 dark:bg-gray-800"
    :class="{
      'border-l-4 border-l-primary-500': item.status === 'uploading',
      'border-l-4 border-l-amber-500': item.status === 'paused',
      'border-l-4 border-l-green-500': item.status === 'completed',
      'border-l-4 border-l-red-500': item.status === 'failed',
    }"
  >
    <div class="flex items-start gap-3">
      <!-- Index number + File icon -->
      <div class="flex flex-col items-center gap-1">
        <span class="text-xs font-medium text-gray-400 dark:text-gray-500">{{ index + 1 }}</span>
        <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-700">
          <component :is="fileIcon" class="h-5 w-5 text-gray-500 dark:text-gray-400" />
        </div>
      </div>

      <!-- File info -->
      <div class="min-w-0 flex-1">
        <!-- Row 1: name + status badge -->
        <div class="flex items-center justify-between gap-2">
          <p
            class="truncate text-sm font-medium text-gray-900 dark:text-gray-100"
            :title="item.fileName"
          >
            {{ truncatedName }}
          </p>
          <span
            class="flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
            :class="statusBadgeClass"
          >
            {{ statusText }}
          </span>
        </div>

        <!-- Row 2: file size + platform badges -->
        <div class="mt-1.5 flex flex-wrap items-center gap-2">
          <span class="text-xs text-gray-500 dark:text-gray-400">
            {{ formatFileSize(item.fileSize) }}
          </span>
          <span class="text-gray-300 dark:text-gray-600">|</span>
          <div class="flex flex-wrap gap-1">
            <PlatformBadge
              v-for="platform in item.platforms"
              :key="platform"
              :platform="platform as Platform"
            />
          </div>
        </div>

        <!-- Progress bar (for uploading, paused, failed with partial progress) -->
        <div
          v-if="item.status === 'uploading' || item.status === 'paused' || (item.status === 'failed' && item.progress > 0)"
          class="mt-3"
        >
          <div class="mb-1 flex items-center justify-between text-xs">
            <span class="text-gray-500 dark:text-gray-400">진행률</span>
            <span class="font-medium text-gray-700 dark:text-gray-300">
              {{ Math.round(item.progress) }}%
            </span>
          </div>
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-300"
              :class="progressBarClass"
              :style="{ width: `${item.progress}%` }"
            />
          </div>
        </div>

        <!-- Completed progress bar (full) -->
        <div v-if="item.status === 'completed'" class="mt-3">
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div class="h-full w-full rounded-full bg-green-500" />
          </div>
        </div>

        <!-- Error message -->
        <div
          v-if="item.status === 'failed' && item.error"
          class="mt-2 rounded-md bg-red-50 px-3 py-2 dark:bg-red-900/20"
        >
          <p class="text-xs text-red-600 dark:text-red-400">{{ item.error }}</p>
        </div>

        <!-- Action buttons -->
        <div class="mt-3 flex items-center gap-1">
          <!-- Uploading: Pause -->
          <button
            v-if="item.status === 'uploading'"
            class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-xs font-medium text-amber-700 transition-colors hover:bg-amber-100 dark:text-amber-400 dark:hover:bg-amber-900/30"
            title="일시정지"
            @click="emit('pause', item.id)"
          >
            <PauseIcon class="h-3.5 w-3.5" />
            일시정지
          </button>

          <!-- Paused: Resume -->
          <button
            v-if="item.status === 'paused'"
            class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-xs font-medium text-primary-700 transition-colors hover:bg-primary-100 dark:text-primary-400 dark:hover:bg-primary-900/30"
            title="재개"
            @click="emit('resume', item.id)"
          >
            <PlayIcon class="h-3.5 w-3.5" />
            재개
          </button>

          <!-- Failed: Retry -->
          <button
            v-if="item.status === 'failed'"
            class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-xs font-medium text-primary-700 transition-colors hover:bg-primary-100 dark:text-primary-400 dark:hover:bg-primary-900/30"
            title="재시도"
            @click="emit('retry', item.id)"
          >
            <ArrowPathIcon class="h-3.5 w-3.5" />
            재시도
          </button>

          <!-- Pending: Move up/down -->
          <button
            v-if="canMoveUp"
            class="inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
            title="위로 이동"
            @click="emit('moveUp', item.id)"
          >
            <ChevronUpIcon class="h-3.5 w-3.5" />
          </button>
          <button
            v-if="canMoveDown"
            class="inline-flex items-center gap-0.5 rounded-md px-2 py-1 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
            title="아래로 이동"
            @click="emit('moveDown', item.id)"
          >
            <ChevronDownIcon class="h-3.5 w-3.5" />
          </button>

          <!-- Spacer -->
          <div class="flex-1" />

          <!-- Remove (all except completed) -->
          <button
            v-if="canRemove"
            class="inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-100 dark:text-red-400 dark:hover:bg-red-900/30"
            title="제거"
            @click="emit('remove', item.id)"
          >
            <XMarkIcon class="h-3.5 w-3.5" />
            제거
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
