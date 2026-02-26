<template>
  <div class="group">
    <div class="mb-1 flex items-center justify-between">
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ option.text }}
      </span>
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ option.voteCount.toLocaleString('ko-KR') }}{{ $t('fanPoll.votesUnit') }}
        </span>
        <span class="min-w-[3rem] text-right text-sm font-bold" :class="percentageColor">
          {{ option.percentage.toFixed(1) }}%
        </span>
      </div>
    </div>
    <div class="h-3 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
      <div
        class="h-full rounded-full transition-all duration-700 ease-out"
        :class="barColor"
        :style="{ width: `${Math.min(option.percentage, 100)}%` }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PollOption } from '@/types/fanPoll'

const props = defineProps<{
  option: PollOption
  rank?: number
  colorIndex?: number
}>()

const barColors = [
  'bg-primary-500',
  'bg-blue-500',
  'bg-purple-500',
  'bg-amber-500',
  'bg-emerald-500',
  'bg-pink-500',
  'bg-cyan-500',
  'bg-orange-500',
]

const barColor = computed(() => {
  const idx = props.colorIndex ?? 0
  return barColors[idx % barColors.length]
})

const percentageColor = computed(() => {
  if (props.rank === 1) return 'text-primary-600 dark:text-primary-400'
  return 'text-gray-700 dark:text-gray-300'
})
</script>
