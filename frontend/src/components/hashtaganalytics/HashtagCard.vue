<script setup lang="ts">
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  HashtagIcon,
  EyeIcon,
  HeartIcon,
} from '@heroicons/vue/24/outline'
import type { HashtagPerformance } from '@/types/hashtagAnalytics'

defineProps<{
  performance: HashtagPerformance
}>()

const emit = defineEmits<{
  click: [id: number]
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const trendConfig: Record<string, { icon: typeof ArrowTrendingUpIcon; color: string }> = {
  UP: { icon: ArrowTrendingUpIcon, color: 'text-green-500' },
  DOWN: { icon: ArrowTrendingDownIcon, color: 'text-red-500' },
  STABLE: { icon: MinusIcon, color: 'text-gray-400' },
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK
const getTrendConfig = (dir: string) => trendConfig[dir] ?? trendConfig.STABLE

const formatNumber = (n: number) => n.toLocaleString('ko-KR')
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('click', performance.id)"
  >
    <!-- Header: Hashtag name + badges -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <div class="flex items-center gap-1.5">
        <HashtagIcon class="h-4 w-4 text-primary-500" />
        <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ performance.hashtag }}
        </span>
      </div>

      <span
        :class="[getPlatformStyle(performance.platform).bg, getPlatformStyle(performance.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ performance.platform }}
      </span>

      <span class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        {{ performance.category }}
      </span>
    </div>

    <!-- Metrics -->
    <div class="mb-3 grid grid-cols-3 gap-3">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">사용 횟수</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(performance.usageCount) }}
        </p>
      </div>
      <div>
        <div class="flex items-center gap-1">
          <EyeIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">총 조회수</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(performance.totalViews) }}
        </p>
      </div>
      <div>
        <div class="flex items-center gap-1">
          <HeartIcon class="h-3 w-3 text-gray-400" />
          <p class="text-xs text-gray-500 dark:text-gray-400">참여율</p>
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ performance.avgEngagement.toFixed(1) }}%
        </p>
      </div>
    </div>

    <!-- Trend + Growth -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center gap-1.5">
        <component
          :is="getTrendConfig(performance.trendDirection).icon"
          class="h-4 w-4"
          :class="getTrendConfig(performance.trendDirection).color"
        />
        <span
          class="text-xs font-semibold"
          :class="getTrendConfig(performance.trendDirection).color"
        >
          {{ performance.trendDirection === 'UP' ? '상승' : performance.trendDirection === 'DOWN' ? '하락' : '유지' }}
        </span>
      </div>
      <span
        class="text-xs font-semibold"
        :class="performance.growthRate >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
      >
        {{ performance.growthRate >= 0 ? '+' : '' }}{{ performance.growthRate.toFixed(1) }}%
      </span>
    </div>
  </div>
</template>
