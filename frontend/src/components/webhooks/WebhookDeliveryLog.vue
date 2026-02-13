<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  ChevronDownIcon,
  ChevronUpIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  XCircleIcon,
  ClockIcon,
  CodeBracketIcon,
} from '@heroicons/vue/24/outline'
import type { Webhook, WebhookDelivery } from '@/types/webhook'
import { WEBHOOK_EVENT_LABELS } from '@/types/webhook'

const props = defineProps<{
  webhook: Webhook
}>()

const emit = defineEmits<{
  retry: [webhookId: number, deliveryId: number]
  close: []
}>()

const expandedRows = ref<Set<number>>(new Set())

const deliveries = computed<WebhookDelivery[]>(() => {
  return [...(props.webhook.recentDeliveries || [])].sort(
    (a, b) => new Date(b.sentAt).getTime() - new Date(a.sentAt).getTime(),
  )
})

function toggleRow(id: number) {
  if (expandedRows.value.has(id)) {
    expandedRows.value.delete(id)
  } else {
    expandedRows.value.add(id)
  }
}

function isSuccess(statusCode: number): boolean {
  return statusCode >= 200 && statusCode < 300
}

function statusColorClass(statusCode: number): string {
  if (statusCode >= 200 && statusCode < 300) {
    return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20'
  }
  if (statusCode >= 400 && statusCode < 500) {
    return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/20'
  }
  return 'text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20'
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function getEventLabel(event: string): string {
  return WEBHOOK_EVENT_LABELS[event as keyof typeof WEBHOOK_EVENT_LABELS]?.label ?? event
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-gray-200 px-5 py-4 dark:border-gray-700">
      <div>
        <h3 class="text-base font-semibold text-gray-900 dark:text-white">배달 기록</h3>
        <p class="mt-0.5 font-mono text-xs text-gray-500 dark:text-gray-400">
          {{ webhook.url }}
        </p>
      </div>
      <button
        class="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
        @click="emit('close')"
      >
        <XCircleIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Empty state -->
    <div
      v-if="deliveries.length === 0"
      class="flex flex-col items-center py-12 text-center"
    >
      <ClockIcon class="mb-3 h-10 w-10 text-gray-300 dark:text-gray-600" />
      <p class="text-sm font-medium text-gray-600 dark:text-gray-400">배달 기록이 없습니다</p>
      <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
        웹훅이 호출되면 여기에 기록됩니다.
      </p>
    </div>

    <!-- Delivery table -->
    <div v-else class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
            <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400">
              이벤트
            </th>
            <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400">
              상태 코드
            </th>
            <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400">
              전송 시각
            </th>
            <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400">
              응답 시간
            </th>
            <th class="px-4 py-2.5 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-for="delivery in deliveries" :key="delivery.id">
            <!-- Main row -->
            <tr
              class="border-b border-gray-100 transition-colors hover:bg-gray-50 dark:border-gray-700/50 dark:hover:bg-gray-750"
              :class="{ 'bg-gray-50 dark:bg-gray-750': expandedRows.has(delivery.id) }"
            >
              <td class="px-4 py-3">
                <div class="flex items-center gap-2">
                  <CheckCircleIcon
                    v-if="isSuccess(delivery.statusCode)"
                    class="h-4 w-4 shrink-0 text-green-500"
                  />
                  <XCircleIcon v-else class="h-4 w-4 shrink-0 text-red-500" />
                  <span class="text-gray-900 dark:text-white">
                    {{ getEventLabel(delivery.event) }}
                  </span>
                </div>
              </td>
              <td class="px-4 py-3">
                <span
                  class="inline-block rounded px-2 py-0.5 text-xs font-mono font-medium"
                  :class="statusColorClass(delivery.statusCode)"
                >
                  {{ delivery.statusCode }}
                </span>
              </td>
              <td class="px-4 py-3 text-gray-600 dark:text-gray-400">
                {{ formatDate(delivery.sentAt) }}
              </td>
              <td class="px-4 py-3">
                <span
                  class="text-gray-600 dark:text-gray-400"
                  :class="{ 'font-medium text-yellow-600 dark:text-yellow-400': delivery.duration > 5000 }"
                >
                  {{ formatDuration(delivery.duration) }}
                </span>
              </td>
              <td class="px-4 py-3 text-right">
                <div class="flex items-center justify-end gap-1">
                  <!-- Expand/collapse -->
                  <button
                    v-if="delivery.responseBody"
                    class="rounded p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-600 dark:hover:bg-gray-600 dark:hover:text-gray-300"
                    :title="expandedRows.has(delivery.id) ? '접기' : '응답 보기'"
                    @click="toggleRow(delivery.id)"
                  >
                    <ChevronUpIcon v-if="expandedRows.has(delivery.id)" class="h-4 w-4" />
                    <ChevronDownIcon v-else class="h-4 w-4" />
                  </button>
                  <!-- Retry for failed -->
                  <button
                    v-if="!isSuccess(delivery.statusCode)"
                    class="inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20"
                    @click="emit('retry', webhook.id, delivery.id)"
                  >
                    <ArrowPathIcon class="h-3.5 w-3.5" />
                    재시도
                  </button>
                </div>
              </td>
            </tr>
            <!-- Expanded response body row -->
            <tr v-if="expandedRows.has(delivery.id) && delivery.responseBody">
              <td
                colspan="5"
                class="border-b border-gray-100 bg-gray-50 px-4 py-3 dark:border-gray-700/50 dark:bg-gray-900"
              >
                <div class="flex items-start gap-2">
                  <CodeBracketIcon class="mt-0.5 h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" />
                  <div class="min-w-0 flex-1">
                    <p class="mb-1 text-xs font-medium text-gray-500 dark:text-gray-400">
                      응답 본문
                    </p>
                    <pre
                      class="overflow-x-auto rounded-lg border border-gray-200 bg-white p-3 font-mono text-xs text-gray-800 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200"
                    >{{ delivery.responseBody }}</pre>
                  </div>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>
