<script setup lang="ts">
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
} from '@heroicons/vue/24/outline'
import type { HealthTrend } from '@/types/channelHealth'

defineProps<{
  trend: HealthTrend
}>()

const categoryConfig: Record<string, { label: string; color: string }> = {
  GROWTH: { label: '성장', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300' },
  ENGAGEMENT: { label: '참여도', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300' },
  CONSISTENCY: { label: '일관성', color: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300' },
  AUDIENCE: { label: '오디언스', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300' },
  MONETIZATION: { label: '수익화', color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300' },
}

function scoreColor(score: number): string {
  if (score >= 80) return 'text-green-600 dark:text-green-400'
  if (score >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}
</script>

<template>
  <tr class="border-b border-gray-100 last:border-0 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-800/50">
    <!-- Category -->
    <td class="px-4 py-3">
      <span
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
        :class="categoryConfig[trend.category]?.color"
      >
        {{ categoryConfig[trend.category]?.label ?? trend.category }}
      </span>
    </td>

    <!-- Date -->
    <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
      {{ formatDate(trend.date) }}
    </td>

    <!-- Score -->
    <td class="px-4 py-3">
      <span class="text-sm font-bold" :class="scoreColor(trend.score)">
        {{ trend.score }}
      </span>
    </td>

    <!-- Change -->
    <td class="px-4 py-3">
      <div class="flex items-center gap-1">
        <ArrowTrendingUpIcon
          v-if="trend.change > 0"
          class="h-4 w-4 text-green-500"
        />
        <ArrowTrendingDownIcon
          v-else-if="trend.change < 0"
          class="h-4 w-4 text-red-500"
        />
        <MinusIcon
          v-else
          class="h-4 w-4 text-gray-400"
        />
        <span
          class="text-sm font-medium"
          :class="
            trend.change > 0
              ? 'text-green-600 dark:text-green-400'
              : trend.change < 0
                ? 'text-red-600 dark:text-red-400'
                : 'text-gray-500 dark:text-gray-400'
          "
        >
          {{ trend.change > 0 ? '+' : '' }}{{ trend.change.toFixed(1) }}%
        </span>
      </div>
    </td>

    <!-- Recommendation -->
    <td class="px-4 py-3">
      <span v-if="trend.recommendation" class="text-sm text-gray-700 dark:text-gray-300">
        {{ trend.recommendation }}
      </span>
      <span v-else class="text-sm text-gray-400 dark:text-gray-500">-</span>
    </td>
  </tr>
</template>
