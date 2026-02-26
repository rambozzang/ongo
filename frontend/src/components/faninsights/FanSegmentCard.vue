<script setup lang="ts">
import { computed } from 'vue'
import {
  UserGroupIcon,
  HandThumbUpIcon,
} from '@heroicons/vue/24/outline'
import type { FanSegment } from '@/types/fanInsights'

interface Props {
  segment: FanSegment
}

const props = defineProps<Props>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.segment.platform] || props.segment.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.segment.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formattedMembers = computed(() => {
  const v = props.segment.memberCount
  if (v >= 10000) return `${(v / 10000).toFixed(1)}만`
  if (v >= 1000) return `${(v / 1000).toFixed(1)}천`
  return v.toLocaleString()
})

const engagementColor = computed(() => {
  const e = props.segment.avgEngagement
  if (e >= 10) return 'text-green-600 dark:text-green-400'
  if (e >= 5) return 'text-blue-600 dark:text-blue-400'
  return 'text-gray-600 dark:text-gray-400'
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex items-start gap-3 flex-1 min-w-0">
        <UserGroupIcon class="h-5 w-5 flex-shrink-0 mt-0.5 text-indigo-500" />
        <div class="flex-1 min-w-0">
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ segment.name }}
          </h3>
          <p class="mt-1 text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
            {{ segment.description }}
          </p>
        </div>
      </div>
    </div>

    <!-- Platform Badge -->
    <div class="mb-3">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
    </div>

    <!-- Stats -->
    <div class="mb-3 grid grid-cols-2 gap-3">
      <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-900/50">
        <div class="flex items-center gap-1.5 mb-1">
          <UserGroupIcon class="h-4 w-4 text-gray-400" />
          <span class="text-xs text-gray-500 dark:text-gray-400">멤버 수</span>
        </div>
        <p class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ formattedMembers }}명
        </p>
      </div>
      <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-900/50">
        <div class="flex items-center gap-1.5 mb-1">
          <HandThumbUpIcon class="h-4 w-4 text-gray-400" />
          <span class="text-xs text-gray-500 dark:text-gray-400">평균 참여율</span>
        </div>
        <p :class="['text-sm font-bold', engagementColor]">
          {{ segment.avgEngagement }}%
        </p>
      </div>
    </div>

    <!-- Interest Tags -->
    <div>
      <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">관심사</p>
      <div class="flex flex-wrap gap-1.5">
        <span
          v-for="interest in segment.topInterests"
          :key="interest"
          class="inline-flex items-center rounded bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300"
        >
          {{ interest }}
        </span>
      </div>
    </div>
  </div>
</template>
