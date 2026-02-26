<script setup lang="ts">
import { computed } from 'vue'
import { UserCircleIcon } from '@heroicons/vue/24/outline'
import type { BenchmarkPeer } from '@/types/creatorBenchmark'

interface Props {
  peer: BenchmarkPeer
}

const props = defineProps<Props>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.peer.platform] || props.peer.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.peer.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formatNumber = (value: number): string => {
  if (value >= 10000) return `${(value / 10000).toFixed(1)}만`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}K`
  return value.toLocaleString()
}
</script>

<template>
  <tr class="border-b border-gray-100 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700/50">
    <!-- Creator Name -->
    <td class="px-4 py-3">
      <div class="flex items-center gap-3">
        <UserCircleIcon class="h-8 w-8 flex-shrink-0 text-gray-400 dark:text-gray-500" />
        <div class="min-w-0">
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
            {{ peer.name }}
          </p>
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ peer.category }}
          </p>
        </div>
      </div>
    </td>

    <!-- Platform -->
    <td class="px-4 py-3">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
    </td>

    <!-- Subscribers -->
    <td class="px-4 py-3 text-right">
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ formatNumber(peer.subscribers) }}
      </span>
    </td>

    <!-- Average Views -->
    <td class="px-4 py-3 text-right">
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ formatNumber(peer.avgViews) }}
      </span>
    </td>

    <!-- Engagement Rate -->
    <td class="px-4 py-3 text-right">
      <span
        :class="[
          'text-sm font-bold',
          peer.engagementRate >= 5
            ? 'text-green-600 dark:text-green-400'
            : peer.engagementRate >= 3
              ? 'text-yellow-600 dark:text-yellow-400'
              : 'text-red-600 dark:text-red-400',
        ]"
      >
        {{ peer.engagementRate }}%
      </span>
    </td>
  </tr>
</template>
