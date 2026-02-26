<template>
  <span
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
    :class="badgeClass"
  >
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CampaignStatus } from '@/types/fanSegmentCampaign'

const props = defineProps<{
  status: CampaignStatus
}>()

const badgeClass = computed(() => {
  const classes: Record<CampaignStatus, string> = {
    DRAFT: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    SCHEDULED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    SENDING: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    COMPLETED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return classes[props.status]
})

const label = computed(() => {
  const labels: Record<CampaignStatus, string> = {
    DRAFT: '초안',
    SCHEDULED: '예약됨',
    SENDING: '발송 중',
    COMPLETED: '완료',
  }
  return labels[props.status]
})
</script>
