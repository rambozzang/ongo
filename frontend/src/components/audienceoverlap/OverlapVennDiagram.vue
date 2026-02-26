<script setup lang="ts">
import { computed } from 'vue'
import type { AudienceOverlapResult } from '@/types/audienceOverlap'

interface Props {
  result: AudienceOverlapResult
}

const props = defineProps<Props>()

const platformLabels: Record<string, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM: 'Instagram',
  NAVER_CLIP: 'Naver Clip',
}

const platformColors: Record<string, { bg: string; border: string; text: string }> = {
  YOUTUBE: {
    bg: 'bg-red-200/60 dark:bg-red-900/40',
    border: 'border-red-400 dark:border-red-600',
    text: 'text-red-800 dark:text-red-300',
  },
  TIKTOK: {
    bg: 'bg-pink-200/60 dark:bg-pink-900/40',
    border: 'border-pink-400 dark:border-pink-600',
    text: 'text-pink-800 dark:text-pink-300',
  },
  INSTAGRAM: {
    bg: 'bg-purple-200/60 dark:bg-purple-900/40',
    border: 'border-purple-400 dark:border-purple-600',
    text: 'text-purple-800 dark:text-purple-300',
  },
  NAVER_CLIP: {
    bg: 'bg-green-200/60 dark:bg-green-900/40',
    border: 'border-green-400 dark:border-green-600',
    text: 'text-green-800 dark:text-green-300',
  },
}

const defaultColor = {
  bg: 'bg-gray-200/60 dark:bg-gray-700/40',
  border: 'border-gray-400 dark:border-gray-600',
  text: 'text-gray-800 dark:text-gray-300',
}

const colorA = computed(() => platformColors[props.result.platformA] ?? defaultColor)
const colorB = computed(() => platformColors[props.result.platformB] ?? defaultColor)
const labelA = computed(() => platformLabels[props.result.platformA] ?? props.result.platformA)
const labelB = computed(() => platformLabels[props.result.platformB] ?? props.result.platformB)

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-6 shadow-sm">
    <!-- Title -->
    <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4 text-center">
      {{ labelA }} &harr; {{ labelB }} 오디언스 오버랩
    </h3>

    <!-- Venn Diagram (CSS circles) -->
    <div class="relative flex items-center justify-center h-48 mb-4">
      <!-- Circle A -->
      <div
        :class="[
          'absolute w-40 h-40 rounded-full border-2 flex items-center justify-center',
          colorA.bg, colorA.border,
        ]"
        style="left: calc(50% - 120px);"
      >
        <div class="text-center -ml-8">
          <p :class="['text-xs font-medium', colorA.text]">{{ labelA }}</p>
          <p :class="['text-lg font-bold', colorA.text]">{{ formatNumber(result.uniqueToA) }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">고유</p>
        </div>
      </div>

      <!-- Circle B -->
      <div
        :class="[
          'absolute w-40 h-40 rounded-full border-2 flex items-center justify-center',
          colorB.bg, colorB.border,
        ]"
        style="left: calc(50% - 20px);"
      >
        <div class="text-center ml-8">
          <p :class="['text-xs font-medium', colorB.text]">{{ labelB }}</p>
          <p :class="['text-lg font-bold', colorB.text]">{{ formatNumber(result.uniqueToB) }}</p>
          <p class="text-xs text-gray-500 dark:text-gray-400">고유</p>
        </div>
      </div>

      <!-- Overlap center -->
      <div class="absolute z-10 text-center" style="left: calc(50% - 30px); width: 60px;">
        <p class="text-lg font-bold text-blue-700 dark:text-blue-300">{{ formatNumber(result.sharedAudience) }}</p>
        <p class="text-xs text-blue-600 dark:text-blue-400">공유</p>
        <p class="text-xs font-semibold text-blue-800 dark:text-blue-200 mt-0.5">
          {{ result.overlapPercent.toFixed(1) }}%
        </p>
      </div>
    </div>

    <!-- Legend -->
    <div class="flex items-center justify-center gap-6 text-xs text-gray-500 dark:text-gray-400">
      <div class="flex items-center gap-1.5">
        <span :class="['w-3 h-3 rounded-full border', colorA.bg, colorA.border]"></span>
        {{ labelA }} 고유 오디언스
      </div>
      <div class="flex items-center gap-1.5">
        <span :class="['w-3 h-3 rounded-full border', colorB.bg, colorB.border]"></span>
        {{ labelB }} 고유 오디언스
      </div>
      <div class="flex items-center gap-1.5">
        <span class="w-3 h-3 rounded-full bg-blue-300 dark:bg-blue-700 border border-blue-400 dark:border-blue-500"></span>
        공유 오디언스
      </div>
    </div>
  </div>
</template>
