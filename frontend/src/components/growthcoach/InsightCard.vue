<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Impact Badge + Category -->
    <div class="mb-3 flex items-center justify-between">
      <span
        class="rounded-full px-2 py-0.5 text-xs font-medium"
        :class="impactBadgeClass"
      >
        {{ $t(`growthCoach.insights.impact${insight.impact.charAt(0) + insight.impact.slice(1).toLowerCase()}`) }}
      </span>
      <span class="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs font-medium text-gray-600 dark:text-gray-400">
        {{ insight.category }}
      </span>
    </div>

    <!-- Title + Description -->
    <h3 class="mb-1.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ insight.title }}
    </h3>
    <p class="mb-3 text-sm text-gray-600 dark:text-gray-400 leading-relaxed">
      {{ insight.description }}
    </p>

    <!-- Suggested Action -->
    <div
      v-if="insight.actionable && insight.suggestedAction"
      class="mb-3 rounded-lg bg-primary-50 dark:bg-primary-900/10 border border-primary-100 dark:border-primary-800 p-3"
    >
      <div class="flex items-start gap-2">
        <LightBulbIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-primary-500 dark:text-primary-400" />
        <div>
          <p class="text-xs font-medium text-primary-700 dark:text-primary-300">
            {{ $t('growthCoach.insights.suggestedAction') }}
          </p>
          <p class="mt-0.5 text-sm text-primary-600 dark:text-primary-400">
            {{ insight.suggestedAction }}
          </p>
        </div>
      </div>
    </div>

    <!-- Footer: Actionable badge + Created date -->
    <div class="flex items-center justify-between text-xs">
      <span
        v-if="insight.actionable"
        class="inline-flex items-center gap-1 rounded-full bg-green-100 dark:bg-green-900/30 px-2 py-0.5 font-medium text-green-700 dark:text-green-400"
      >
        <BoltIcon class="h-3 w-3" />
        {{ $t('growthCoach.insights.actionable') }}
      </span>
      <span v-else />
      <span class="text-gray-400 dark:text-gray-500">
        {{ $t('growthCoach.insights.createdAt') }}: {{ formatDate(insight.createdAt) }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  LightBulbIcon,
  BoltIcon,
} from '@heroicons/vue/24/outline'
import type { GrowthInsight } from '@/types/growthCoach'

const props = defineProps<{
  insight: GrowthInsight
}>()


const impactBadgeClass = computed(() => {
  switch (props.insight.impact) {
    case 'HIGH': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    case 'MEDIUM': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'LOW': return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
})

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString()
}
</script>
