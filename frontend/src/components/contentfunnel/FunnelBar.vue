<template>
  <div class="flex items-center gap-3">
    <!-- Stage name -->
    <div class="w-28 flex-shrink-0 text-right">
      <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ stage.name }}</p>
    </div>

    <!-- Bar -->
    <div class="relative flex-1">
      <div class="h-10 w-full overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
        <div
          class="flex h-full items-center rounded-lg transition-all duration-500"
          :class="barColor"
          :style="{ width: `${Math.max(stage.rate, 2)}%` }"
        >
          <span
            v-if="stage.rate >= 10"
            class="ml-3 text-xs font-semibold text-white"
          >
            {{ stage.count.toLocaleString('ko-KR') }}
          </span>
        </div>
      </div>
    </div>

    <!-- Rate & Drop off -->
    <div class="w-20 flex-shrink-0 text-right">
      <p class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ stage.rate.toFixed(1) }}%</p>
      <p v-if="stage.dropOff > 0" class="text-xs text-red-500 dark:text-red-400">
        -{{ stage.dropOff.toFixed(1) }}%
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FunnelStage } from '@/types/contentFunnel'

const props = defineProps<{
  stage: FunnelStage
  index: number
}>()

const barColor = computed(() => {
  const colors = [
    'bg-primary-500',
    'bg-primary-400',
    'bg-primary-300 dark:bg-primary-500/70',
    'bg-primary-200 dark:bg-primary-500/50',
    'bg-primary-100 dark:bg-primary-500/30',
  ]
  return colors[Math.min(props.index, colors.length - 1)]
})
</script>
