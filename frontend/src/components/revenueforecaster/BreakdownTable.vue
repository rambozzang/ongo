<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-gray-200 dark:border-gray-700">
          <th class="pb-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.breakdown.source') }}
          </th>
          <th class="pb-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.breakdown.currentMonthly') }}
          </th>
          <th class="pb-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.breakdown.forecastMonthly') }}
          </th>
          <th class="pb-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('revenueForecaster.breakdown.growthRate') }}
          </th>
          <th class="pb-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 pl-4 min-w-[140px]">
            {{ $t('revenueForecaster.breakdown.share') }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in breakdowns"
          :key="item.source"
          class="border-b border-gray-100 dark:border-gray-800 last:border-0"
        >
          <!-- Source Name -->
          <td class="py-3">
            <div class="flex items-center gap-2">
              <span
                class="h-2.5 w-2.5 flex-shrink-0 rounded-full"
                :style="{ backgroundColor: sourceColor(item.source) }"
              />
              <span class="font-medium text-gray-900 dark:text-gray-100">
                {{ sourceLabel(item.source) }}
              </span>
            </div>
          </td>

          <!-- Current Monthly -->
          <td class="py-3 text-right text-gray-600 dark:text-gray-300">
            {{ formatRevenue(item.currentMonthly) }}
          </td>

          <!-- Forecast Monthly -->
          <td class="py-3 text-right font-medium text-gray-900 dark:text-gray-100">
            {{ formatRevenue(item.forecastMonthly) }}
          </td>

          <!-- Growth Rate -->
          <td class="py-3 text-right">
            <span
              class="inline-flex items-center gap-0.5 font-medium"
              :class="item.growthRate >= 0
                ? 'text-green-600 dark:text-green-400'
                : 'text-red-600 dark:text-red-400'"
            >
              <ArrowUpIcon v-if="item.growthRate >= 0" class="h-3 w-3" />
              <ArrowDownIcon v-else class="h-3 w-3" />
              {{ item.growthRate >= 0 ? '+' : '' }}{{ item.growthRate }}%
            </span>
          </td>

          <!-- Share with bar -->
          <td class="py-3 pl-4">
            <div class="flex items-center gap-2">
              <div class="h-2 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                <div
                  class="h-2 rounded-full transition-all duration-500"
                  :style="{
                    width: `${item.share}%`,
                    backgroundColor: sourceColor(item.source),
                  }"
                />
              </div>
              <span class="w-10 text-right text-xs text-gray-500 dark:text-gray-400">
                {{ item.share }}%
              </span>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowUpIcon, ArrowDownIcon } from '@heroicons/vue/24/outline'
import type { RevenueBreakdown, RevenueSource } from '@/types/revenueForecaster'

const { t } = useI18n({ useScope: 'global' })

defineProps<{
  breakdowns: RevenueBreakdown[]
}>()

const sourceColors: Record<RevenueSource, string> = {
  AD_REVENUE: '#3b82f6',
  SPONSORSHIP: '#8b5cf6',
  MERCHANDISE: '#f59e0b',
  MEMBERSHIP: '#10b981',
  SUPER_CHAT: '#ef4444',
  AFFILIATE: '#ec4899',
}

function sourceColor(source: RevenueSource): string {
  return sourceColors[source] ?? '#6b7280'
}

function sourceLabel(source: RevenueSource): string {
  return t(`revenueForecaster.breakdown.sources.${source}`)
}

function formatRevenue(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}${t('revenueForecaster.manwon')}`
  }
  return `${value.toLocaleString()}${t('revenueForecaster.won')}`
}
</script>
