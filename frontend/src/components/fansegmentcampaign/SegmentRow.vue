<template>
  <tr class="hover:bg-gray-50 dark:hover:bg-gray-700/50">
    <td class="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900 dark:text-gray-100">
      {{ segment.name }}
    </td>
    <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
      {{ segment.criteria }}
    </td>
    <td class="whitespace-nowrap px-4 py-3 text-right text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ segment.fanCount.toLocaleString('ko-KR') }}
    </td>
    <td class="whitespace-nowrap px-4 py-3 text-right text-sm">
      <span
        class="font-semibold"
        :class="engagementClass"
      >
        {{ segment.avgEngagement.toFixed(1) }}%
      </span>
    </td>
  </tr>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FanSegment } from '@/types/fanSegmentCampaign'

const props = defineProps<{
  segment: FanSegment
}>()

const engagementClass = computed(() => {
  if (props.segment.avgEngagement >= 15) return 'text-green-600 dark:text-green-400'
  if (props.segment.avgEngagement >= 5) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})
</script>
