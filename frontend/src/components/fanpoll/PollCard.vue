<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    :class="selected ? 'ring-2 ring-primary-500 dark:ring-primary-400' : ''"
    @click="$emit('select', poll.id)"
  >
    <!-- Header: title + badges -->
    <div class="mb-3 flex items-start justify-between gap-2">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ poll.title }}
        </h3>
        <p class="mt-1 line-clamp-2 text-xs text-gray-500 dark:text-gray-400">
          {{ poll.description }}
        </p>
      </div>
      <div class="flex flex-shrink-0 items-center gap-1.5">
        <!-- Type badge -->
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="typeBadgeClass"
        >
          {{ typeLabel }}
        </span>
        <!-- Status badge -->
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="statusBadgeClass"
        >
          {{ statusLabel }}
        </span>
      </div>
    </div>

    <!-- Stats row -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
      <div class="flex items-center gap-3">
        <!-- Total votes -->
        <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
          <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          <span class="font-medium text-gray-700 dark:text-gray-300">
            {{ poll.totalVotes.toLocaleString('ko-KR') }}
          </span>
          {{ $t('fanPoll.votesUnit') }}
        </div>
        <!-- Options count -->
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ poll.options.length }}{{ $t('fanPoll.optionsUnit') }}
        </span>
      </div>

      <!-- Deadline -->
      <div class="flex items-center gap-1 text-xs" :class="deadlineClass">
        <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ deadlineText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FanPoll } from '@/types/fanPoll'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  poll: FanPoll
  selected?: boolean
}>()

defineEmits<{
  select: [id: number]
}>()

const typeBadgeClass = computed(() => {
  const map: Record<string, string> = {
    SINGLE_CHOICE: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    MULTIPLE_CHOICE: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    RANKING: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  }
  return map[props.poll.type] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const typeLabel = computed(() => {
  const labels: Record<string, string> = {
    SINGLE_CHOICE: t('fanPoll.typeSingle'),
    MULTIPLE_CHOICE: t('fanPoll.typeMultiple'),
    RANKING: t('fanPoll.typeRanking'),
  }
  return labels[props.poll.type] ?? props.poll.type
})

const statusBadgeClass = computed(() => {
  const map: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
    ACTIVE: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    CLOSED: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
    SCHEDULED: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  }
  return map[props.poll.status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: t('fanPoll.statusDraft'),
    ACTIVE: t('fanPoll.statusActive'),
    CLOSED: t('fanPoll.statusClosed'),
    SCHEDULED: t('fanPoll.statusScheduled'),
  }
  return labels[props.poll.status] ?? props.poll.status
})

const deadlineClass = computed(() => {
  if (props.poll.status === 'CLOSED') return 'text-gray-400 dark:text-gray-500'
  const now = new Date()
  const deadline = new Date(props.poll.deadline)
  const daysLeft = Math.ceil((deadline.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  if (daysLeft <= 1) return 'text-red-500 dark:text-red-400'
  if (daysLeft <= 3) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-gray-500 dark:text-gray-400'
})

const deadlineText = computed(() => {
  const deadline = new Date(props.poll.deadline)
  if (props.poll.status === 'CLOSED') {
    return t('fanPoll.ended')
  }
  const now = new Date()
  const daysLeft = Math.ceil((deadline.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  if (daysLeft <= 0) return t('fanPoll.expired')
  if (daysLeft === 1) return t('fanPoll.daysLeft', { n: 1 })
  return t('fanPoll.daysLeft', { n: daysLeft })
})
</script>
