<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { AlgorithmScore } from '@/types/algorithmInsights'

interface Props {
  score: AlgorithmScore
}

const props = defineProps<Props>()

const animatedScore = ref(0)

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.score.platform] || props.score.platform
})

const platformColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'border-red-300 dark:border-red-700',
    TIKTOK: 'border-gray-800 dark:border-gray-500',
    INSTAGRAM: 'border-pink-300 dark:border-pink-700',
    NAVERCLIP: 'border-green-300 dark:border-green-700',
  }
  return colors[props.score.platform] || 'border-gray-300 dark:border-gray-700'
})

const platformIconBg = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-600 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.score.platform] || 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
})

// Circular progress
const width = 100
const strokeWidth = 6
const radius = (width - strokeWidth) / 2
const circumference = 2 * Math.PI * radius
const strokeDashoffset = computed(() => {
  return circumference - (animatedScore.value / 100) * circumference
})

const overallColor = computed(() => {
  const s = props.score.overallScore
  if (s >= 80) return 'stroke-green-500 dark:stroke-green-400'
  if (s >= 60) return 'stroke-yellow-500 dark:stroke-yellow-400'
  if (s >= 40) return 'stroke-orange-500 dark:stroke-orange-400'
  return 'stroke-red-500 dark:stroke-red-400'
})

const overallTextColor = computed(() => {
  const s = props.score.overallScore
  if (s >= 80) return 'text-green-600 dark:text-green-400'
  if (s >= 60) return 'text-yellow-600 dark:text-yellow-400'
  if (s >= 40) return 'text-orange-600 dark:text-orange-400'
  return 'text-red-600 dark:text-red-400'
})

const subScores = computed(() => [
  { label: '콘텐츠', value: props.score.contentScore, color: 'bg-blue-500 dark:bg-blue-400' },
  { label: '참여', value: props.score.engagementScore, color: 'bg-green-500 dark:bg-green-400' },
  { label: '메타데이터', value: props.score.metadataScore, color: 'bg-purple-500 dark:bg-purple-400' },
  { label: '일관성', value: props.score.consistencyScore, color: 'bg-orange-500 dark:bg-orange-400' },
  { label: '오디언스', value: props.score.audienceScore, color: 'bg-pink-500 dark:bg-pink-400' },
])

const getBarColor = (value: number): string => {
  if (value >= 80) return 'bg-green-500 dark:bg-green-400'
  if (value >= 60) return 'bg-yellow-500 dark:bg-yellow-400'
  if (value >= 40) return 'bg-orange-500 dark:bg-orange-400'
  return 'bg-red-500 dark:bg-red-400'
}

let animationTimeout: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  animationTimeout = setTimeout(() => {
    animatedScore.value = Math.min(props.score.overallScore, 100)
  }, 100)
})

onUnmounted(() => {
  if (animationTimeout) clearTimeout(animationTimeout)
})
</script>

<template>
  <div
    :class="['rounded-xl border-2 bg-white p-5 shadow-sm dark:bg-gray-800', platformColor]"
  >
    <!-- Platform Header -->
    <div class="mb-4 flex items-center gap-3">
      <div :class="['rounded-lg px-3 py-1 text-sm font-semibold', platformIconBg]">
        {{ platformLabel }}
      </div>
    </div>

    <!-- Overall Score Circle -->
    <div class="mb-4 flex items-center justify-center">
      <div class="relative inline-flex items-center justify-center">
        <svg :width="width" :height="width" class="transform -rotate-90">
          <circle
            :cx="width / 2"
            :cy="width / 2"
            :r="radius"
            class="stroke-gray-200 dark:stroke-gray-700"
            :stroke-width="strokeWidth"
            fill="none"
          />
          <circle
            :cx="width / 2"
            :cy="width / 2"
            :r="radius"
            :class="overallColor"
            :stroke-width="strokeWidth"
            fill="none"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="strokeDashoffset"
            stroke-linecap="round"
            class="transition-all duration-1000 ease-out"
          />
        </svg>
        <div class="absolute inset-0 flex flex-col items-center justify-center">
          <span :class="['text-2xl font-bold', overallTextColor]">{{ score.overallScore }}</span>
          <span class="text-xs text-gray-500 dark:text-gray-400">전체 점수</span>
        </div>
      </div>
    </div>

    <!-- Sub Scores -->
    <div class="space-y-3">
      <div v-for="sub in subScores" :key="sub.label" class="flex items-center gap-3">
        <span class="w-16 flex-shrink-0 text-xs font-medium text-gray-600 dark:text-gray-400">
          {{ sub.label }}
        </span>
        <div class="flex-1 h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            :class="['h-full rounded-full transition-all duration-500', getBarColor(sub.value)]"
            :style="{ width: `${sub.value}%` }"
          />
        </div>
        <span class="w-8 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
          {{ sub.value }}
        </span>
      </div>
    </div>
  </div>
</template>
