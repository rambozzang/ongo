<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
} from '@heroicons/vue/24/outline'
import type { RevenueStream } from '@/types/revenueAnalyzer'
import RevenueSourceBadge from './RevenueSourceBadge.vue'

const props = defineProps<{
  stream: RevenueStream
}>()

const formattedAmount = computed(() => {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: props.stream.currency || 'KRW',
    maximumFractionDigits: 0,
  }).format(props.stream.amount)
})

const growthClass = computed(() => {
  if (props.stream.growth > 0) return 'text-green-600 dark:text-green-400'
  if (props.stream.growth < 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-500 dark:text-gray-400'
})

const growthPrefix = computed(() => (props.stream.growth > 0 ? '+' : ''))

const isPositive = computed(() => props.stream.growth >= 0)
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all duration-200 hover:shadow-lg dark:border-gray-700 dark:bg-gray-900 dark:hover:shadow-gray-900/50">
    <!-- Header: Channel name + Source badge -->
    <div class="mb-3 flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {{ stream.channelName }}
      </h3>
      <RevenueSourceBadge :source="stream.source" />
    </div>

    <!-- Amount -->
    <div class="mb-3">
      <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
        {{ formattedAmount }}
      </p>
      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ stream.period }}
      </p>
    </div>

    <!-- Growth indicator -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center gap-1.5">
        <component
          :is="isPositive ? ArrowTrendingUpIcon : ArrowTrendingDownIcon"
          class="h-4 w-4"
          :class="growthClass"
        />
        <span :class="growthClass" class="text-sm font-semibold">
          {{ growthPrefix }}{{ stream.growth }}%
        </span>
      </div>
      <span class="text-xs text-gray-400 dark:text-gray-500">전월 대비</span>
    </div>
  </div>
</template>
