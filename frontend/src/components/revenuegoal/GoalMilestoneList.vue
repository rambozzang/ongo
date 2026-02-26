<script setup lang="ts">
import { CheckCircleIcon } from '@heroicons/vue/24/solid'
import type { RevenueGoalMilestone } from '@/types/revenueGoal'

defineProps<{
  milestones: RevenueGoalMilestone[]
}>()

const formatAmount = (value: number): string => {
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}억원`
  if (value >= 10000) return `${(value / 10000).toFixed(0)}만원`
  return `${value.toLocaleString('ko-KR')}원`
}

const formatDate = (iso?: string): string => {
  if (!iso) return ''
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="space-y-1.5">
    <div
      v-for="milestone in milestones"
      :key="milestone.id"
      class="flex items-center gap-3 rounded-lg px-3 py-2 transition-colors"
      :class="
        milestone.reached
          ? 'bg-green-50 dark:bg-green-900/10'
          : 'bg-gray-50 dark:bg-gray-800/50'
      "
    >
      <!-- Check icon -->
      <div class="flex-shrink-0">
        <CheckCircleIcon
          v-if="milestone.reached"
          class="h-5 w-5 text-green-500 dark:text-green-400"
        />
        <div
          v-else
          class="h-5 w-5 rounded-full border-2 border-gray-300 dark:border-gray-600"
        />
      </div>

      <!-- Label -->
      <div class="min-w-0 flex-1">
        <p
          class="text-sm font-medium"
          :class="
            milestone.reached
              ? 'text-green-800 dark:text-green-200 line-through decoration-green-400/50'
              : 'text-gray-800 dark:text-gray-200'
          "
        >
          {{ milestone.label }}
        </p>
      </div>

      <!-- Amount -->
      <span class="flex-shrink-0 text-xs font-medium text-gray-500 dark:text-gray-400">
        {{ formatAmount(milestone.targetAmount) }}
      </span>

      <!-- Reached date -->
      <span
        v-if="milestone.reached && milestone.reachedAt"
        class="flex-shrink-0 text-[10px] text-green-600 dark:text-green-400"
      >
        {{ formatDate(milestone.reachedAt) }}
      </span>
    </div>

    <!-- Empty state -->
    <div
      v-if="milestones.length === 0"
      class="rounded-lg bg-gray-50 px-4 py-6 text-center text-sm text-gray-400 dark:bg-gray-800/50 dark:text-gray-500"
    >
      등록된 마일스톤이 없습니다
    </div>
  </div>
</template>
