<template>
  <div class="card">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('prediction.historyTitle') }}
      </h3>
      <div v-if="summary" class="flex items-center gap-3 text-xs">
        <span class="text-gray-500 dark:text-gray-400">
          {{ $t('prediction.totalPredictions') }}: <span class="font-semibold text-gray-900 dark:text-gray-100">{{ summary.totalPredictions }}</span>
        </span>
        <span class="text-gray-500 dark:text-gray-400">
          {{ $t('prediction.avgAccuracy') }}: <span class="font-semibold" :class="accuracyColor(summary.avgAccuracy)">{{ summary.avgAccuracy }}%</span>
        </span>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-12">
      <LoadingSpinner />
    </div>

    <div v-else-if="history.length === 0" class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500">
      <ClockIcon class="h-12 w-12 mb-3" />
      <p class="text-sm">{{ $t('prediction.noHistory') }}</p>
    </div>

    <div v-else class="overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-200 dark:border-gray-700 text-xs uppercase text-gray-500 dark:text-gray-400">
            <th class="whitespace-nowrap px-3 py-3 font-medium">{{ $t('prediction.table.video') }}</th>
            <th class="whitespace-nowrap px-3 py-3 font-medium">{{ $t('prediction.table.platform') }}</th>
            <th class="whitespace-nowrap px-3 py-3 font-medium text-right">{{ $t('prediction.table.predicted') }}</th>
            <th class="whitespace-nowrap px-3 py-3 font-medium text-right">{{ $t('prediction.table.actual') }}</th>
            <th class="whitespace-nowrap px-3 py-3 font-medium text-right">{{ $t('prediction.table.accuracy') }}</th>
            <th class="hidden whitespace-nowrap px-3 py-3 font-medium tablet:table-cell">{{ $t('prediction.table.date') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
          <tr
            v-for="item in history"
            :key="item.id"
            class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
          >
            <td class="px-3 py-3">
              <p class="truncate max-w-[200px] font-medium text-gray-900 dark:text-gray-100" :title="item.videoTitle">
                {{ item.videoTitle }}
              </p>
            </td>
            <td class="px-3 py-3">
              <PlatformBadge :platform="item.platform" />
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right">
              <div class="text-gray-700 dark:text-gray-300">{{ formatCompact(item.predictedViews) }}</div>
              <div class="text-xs text-gray-400 dark:text-gray-500">
                {{ formatCompact(item.predictedLikes) }} {{ $t('prediction.likesShort') }}
              </div>
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right">
              <div class="text-gray-700 dark:text-gray-300">{{ formatCompact(item.actualViews) }}</div>
              <div class="text-xs text-gray-400 dark:text-gray-500">
                {{ formatCompact(item.actualLikes) }} {{ $t('prediction.likesShort') }}
              </div>
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right">
              <div class="flex items-center justify-end gap-2">
                <!-- Accuracy mini bar -->
                <div class="hidden h-1.5 w-16 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700 tablet:block">
                  <div
                    class="h-full rounded-full transition-all"
                    :class="accuracyBarColor(item.accuracyScore)"
                    :style="{ width: `${item.accuracyScore}%` }"
                  />
                </div>
                <span class="font-semibold" :class="accuracyColor(item.accuracyScore)">
                  {{ item.accuracyScore }}%
                </span>
              </div>
            </td>
            <td class="hidden whitespace-nowrap px-3 py-3 text-gray-500 dark:text-gray-400 tablet:table-cell">
              {{ formatDate(item.createdAt) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ClockIcon } from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import type { PredictionHistoryItem, PredictionSummary } from '@/types/prediction'

defineProps<{
  history: PredictionHistoryItem[]
  summary: PredictionSummary | null
  loading?: boolean
}>()

function accuracyColor(score: number): string {
  if (score >= 80) return 'text-green-600 dark:text-green-400'
  if (score >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
}

function accuracyBarColor(score: number): string {
  if (score >= 80) return 'bg-green-500'
  if (score >= 60) return 'bg-yellow-500'
  return 'bg-red-500'
}

function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}
</script>
