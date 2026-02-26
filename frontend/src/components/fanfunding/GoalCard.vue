<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: title + badge + delete -->
    <div class="mb-3 flex items-start justify-between gap-2">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ goal.title }}
        </h3>
      </div>
      <div class="flex flex-shrink-0 items-center gap-2">
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="
            goal.isActive
              ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
              : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400'
          "
        >
          {{ goal.isActive ? $t('fanFunding.goalActive') : $t('fanFunding.goalInactive') }}
        </span>
        <button
          class="rounded p-1 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
          :title="$t('action.delete')"
          @click="$emit('delete', goal.id)"
        >
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Progress bar -->
    <div class="mb-2">
      <div class="mb-1 flex items-center justify-between text-xs">
        <span class="font-medium text-gray-700 dark:text-gray-300">{{ percentage.toFixed(1) }}%</span>
        <span class="text-gray-500 dark:text-gray-400">
          {{ formatKRW(goal.currentAmount) }} / {{ formatKRW(goal.targetAmount) }}
        </span>
      </div>
      <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="progressBarColor"
          :style="{ width: `${Math.min(percentage, 100)}%` }"
        />
      </div>
    </div>

    <!-- Deadline -->
    <div v-if="goal.deadline" class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
      <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      <span>{{ $t('fanFunding.goalDeadline') }}: {{ goal.deadline }}</span>
      <span v-if="daysLeft !== null" class="ml-1" :class="daysLeft <= 7 ? 'text-red-500 dark:text-red-400' : ''">
        ({{ daysLeft > 0 ? $t('fanFunding.daysLeft', { n: daysLeft }) : $t('fanFunding.expired') }})
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FundingGoal } from '@/types/fanFunding'

const props = defineProps<{
  goal: FundingGoal
}>()

defineEmits<{
  delete: [id: number]
}>()

const percentage = computed(() => {
  if (props.goal.targetAmount === 0) return 0
  return (props.goal.currentAmount / props.goal.targetAmount) * 100
})

const progressBarColor = computed(() => {
  if (percentage.value >= 100) return 'bg-green-500'
  if (percentage.value >= 60) return 'bg-primary-500'
  if (percentage.value >= 30) return 'bg-yellow-500'
  return 'bg-orange-500'
})

const daysLeft = computed(() => {
  if (!props.goal.deadline) return null
  const now = new Date()
  const deadline = new Date(props.goal.deadline)
  return Math.ceil((deadline.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
})

function formatKRW(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(0)}만원`
  }
  return `${value.toLocaleString('ko-KR')}원`
}
</script>
