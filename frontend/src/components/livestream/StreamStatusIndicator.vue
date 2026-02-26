<script setup lang="ts">
defineProps<{
  status: 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELLED'
}>()

const statusConfig: Record<string, { bg: string; text: string; dot: string; label: string }> = {
  LIVE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', dot: 'bg-red-500', label: 'LIVE' },
  SCHEDULED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', dot: 'bg-blue-500', label: '예약됨' },
  ENDED: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-400', dot: 'bg-gray-400', label: '종료' },
  CANCELLED: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', dot: 'bg-red-500', label: '취소됨' },
}

const getConfig = (status: string) => statusConfig[status] ?? statusConfig.ENDED
</script>

<template>
  <span
    :class="[getConfig(status).bg, getConfig(status).text]"
    class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
  >
    <span
      :class="[
        getConfig(status).dot,
        status === 'LIVE' ? 'animate-pulse' : '',
      ]"
      class="h-2 w-2 rounded-full"
    />
    {{ getConfig(status).label }}
  </span>
</template>
