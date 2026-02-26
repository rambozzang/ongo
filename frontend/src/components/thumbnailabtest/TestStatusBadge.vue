<script setup lang="ts">
import type { TestStatus } from '@/types/thumbnailAbTest'

const props = defineProps<{
  status: TestStatus
}>()

const statusConfig: Record<TestStatus, { bg: string; text: string; label: string }> = {
  ACTIVE: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: 'Active' },
  COMPLETED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: 'Completed' },
  PENDING: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: 'Pending' },
}

const config = statusConfig[props.status] ?? statusConfig.PENDING
</script>

<template>
  <span
    :class="[config.bg, config.text]"
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold"
  >
    <span
      class="mr-1.5 h-1.5 w-1.5 rounded-full"
      :class="{
        'bg-green-500': status === 'ACTIVE',
        'bg-blue-500': status === 'COMPLETED',
        'bg-yellow-500': status === 'PENDING',
      }"
    />
    {{ config.label }}
  </span>
</template>
