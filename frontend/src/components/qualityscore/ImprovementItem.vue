<template>
  <div
    class="flex items-start gap-3 rounded-lg border border-gray-100 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800/50"
  >
    <!-- Priority badge -->
    <span
      class="mt-0.5 inline-flex flex-shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-semibold"
      :class="priorityBadgeClass"
    >
      {{ $t(`qualityScore.priority.${improvement.priority}`) }}
    </span>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ improvement.action }}
      </p>
      <div class="mt-1 flex items-center gap-1.5">
        <ArrowTrendingUpIcon class="h-3.5 w-3.5 text-green-500 dark:text-green-400" />
        <span class="text-xs font-medium text-green-600 dark:text-green-400">
          {{ improvement.expectedImpact }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowTrendingUpIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  improvement: {
    action: string
    expectedImpact: string
    priority: 'HIGH' | 'MEDIUM' | 'LOW'
  }
}>()

const priorityBadgeMap: Record<string, string> = {
  HIGH: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  MEDIUM: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
  LOW: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
}

const priorityBadgeClass = computed(() =>
  priorityBadgeMap[props.improvement.priority] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
)
</script>
