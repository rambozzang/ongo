<script setup lang="ts">
import { computed } from 'vue'
import type { AudienceOverlapResult } from '@/types/audienceOverlap'

interface Props {
  result: AudienceOverlapResult
}

const props = defineProps<Props>()

const platformConfig: Record<string, { label: string; color: string }> = {
  YOUTUBE: {
    label: 'YouTube',
    color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  },
  TIKTOK: {
    label: 'TikTok',
    color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
  },
  INSTAGRAM: {
    label: 'Instagram',
    color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
  },
  NAVER_CLIP: {
    label: 'Naver Clip',
    color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  },
}

const platformA = computed(() => platformConfig[props.result.platformA] ?? { label: props.result.platformA, color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300' })
const platformB = computed(() => platformConfig[props.result.platformB] ?? { label: props.result.platformB, color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300' })

const overlapLevel = computed(() => {
  const p = props.result.overlapPercent
  if (p >= 40) return { label: '높음', color: 'text-red-600 dark:text-red-400' }
  if (p >= 20) return { label: '보통', color: 'text-yellow-600 dark:text-yellow-400' }
  return { label: '낮음', color: 'text-green-600 dark:text-green-400' }
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200">
    <!-- Platform pair header -->
    <div class="flex items-center gap-2 mb-3">
      <span
        :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', platformA.color]"
      >
        {{ platformA.label }}
      </span>
      <span class="text-gray-400 dark:text-gray-500 text-xs">&harr;</span>
      <span
        :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', platformB.color]"
      >
        {{ platformB.label }}
      </span>
    </div>

    <!-- Overlap percentage -->
    <div class="mb-3 text-center">
      <p class="text-3xl font-bold text-gray-900 dark:text-gray-100">
        {{ result.overlapPercent.toFixed(1) }}%
      </p>
      <p :class="['text-xs font-medium mt-1', overlapLevel.color]">
        오버랩 {{ overlapLevel.label }}
      </p>
    </div>

    <!-- Progress bar -->
    <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden mb-4">
      <div
        class="h-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400 rounded-full transition-all duration-500"
        :style="{ width: `${Math.min(result.overlapPercent, 100)}%` }"
      ></div>
    </div>

    <!-- Stats grid -->
    <div class="grid grid-cols-3 gap-2 text-center">
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ platformA.label }} 고유</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(result.uniqueToA) }}</p>
      </div>
      <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-2">
        <p class="text-xs text-blue-600 dark:text-blue-400">공유</p>
        <p class="text-sm font-semibold text-blue-700 dark:text-blue-300">{{ formatNumber(result.sharedAudience) }}</p>
      </div>
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ platformB.label }} 고유</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(result.uniqueToB) }}</p>
      </div>
    </div>

    <!-- Analyzed date -->
    <p class="mt-3 text-xs text-gray-400 dark:text-gray-500 text-right">
      분석일: {{ formatDate(result.analyzedAt) }}
    </p>
  </div>
</template>
