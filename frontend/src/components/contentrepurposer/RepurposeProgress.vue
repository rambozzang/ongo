<script setup lang="ts">
defineProps<{
  progress: number
  status: string
}>()

const statusConfig: Record<string, { color: string; barColor: string; label: string }> = {
  PENDING: { color: 'text-gray-600 dark:text-gray-400', barColor: 'bg-gray-400', label: '대기중' },
  PROCESSING: { color: 'text-blue-600 dark:text-blue-400', barColor: 'bg-blue-500', label: '변환중' },
  COMPLETED: { color: 'text-green-600 dark:text-green-400', barColor: 'bg-green-500', label: '완료' },
  FAILED: { color: 'text-red-600 dark:text-red-400', barColor: 'bg-red-500', label: '실패' },
}

const getConfig = (status: string) => statusConfig[status] ?? statusConfig.PENDING
</script>

<template>
  <div class="w-full">
    <div class="mb-1 flex items-center justify-between">
      <span :class="getConfig(status).color" class="text-xs font-medium">
        {{ getConfig(status).label }}
      </span>
      <span class="text-xs font-semibold text-gray-700 dark:text-gray-300">
        {{ progress }}%
      </span>
    </div>
    <div class="h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
      <div
        :class="getConfig(status).barColor"
        class="h-full rounded-full transition-all duration-700 ease-out"
        :style="{ width: `${progress}%` }"
      />
    </div>
  </div>
</template>
