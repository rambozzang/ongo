<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="translate-y-full opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition-all duration-200 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-full opacity-0"
    >
      <div
        v-if="visible"
        class="fixed bottom-6 right-6 z-50 w-96 max-h-[28rem] rounded-xl border border-gray-200 bg-white shadow-2xl dark:border-gray-700 dark:bg-gray-800"
      >
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-700">
          <div class="flex items-center gap-2">
            <div
              class="flex h-7 w-7 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30"
            >
              <svg class="h-4 w-4 text-primary-600 dark:text-primary-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15.3M14.25 3.104c.251.023.501.05.75.082M19.8 15.3l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23.693L5 14.5m14.8.8l1.402 1.402c1.232 1.232.65 3.318-1.067 3.611A48.309 48.309 0 0112 21c-2.773 0-5.491-.235-8.135-.687-1.718-.293-2.3-2.379-1.067-3.61L5 14.5" />
              </svg>
            </div>
            <div>
              <h3 class="text-sm font-semibold text-gray-900 dark:text-white">AI 배치 처리</h3>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                {{ operationLabel }}
              </p>
            </div>
          </div>
          <div class="flex items-center gap-1">
            <button
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              @click="collapsed = !collapsed"
            >
              <svg
                class="h-4 w-4 transition-transform"
                :class="{ 'rotate-180': collapsed }"
                fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            <button
              v-if="isFinished"
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              @click="$emit('close')"
            >
              <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Overall Progress -->
        <div class="px-4 py-3">
          <div class="mb-1 flex items-center justify-between text-xs">
            <span class="text-gray-600 dark:text-gray-400">
              {{ completedCount }} / {{ batch?.totalItems ?? 0 }} 완료
            </span>
            <span class="font-medium" :class="statusColorClass">
              {{ statusLabel }}
            </span>
          </div>
          <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="progressBarClass"
              :style="{ width: `${progressPercent}%` }"
            />
          </div>
        </div>

        <!-- Items List -->
        <div v-if="!collapsed" class="max-h-60 overflow-y-auto border-t border-gray-100 dark:border-gray-700">
          <div
            v-for="item in batch?.items ?? []"
            :key="item.videoId"
            class="flex items-center gap-3 border-b border-gray-50 px-4 py-2.5 last:border-0 dark:border-gray-700/50"
          >
            <!-- Status Icon -->
            <div class="flex-shrink-0">
              <!-- Pending -->
              <div v-if="item.status === 'PENDING'" class="h-5 w-5 rounded-full border-2 border-gray-300 dark:border-gray-600" />
              <!-- Processing -->
              <div v-else-if="item.status === 'PROCESSING'" class="h-5 w-5">
                <svg class="h-5 w-5 animate-spin text-primary-500" fill="none" viewBox="0 0 24 24">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" class="opacity-25" />
                  <path fill="currentColor" class="opacity-75" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              </div>
              <!-- Completed -->
              <div v-else-if="item.status === 'COMPLETED'" class="flex h-5 w-5 items-center justify-center rounded-full bg-green-500">
                <svg class="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <!-- Failed -->
              <div v-else class="flex h-5 w-5 items-center justify-center rounded-full bg-red-500">
                <svg class="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </div>
            </div>

            <!-- Video Info -->
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm text-gray-900 dark:text-gray-100">
                {{ item.videoTitle || `영상 #${item.videoId}` }}
              </p>
              <p v-if="item.error" class="truncate text-xs text-red-500 dark:text-red-400">
                {{ item.error }}
              </p>
            </div>

            <!-- Status label -->
            <span class="flex-shrink-0 text-xs" :class="getItemStatusClass(item.status)">
              {{ getItemStatusLabel(item.status) }}
            </span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { aiApi } from '@/api/ai'
import type { AiBatchResponse, AiBatchOperation, BatchItemStatus } from '@/types/ai'
import { BATCH_OPERATIONS } from '@/types/ai'

const props = defineProps<{
  batchId: string
  operation: AiBatchOperation
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  completed: [batch: AiBatchResponse]
}>()

const batch = ref<AiBatchResponse | null>(null)
const collapsed = ref(false)
let pollInterval: ReturnType<typeof setInterval> | null = null

const operationLabel = computed(() => {
  return BATCH_OPERATIONS.find(op => op.key === props.operation)?.label ?? props.operation
})

const completedCount = computed(() => {
  if (!batch.value) return 0
  return batch.value.items.filter(i => i.status === 'COMPLETED' || i.status === 'FAILED').length
})

const progressPercent = computed(() => {
  if (!batch.value || batch.value.totalItems === 0) return 0
  return Math.round((completedCount.value / batch.value.totalItems) * 100)
})

const isFinished = computed(() => {
  return batch.value?.status === 'COMPLETED' || batch.value?.status === 'PARTIALLY_FAILED'
})

const statusLabel = computed(() => {
  switch (batch.value?.status) {
    case 'PENDING': return '대기 중'
    case 'PROCESSING': return '처리 중...'
    case 'COMPLETED': return '완료'
    case 'PARTIALLY_FAILED': return '일부 실패'
    default: return '대기 중'
  }
})

const statusColorClass = computed(() => {
  switch (batch.value?.status) {
    case 'COMPLETED': return 'text-green-600 dark:text-green-400'
    case 'PARTIALLY_FAILED': return 'text-yellow-600 dark:text-yellow-400'
    case 'PROCESSING': return 'text-primary-600 dark:text-primary-400'
    default: return 'text-gray-500 dark:text-gray-400'
  }
})

const progressBarClass = computed(() => {
  switch (batch.value?.status) {
    case 'COMPLETED': return 'bg-green-500'
    case 'PARTIALLY_FAILED': return 'bg-yellow-500'
    default: return 'bg-primary-500'
  }
})

function getItemStatusClass(status: BatchItemStatus): string {
  switch (status) {
    case 'COMPLETED': return 'text-green-600 dark:text-green-400'
    case 'FAILED': return 'text-red-600 dark:text-red-400'
    case 'PROCESSING': return 'text-primary-600 dark:text-primary-400'
    default: return 'text-gray-400 dark:text-gray-500'
  }
}

function getItemStatusLabel(status: BatchItemStatus): string {
  switch (status) {
    case 'PENDING': return '대기'
    case 'PROCESSING': return '처리 중'
    case 'COMPLETED': return '완료'
    case 'FAILED': return '실패'
    default: return status
  }
}

async function fetchStatus() {
  try {
    batch.value = await aiApi.getBatchStatus(props.batchId)

    if (isFinished.value) {
      stopPolling()
      emit('completed', batch.value!)
    }
  } catch {
    // Silently handle polling errors
  }
}

function startPolling() {
  fetchStatus()
  pollInterval = setInterval(fetchStatus, 2000)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

onMounted(() => {
  if (props.batchId) {
    startPolling()
  }
})

onUnmounted(stopPolling)

watch(() => props.batchId, (newId) => {
  stopPolling()
  if (newId) {
    startPolling()
  }
})
</script>
