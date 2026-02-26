<script setup lang="ts">
import {
  ArrowRightIcon,
  ArrowDownTrayIcon,
} from '@heroicons/vue/24/outline'
import type { RepurposeJob } from '@/types/contentRepurposer'
import RepurposeProgress from './RepurposeProgress.vue'

defineProps<{
  job: RepurposeJob
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVER_CLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const formatLabels: Record<string, string> = {
  SHORT: 'Shorts',
  REEL: 'Reels',
  CLIP: 'Clip',
  STORY: 'Story',
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK
const getFormatLabel = (format: string) => formatLabels[format] ?? format

const formatDate = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Title -->
    <h3 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-1">
      {{ job.originalTitle }}
    </h3>

    <!-- Platform flow: Source -> Target -->
    <div class="mb-3 flex items-center gap-2">
      <span
        :class="[getPlatformStyle(job.originalPlatform).bg, getPlatformStyle(job.originalPlatform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ job.originalPlatform }}
      </span>

      <ArrowRightIcon class="h-3.5 w-3.5 text-gray-400 dark:text-gray-500" />

      <span
        :class="[getPlatformStyle(job.targetPlatform).bg, getPlatformStyle(job.targetPlatform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ job.targetPlatform }}
      </span>

      <span class="rounded-md bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        {{ getFormatLabel(job.targetFormat) }}
      </span>
    </div>

    <!-- Progress -->
    <div class="mb-3">
      <RepurposeProgress :progress="job.progress" :status="job.status" />
    </div>

    <!-- Footer: Date + Download -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ formatDate(job.createdAt) }}
      </span>

      <a
        v-if="job.outputUrl && job.status === 'COMPLETED'"
        :href="job.outputUrl"
        target="_blank"
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
      >
        <ArrowDownTrayIcon class="h-3.5 w-3.5" />
        다운로드
      </a>
    </div>
  </div>
</template>
