<script setup lang="ts">
import { computed } from 'vue'
import {
  PencilIcon,
  TrashIcon,
  PaperAirplaneIcon,
  ExclamationTriangleIcon,
  ClockIcon,
  GlobeAltIcon,
} from '@heroicons/vue/24/outline'
import type { Webhook, WebhookEvent } from '@/types/webhook'
import { WEBHOOK_EVENT_LABELS } from '@/types/webhook'

const props = defineProps<{
  webhook: Webhook
}>()

const emit = defineEmits<{
  edit: [webhook: Webhook]
  delete: [webhook: Webhook]
  test: [webhook: Webhook]
  toggle: [webhook: Webhook]
  select: [webhook: Webhook]
}>()

const truncatedUrl = computed(() => {
  const url = props.webhook.url
  if (url.length <= 50) return url
  return url.substring(0, 47) + '...'
})

const lastTriggeredLabel = computed(() => {
  if (!props.webhook.lastTriggeredAt) return '아직 없음'
  const date = new Date(props.webhook.lastTriggeredAt)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMinutes = Math.floor(diffMs / (1000 * 60))
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffMinutes < 1) return '방금 전'
  if (diffMinutes < 60) return `${diffMinutes}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  return `${diffDays}일 전`
})

function getEventLabel(event: WebhookEvent): string {
  return WEBHOOK_EVENT_LABELS[event]?.label ?? event
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-5 transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
    @click="emit('select', webhook)"
  >
    <!-- Header: URL + Toggle -->
    <div class="mb-3 flex items-start justify-between gap-3">
      <div class="flex min-w-0 items-center gap-2">
        <GlobeAltIcon class="h-5 w-5 shrink-0 text-gray-400 dark:text-gray-500" />
        <span
          class="truncate font-mono text-sm text-gray-900 dark:text-white"
          :title="webhook.url"
        >
          {{ truncatedUrl }}
        </span>
      </div>
      <button
        class="relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
        :class="webhook.isActive ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
        role="switch"
        :aria-checked="webhook.isActive"
        :aria-label="webhook.isActive ? '활성 상태 - 클릭하여 비활성화' : '비활성 상태 - 클릭하여 활성화'"
        @click.stop="emit('toggle', webhook)"
      >
        <span
          class="inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform"
          :class="webhook.isActive ? 'translate-x-6' : 'translate-x-1'"
        />
      </button>
    </div>

    <!-- Status indicator -->
    <div class="mb-3 flex items-center gap-2">
      <span
        class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium"
        :class="
          webhook.isActive
            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
            : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
        "
      >
        <span
          class="h-1.5 w-1.5 rounded-full"
          :class="webhook.isActive ? 'bg-green-500' : 'bg-gray-400'"
        />
        {{ webhook.isActive ? '활성' : '비활성' }}
      </span>

      <span
        v-if="webhook.failureCount > 0"
        class="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/30 dark:text-red-400"
      >
        <ExclamationTriangleIcon class="h-3 w-3" />
        실패 {{ webhook.failureCount }}회
      </span>
    </div>

    <!-- Event badges -->
    <div class="mb-3 flex flex-wrap gap-1.5">
      <span
        v-for="event in webhook.events"
        :key="event"
        class="inline-block rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-700 dark:bg-gray-700 dark:text-gray-300"
      >
        {{ getEventLabel(event) }}
      </span>
    </div>

    <!-- Last triggered -->
    <div class="mb-4 flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
      <ClockIcon class="h-3.5 w-3.5" />
      <span>마지막 호출: {{ lastTriggeredLabel }}</span>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-2 border-t border-gray-100 pt-3 dark:border-gray-700">
      <button
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
        @click.stop="emit('edit', webhook)"
      >
        <PencilIcon class="h-3.5 w-3.5" />
        수정
      </button>
      <button
        class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20"
        @click.stop="emit('test', webhook)"
      >
        <PaperAirplaneIcon class="h-3.5 w-3.5" />
        테스트
      </button>
      <button
        class="ml-auto inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
        @click.stop="emit('delete', webhook)"
      >
        <TrashIcon class="h-3.5 w-3.5" />
        삭제
      </button>
    </div>
  </div>
</template>
