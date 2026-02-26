<script setup lang="ts">
import type { AutomationLog } from '@/types/platformAutomation'

interface Props {
  log: AutomationLog
}

defineProps<Props>()

const statusConfig: Record<string, { label: string; class: string }> = {
  SUCCESS: {
    label: '성공',
    class: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  },
  FAILED: {
    label: '실패',
    class: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  },
  SKIPPED: {
    label: '건너뜀',
    class: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  },
}

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}
</script>

<template>
  <div
    class="flex items-center gap-4 rounded-lg border border-gray-100 bg-white px-4 py-3 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-900 dark:hover:bg-gray-800"
  >
    <!-- Status Badge -->
    <span
      :class="statusConfig[log.status].class"
      class="inline-flex flex-shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
    >
      {{ statusConfig[log.status].label }}
    </span>

    <!-- Rule Name + Message -->
    <div class="flex-1 min-w-0">
      <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ log.ruleName }}
      </span>
      <p class="mt-0.5 truncate text-sm text-gray-500 dark:text-gray-400">
        {{ log.message }}
      </p>
    </div>

    <!-- Time -->
    <div class="flex-shrink-0">
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ formatDateTime(log.executedAt) }}
      </span>
    </div>
  </div>
</template>
