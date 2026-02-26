<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleIcon,
  ClockIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import type { CalendarSuggestion } from '@/types/contentCalendarAi'

interface Props {
  suggestion: CalendarSuggestion
}

const props = defineProps<Props>()

const emit = defineEmits<{
  accept: [id: number]
}>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.suggestion.platform] || props.suggestion.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.suggestion.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const contentTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    VLOG: '브이로그',
    SHORTS: '쇼츠',
    CHALLENGE: '챌린지',
    REVIEW: '리뷰',
    REELS: '릴스',
    TUTORIAL: '튜토리얼',
    LIVE: '라이브',
  }
  return labels[props.suggestion.contentType] || props.suggestion.contentType
})

const confidenceColor = computed(() => {
  const c = props.suggestion.confidence
  if (c >= 85) return 'text-green-600 dark:text-green-400'
  if (c >= 70) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-orange-600 dark:text-orange-400'
})

const confidenceBarColor = computed(() => {
  const c = props.suggestion.confidence
  if (c >= 85) return 'bg-green-500 dark:bg-green-400'
  if (c >= 70) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-orange-500 dark:bg-orange-400'
})

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    PENDING: { label: '대기 중', color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' },
    ACCEPTED: { label: '수락됨', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
    REJECTED: { label: '거절됨', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
  }
  return configs[props.suggestion.status] || configs.PENDING
})

const formattedDate = computed(() => {
  return new Date(props.suggestion.suggestedDate).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
    weekday: 'short',
  })
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ suggestion.title }}
        </h3>
        <p class="mt-1 text-sm text-gray-600 dark:text-gray-400">
          {{ suggestion.description }}
        </p>
      </div>
      <span :class="['ml-2 inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium flex-shrink-0', statusConfig.color]">
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Date & Time -->
    <div class="mb-3 flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
      <ClockIcon class="h-4 w-4 flex-shrink-0" />
      <span>{{ formattedDate }} {{ suggestion.suggestedTime }}</span>
    </div>

    <!-- Badges -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
      <span class="inline-flex items-center rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
        {{ contentTypeLabel }}
      </span>
      <span class="inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-700 dark:text-gray-300">
        {{ suggestion.topic }}
      </span>
    </div>

    <!-- Engagement & Confidence -->
    <div class="mb-3 grid grid-cols-2 gap-3">
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900/50">
        <p class="text-xs text-gray-500 dark:text-gray-400">예상 참여율</p>
        <p class="text-lg font-bold text-gray-900 dark:text-gray-100">
          {{ suggestion.expectedEngagement }}%
        </p>
      </div>
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900/50">
        <p class="text-xs text-gray-500 dark:text-gray-400">신뢰도</p>
        <div class="flex items-center gap-2">
          <SparklesIcon :class="['h-4 w-4', confidenceColor]" />
          <span :class="['text-lg font-bold', confidenceColor]">{{ suggestion.confidence }}%</span>
        </div>
      </div>
    </div>

    <!-- Confidence Bar -->
    <div class="mb-4">
      <div class="h-1.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          :class="['h-full rounded-full transition-all duration-500', confidenceBarColor]"
          :style="{ width: `${suggestion.confidence}%` }"
        />
      </div>
    </div>

    <!-- Accept Button -->
    <button
      v-if="suggestion.status === 'PENDING'"
      @click="emit('accept', suggestion.id)"
      class="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
    >
      <CheckCircleIcon class="h-4 w-4" />
      제안 수락
    </button>
    <div
      v-else-if="suggestion.status === 'ACCEPTED'"
      class="flex items-center justify-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm font-medium text-green-700 dark:bg-green-900/20 dark:text-green-400"
    >
      <CheckCircleIcon class="h-4 w-4" />
      수락 완료
    </div>
  </div>
</template>
