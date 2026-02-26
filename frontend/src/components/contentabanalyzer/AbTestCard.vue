<script setup lang="ts">
import { computed } from 'vue'
import {
  CalendarIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import type { ContentAbTest } from '@/types/contentAbAnalyzer'
import WinnerBadge from './WinnerBadge.vue'

interface Props {
  test: ContentAbTest
}

interface Emits {
  (e: 'select', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string; animate: boolean }> = {
    RUNNING: {
      label: '진행중',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
      animate: true,
    },
    COMPLETED: {
      label: '완료',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
      animate: false,
    },
    PAUSED: {
      label: '일시정지',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
      animate: false,
    },
  }
  return configs[props.test.status] ?? configs.PAUSED
})

const durationText = computed(() => {
  const start = new Date(props.test.startDate).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
  })
  const end = props.test.endDate
    ? new Date(props.test.endDate).toLocaleDateString('ko-KR', {
        month: 'short',
        day: 'numeric',
      })
    : '진행중'
  return `${start} ~ ${end}`
})
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer"
    @click="emit('select', test.id)"
  >
    <!-- Header -->
    <div class="flex items-start justify-between mb-3">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate flex-1 min-w-0 mr-2">
        {{ test.title }}
      </h3>
      <span
        :class="[
          'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium flex-shrink-0',
          statusConfig.color,
          statusConfig.animate ? 'animate-pulse' : '',
        ]"
      >
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Winner & Confidence -->
    <div class="flex items-center gap-3 mb-3">
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-500 dark:text-gray-400">우승자:</span>
        <WinnerBadge :winner="test.winner" />
      </div>
      <div class="flex items-center gap-1">
        <ChartBarIcon class="w-4 h-4 text-gray-400 dark:text-gray-500" />
        <span class="text-xs text-gray-500 dark:text-gray-400">신뢰도</span>
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ test.confidence.toFixed(1) }}%
        </span>
      </div>
    </div>

    <!-- Confidence progress bar -->
    <div class="mb-3">
      <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="
            test.confidence >= 90
              ? 'bg-green-500 dark:bg-green-400'
              : test.confidence >= 70
                ? 'bg-blue-500 dark:bg-blue-400'
                : 'bg-yellow-500 dark:bg-yellow-400'
          "
          :style="{ width: `${test.confidence}%` }"
        ></div>
      </div>
    </div>

    <!-- Variant labels -->
    <div class="flex items-center gap-2 mb-3">
      <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-900/20 dark:text-blue-300">
        A: {{ test.variantA.label }}
      </span>
      <span class="text-xs text-gray-400">vs</span>
      <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-orange-50 text-orange-700 dark:bg-orange-900/20 dark:text-orange-300">
        B: {{ test.variantB.label }}
      </span>
    </div>

    <!-- Duration -->
    <div class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
      <CalendarIcon class="w-3.5 h-3.5" />
      <span>{{ durationText }}</span>
    </div>
  </div>
</template>
