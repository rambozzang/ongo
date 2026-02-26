<script setup lang="ts">
import { computed } from 'vue'
import {
  ClockIcon,
  PlayIcon,
  ArrowPathIcon,
  ChatBubbleLeftIcon,
  ShareIcon,
} from '@heroicons/vue/24/outline'
import type { FanBehavior } from '@/types/fanInsights'

interface Props {
  behavior: FanBehavior
}

const props = defineProps<Props>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.behavior.platform] || props.behavior.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.behavior.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formattedActiveHour = computed(() => {
  const h = props.behavior.activeHour
  return `${h}:00`
})

const stats = computed(() => [
  {
    icon: ClockIcon,
    label: '활동 시간',
    value: `${formattedActiveHour.value} (${props.behavior.activeDay})`,
    color: 'text-blue-500',
  },
  {
    icon: PlayIcon,
    label: '평균 시청 시간',
    value: `${props.behavior.watchDuration}분`,
    color: 'text-green-500',
  },
  {
    icon: ArrowPathIcon,
    label: '재방문률',
    value: `${props.behavior.returnRate}%`,
    color: 'text-purple-500',
  },
  {
    icon: ChatBubbleLeftIcon,
    label: '댓글률',
    value: `${props.behavior.commentRate}%`,
    color: 'text-orange-500',
  },
  {
    icon: ShareIcon,
    label: '공유율',
    value: `${props.behavior.shareRate}%`,
    color: 'text-pink-500',
  },
])
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="mb-4 flex items-center gap-2">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
    </div>

    <!-- Stats Grid -->
    <div class="grid grid-cols-2 gap-4 tablet:grid-cols-3">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="rounded-lg bg-gray-50 p-3 dark:bg-gray-900/50"
      >
        <div class="flex items-center gap-1.5 mb-1">
          <component :is="stat.icon" :class="['h-4 w-4', stat.color]" />
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ stat.label }}</span>
        </div>
        <p class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ stat.value }}
        </p>
      </div>
    </div>
  </div>
</template>
