<script setup lang="ts">
import { LightBulbIcon } from '@heroicons/vue/24/outline'
import type { HealthIssue } from '@/types/platformHealth'

defineProps<{
  issue: HealthIssue
}>()

const severityConfig: Record<string, { bg: string; text: string; label: string }> = {
  HIGH: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: 'HIGH' },
  MEDIUM: { bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300', label: 'MEDIUM' },
  LOW: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: 'LOW' },
}

const getSeverityStyle = (severity: string) => severityConfig[severity] ?? severityConfig.MEDIUM
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-900">
    <div class="mb-2 flex items-center gap-2">
      <!-- Severity badge -->
      <span
        :class="[getSeverityStyle(issue.severity).bg, getSeverityStyle(issue.severity).text]"
        class="rounded-full px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wide"
      >
        {{ getSeverityStyle(issue.severity).label }}
      </span>
      <!-- Category -->
      <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
        {{ issue.category }}
      </span>
    </div>

    <!-- Description -->
    <p class="mb-2 text-sm text-gray-800 dark:text-gray-200">
      {{ issue.description }}
    </p>

    <!-- Recommendation -->
    <div class="flex items-start gap-2 rounded-lg bg-blue-50 p-2.5 dark:bg-blue-900/10">
      <LightBulbIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-blue-500 dark:text-blue-400" />
      <p class="text-xs text-blue-700 dark:text-blue-300">
        {{ issue.recommendation }}
      </p>
    </div>
  </div>
</template>
