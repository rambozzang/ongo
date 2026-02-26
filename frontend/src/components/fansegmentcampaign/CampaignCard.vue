<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-900">
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ campaign.name }}
        </h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ campaign.segmentName }}
        </p>
      </div>
      <CampaignStatusBadge :status="campaign.status" class="ml-2 flex-shrink-0" />
    </div>

    <!-- Type -->
    <div class="mb-3">
      <span
        class="inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium"
        :class="typeClass"
      >
        {{ typeLabel }}
      </span>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-2 gap-3">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">발송 / 대상</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ campaign.sentCount.toLocaleString('ko-KR') }} / {{ campaign.targetCount.toLocaleString('ko-KR') }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">오픈률</p>
        <p class="text-sm font-semibold" :class="campaign.openRate > 0 ? 'text-primary-600 dark:text-primary-400' : 'text-gray-400 dark:text-gray-500'">
          {{ campaign.openRate > 0 ? campaign.openRate.toFixed(1) + '%' : '-' }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">클릭률</p>
        <p class="text-sm font-semibold" :class="campaign.clickRate > 0 ? 'text-green-600 dark:text-green-400' : 'text-gray-400 dark:text-gray-500'">
          {{ campaign.clickRate > 0 ? campaign.clickRate.toFixed(1) + '%' : '-' }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">진행률</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ progressPercent }}%
        </p>
      </div>
    </div>

    <!-- Progress bar -->
    <div class="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
      <div
        class="h-full rounded-full bg-primary-500 transition-all duration-300"
        :style="{ width: `${progressPercent}%` }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SegmentCampaign, CampaignType } from '@/types/fanSegmentCampaign'
import CampaignStatusBadge from './CampaignStatusBadge.vue'

const props = defineProps<{
  campaign: SegmentCampaign
}>()

const progressPercent = computed(() => {
  if (props.campaign.targetCount === 0) return 0
  return Math.round((props.campaign.sentCount / props.campaign.targetCount) * 100)
})

const typeClass = computed(() => {
  const classes: Record<CampaignType, string> = {
    EMAIL: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
    PUSH: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    IN_APP: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400',
    SMS: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  }
  return classes[props.campaign.type]
})

const typeLabel = computed(() => {
  const labels: Record<CampaignType, string> = {
    EMAIL: '이메일',
    PUSH: '푸시 알림',
    IN_APP: '인앱 메시지',
    SMS: 'SMS',
  }
  return labels[props.campaign.type]
})
</script>
