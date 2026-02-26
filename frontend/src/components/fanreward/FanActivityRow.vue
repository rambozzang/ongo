<script setup lang="ts">
import {
  ChatBubbleLeftIcon,
  HeartIcon,
  ShareIcon,
  EyeIcon,
  BellIcon,
} from '@heroicons/vue/24/outline'
import type { FanActivity } from '@/types/fanReward'

interface Props {
  activity: FanActivity
}

defineProps<Props>()

const activityConfig: Record<string, { icon: typeof ChatBubbleLeftIcon; label: string; class: string }> = {
  COMMENT: {
    icon: ChatBubbleLeftIcon,
    label: '댓글',
    class: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30',
  },
  LIKE: {
    icon: HeartIcon,
    label: '좋아요',
    class: 'text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-900/30',
  },
  SHARE: {
    icon: ShareIcon,
    label: '공유',
    class: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30',
  },
  WATCH: {
    icon: EyeIcon,
    label: '시청',
    class: 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30',
  },
  SUBSCRIBE: {
    icon: BellIcon,
    label: '구독',
    class: 'text-yellow-600 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-900/30',
  },
}

function formatTimeAgo(iso: string): string {
  const now = Date.now()
  const target = new Date(iso).getTime()
  const diff = now - target
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  const days = Math.floor(hours / 24)
  return `${days}일 전`
}
</script>

<template>
  <div
    class="flex items-center gap-4 rounded-lg border border-gray-100 bg-white px-4 py-3 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-900 dark:hover:bg-gray-800"
  >
    <!-- Activity Icon -->
    <div
      :class="activityConfig[activity.activityType].class"
      class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg"
    >
      <component :is="activityConfig[activity.activityType].icon" class="h-5 w-5" />
    </div>

    <!-- Fan Name + Description -->
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2">
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ activity.fanName }}
        </span>
        <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-gray-800 dark:text-gray-400">
          {{ activityConfig[activity.activityType].label }}
        </span>
      </div>
      <p class="mt-0.5 truncate text-sm text-gray-500 dark:text-gray-400">
        {{ activity.description }}
      </p>
    </div>

    <!-- Points -->
    <div class="flex-shrink-0 text-right">
      <span class="text-sm font-bold text-primary-600 dark:text-primary-400">
        +{{ activity.points }}P
      </span>
    </div>

    <!-- Time -->
    <div class="hidden flex-shrink-0 text-right tablet:block">
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ formatTimeAgo(activity.timestamp) }}
      </span>
    </div>
  </div>
</template>
