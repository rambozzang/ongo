<script setup lang="ts">
import type { OverlapSegment } from '@/types/audienceOverlap'

interface Props {
  segment: OverlapSegment
}

defineProps<Props>()

const platformBadges: Record<string, { label: string; color: string }> = {
  YOUTUBE: {
    label: 'YouTube',
    color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  },
  TIKTOK: {
    label: 'TikTok',
    color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  },
  INSTAGRAM: {
    label: 'Instagram',
    color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  },
  NAVER_CLIP: {
    label: 'Naver Clip',
    color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  },
}

function getBadge(platform: string) {
  return platformBadges[platform] ?? { label: platform, color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="flex items-center gap-4 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors rounded-lg">
    <!-- Segment name & interest -->
    <div class="flex-1 min-w-0">
      <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
        {{ segment.name }}
      </p>
      <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
        관심사: {{ segment.topInterest }}
      </p>
    </div>

    <!-- Platform badges -->
    <div class="flex flex-wrap gap-1 flex-shrink-0">
      <span
        v-for="platform in segment.platforms"
        :key="platform"
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', getBadge(platform).color]"
      >
        {{ getBadge(platform).label }}
      </span>
    </div>

    <!-- Audience size -->
    <div class="flex-shrink-0 w-20 text-right">
      <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ formatNumber(segment.audienceSize) }}
      </p>
      <p class="text-xs text-gray-500 dark:text-gray-400">사이즈</p>
    </div>

    <!-- Engagement rate -->
    <div class="flex-shrink-0 w-16 text-right">
      <p class="text-sm font-bold" :class="segment.engagementRate >= 5 ? 'text-green-600 dark:text-green-400' : 'text-gray-900 dark:text-gray-100'">
        {{ segment.engagementRate.toFixed(1) }}%
      </p>
      <p class="text-xs text-gray-500 dark:text-gray-400">참여율</p>
    </div>
  </div>
</template>
