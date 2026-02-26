<script setup lang="ts">
import { computed } from 'vue'
import {
  FilmIcon,
  CheckCircleIcon,
  ClockIcon,
  ExclamationCircleIcon,
  ArrowPathIcon,
} from '@heroicons/vue/24/outline'
import SubtitleProgressBar from './SubtitleProgressBar.vue'
import type { SubtitleJob } from '@/types/subtitleGenerator'

const props = defineProps<{
  job: SubtitleJob
  selected?: boolean
}>()

defineEmits<{
  select: [id: number]
}>()

const statusConfig = computed(() => {
  const configs: Record<SubtitleJob['status'], { label: string; color: string; icon: typeof CheckCircleIcon }> = {
    PENDING: {
      label: '대기중',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
      icon: ClockIcon,
    },
    PROCESSING: {
      label: '처리중',
      color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
      icon: ArrowPathIcon,
    },
    COMPLETED: {
      label: '완료',
      color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
      icon: CheckCircleIcon,
    },
    FAILED: {
      label: '실패',
      color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
      icon: ExclamationCircleIcon,
    },
  }
  return configs[props.job.status] ?? configs.PENDING
})

const platformBadge = computed(() => {
  const configs: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    INSTAGRAM: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return configs[props.job.platform.toUpperCase()] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const languageLabel = computed(() => {
  const labels: Record<string, string> = {
    ko: '한국어',
    en: 'English',
    ja: '日本語',
    zh: '中文',
    es: 'Espanol',
    auto: '자동감지',
  }
  return labels[props.job.language] ?? props.job.language
})

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border bg-white p-4 shadow-sm transition-all hover:shadow-md dark:bg-gray-900"
    :class="selected
      ? 'border-primary-500 ring-2 ring-primary-500/20 dark:border-primary-400 dark:ring-primary-400/20'
      : 'border-gray-200 dark:border-gray-700'"
    @click="$emit('select', job.id)"
  >
    <!-- Header: Title + Status -->
    <div class="mb-3 flex items-start justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <FilmIcon class="h-5 w-5 flex-shrink-0 text-gray-400 dark:text-gray-500" />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ job.videoTitle }}
        </h3>
      </div>
      <span
        class="inline-flex flex-shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium"
        :class="statusConfig.color"
      >
        <component :is="statusConfig.icon" class="h-3.5 w-3.5" />
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Badges: Platform, Language, Duration -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="platformBadge"
      >
        {{ job.platform }}
      </span>
      <span class="inline-flex items-center rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400">
        {{ languageLabel }}
      </span>
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ formatDuration(job.duration) }}
      </span>
    </div>

    <!-- Progress Bar -->
    <div class="mb-3">
      <SubtitleProgressBar :progress="job.progress" :status="job.status" />
    </div>

    <!-- Footer: Word count -->
    <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
      <span v-if="job.wordCount > 0">
        {{ job.wordCount.toLocaleString('ko-KR') }}단어
      </span>
      <span v-else>-</span>
      <span>
        {{ new Date(job.createdAt).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
      </span>
    </div>
  </div>
</template>
