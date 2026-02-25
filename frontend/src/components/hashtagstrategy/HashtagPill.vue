<template>
  <div class="group relative inline-flex">
    <span
      class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium cursor-default transition-colors"
      :class="pillClass"
    >
      <span>{{ hashtag.tag }}</span>
      <ArrowTrendingUpIcon v-if="hashtag.trend === 'RISING'" class="h-3 w-3" />
      <ArrowTrendingDownIcon v-else-if="hashtag.trend === 'DECLINING'" class="h-3 w-3" />
      <MinusIcon v-else class="h-3 w-3 opacity-50" />
    </span>

    <!-- Tooltip -->
    <div
      class="pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 -translate-x-1/2 whitespace-nowrap rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-lg opacity-0 transition-opacity group-hover:opacity-100 dark:border-gray-700 dark:bg-gray-800"
    >
      <div class="space-y-1">
        <div class="flex items-center justify-between gap-4">
          <span class="text-gray-500 dark:text-gray-400">{{ $t('hashtagStrategy.reach') }}</span>
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(hashtag.reachEstimate) }}</span>
        </div>
        <div class="flex items-center justify-between gap-4">
          <span class="text-gray-500 dark:text-gray-400">{{ $t('hashtagStrategy.competition') }}</span>
          <span class="font-semibold" :class="difficultyTextClass">{{ difficultyLabel }}</span>
        </div>
        <div class="flex items-center justify-between gap-4">
          <span class="text-gray-500 dark:text-gray-400">{{ $t('hashtagStrategy.trendLabel') }}</span>
          <span class="font-semibold" :class="trendTextClass">{{ trendLabel }}</span>
        </div>
        <div class="flex items-center justify-between gap-4">
          <span class="text-gray-500 dark:text-gray-400">{{ $t('hashtagStrategy.avgViews') }}</span>
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(hashtag.avgViews) }}</span>
        </div>
      </div>
      <div class="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-white dark:border-t-gray-800" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTrendingUpIcon, ArrowTrendingDownIcon, MinusIcon } from '@heroicons/vue/24/outline'
import type { Hashtag } from '@/types/hashtagStrategy'

const props = defineProps<{
  hashtag: Hashtag
}>()

const { t } = useI18n({ useScope: 'global' })

const pillClass = computed(() => {
  switch (props.hashtag.competition) {
    case 'EASY':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'MEDIUM':
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'HARD':
      return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400'
    case 'VERY_HARD':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
})

const difficultyTextClass = computed(() => {
  switch (props.hashtag.competition) {
    case 'EASY': return 'text-green-600 dark:text-green-400'
    case 'MEDIUM': return 'text-yellow-600 dark:text-yellow-400'
    case 'HARD': return 'text-orange-600 dark:text-orange-400'
    case 'VERY_HARD': return 'text-red-600 dark:text-red-400'
    default: return 'text-gray-600 dark:text-gray-400'
  }
})

const difficultyLabel = computed(() => {
  switch (props.hashtag.competition) {
    case 'EASY': return t('hashtagStrategy.difficultyEasy')
    case 'MEDIUM': return t('hashtagStrategy.difficultyMedium')
    case 'HARD': return t('hashtagStrategy.difficultyHard')
    case 'VERY_HARD': return t('hashtagStrategy.difficultyVeryHard')
    default: return props.hashtag.competition
  }
})

const trendTextClass = computed(() => {
  switch (props.hashtag.trend) {
    case 'RISING': return 'text-green-600 dark:text-green-400'
    case 'DECLINING': return 'text-red-600 dark:text-red-400'
    default: return 'text-gray-600 dark:text-gray-400'
  }
})

const trendLabel = computed(() => {
  switch (props.hashtag.trend) {
    case 'RISING': return t('hashtagStrategy.trendRising')
    case 'STABLE': return t('hashtagStrategy.trendStable')
    case 'DECLINING': return t('hashtagStrategy.trendDeclining')
    default: return props.hashtag.trend
  }
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toString()
}
</script>
