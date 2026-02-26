<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <div class="flex items-start justify-between mb-2">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
        {{ collab.influencerName }}
      </h3>
      <span
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
        :class="statusBadgeClass"
      >
        {{ statusLabel }}
      </span>
    </div>

    <!-- Message Preview -->
    <p class="mb-3 text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
      {{ collab.message }}
    </p>

    <!-- Budget -->
    <div class="mb-3 flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.collab.proposedBudget') }}</span>
      <span class="text-sm font-semibold text-primary-600 dark:text-primary-400">{{ formatKRW(collab.proposedBudget) }}</span>
    </div>

    <!-- Dates -->
    <div class="flex items-center justify-between border-t border-gray-100 dark:border-gray-800 pt-2 text-xs text-gray-400 dark:text-gray-500">
      <span>{{ $t('influencerMatch.collab.created') }}: {{ formatDate(collab.createdAt) }}</span>
      <span>{{ $t('influencerMatch.collab.updated') }}: {{ formatDate(collab.updatedAt) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CollabRequest, CollabStatus } from '@/types/influencerMatch'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  collab: CollabRequest
}>()

const statusColorMap: Record<CollabStatus, string> = {
  SUGGESTED: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  CONTACTED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  NEGOTIATING: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  CONFIRMED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  COMPLETED: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  DECLINED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
}

const statusLabelMap: Record<CollabStatus, string> = {
  SUGGESTED: 'influencerMatch.status.suggested',
  CONTACTED: 'influencerMatch.status.contacted',
  NEGOTIATING: 'influencerMatch.status.negotiating',
  CONFIRMED: 'influencerMatch.status.confirmed',
  COMPLETED: 'influencerMatch.status.completed',
  DECLINED: 'influencerMatch.status.declined',
}

const statusBadgeClass = computed(() => {
  return statusColorMap[props.collab.status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const statusLabel = computed(() => {
  const key = statusLabelMap[props.collab.status]
  return key ? t(key) : props.collab.status
})

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>
