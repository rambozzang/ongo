<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <div>
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ poll.title }}
        </h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('fanPoll.totalVotesLabel') }}: {{ poll.totalVotes.toLocaleString('ko-KR') }}{{ $t('fanPoll.votesUnit') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="statusClass"
        >
          {{ statusText }}
        </span>
        <button
          v-if="poll.status === 'ACTIVE'"
          class="rounded-lg px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
          @click="$emit('close', poll.id)"
        >
          {{ $t('fanPoll.closePoll') }}
        </button>
        <button
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
          @click="$emit('delete', poll.id)"
        >
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Horizontal bar chart -->
    <div class="space-y-3">
      <PollOptionBar
        v-for="(option, index) in sortedOptions"
        :key="option.id"
        :option="option"
        :rank="index + 1"
        :color-index="index"
      />
    </div>

    <!-- Footer info -->
    <div class="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ $t('fanPoll.createdAt') }}: {{ formatDate(poll.createdAt) }}
      </span>
      <span class="text-xs" :class="deadlineColor">
        {{ $t('fanPoll.deadline') }}: {{ formatDate(poll.deadline) }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FanPoll } from '@/types/fanPoll'
import PollOptionBar from './PollOptionBar.vue'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  poll: FanPoll
}>()

defineEmits<{
  close: [id: number]
  delete: [id: number]
}>()

const sortedOptions = computed(() =>
  [...props.poll.options].sort((a, b) => b.voteCount - a.voteCount),
)

const statusClass = computed(() => {
  const map: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
    ACTIVE: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    CLOSED: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
    SCHEDULED: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  }
  return map[props.poll.status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const statusText = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: t('fanPoll.statusDraft'),
    ACTIVE: t('fanPoll.statusActive'),
    CLOSED: t('fanPoll.statusClosed'),
    SCHEDULED: t('fanPoll.statusScheduled'),
  }
  return labels[props.poll.status] ?? props.poll.status
})

const deadlineColor = computed(() => {
  if (props.poll.status === 'CLOSED') return 'text-gray-400 dark:text-gray-500'
  const now = new Date()
  const deadline = new Date(props.poll.deadline)
  const daysLeft = Math.ceil((deadline.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  if (daysLeft <= 1) return 'text-red-500 dark:text-red-400'
  return 'text-gray-400 dark:text-gray-500'
})

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}
</script>
