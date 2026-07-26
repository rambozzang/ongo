<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { aiApi } from '@/api/ai'
import type { AiPipelineResponse } from '@/types/ai'
import AsyncState from '@/components/common/AsyncState.vue'

const props = defineProps<{
  pipelineId: string
}>()

const emit = defineEmits<{
  completed: [pipeline: AiPipelineResponse]
  cancelled: []
}>()

const pipeline = ref<AiPipelineResponse | null>(null)
const error = ref<string | null>(null)
const cancelling = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

const isRunning = computed(() => {
  return pipeline.value?.status === 'PENDING' || pipeline.value?.status === 'RUNNING'
})

const completedCount = computed(() => {
  if (!pipeline.value) return 0
  return pipeline.value.steps.filter((s) => s.status === 'COMPLETED').length
})

const totalSteps = computed(() => pipeline.value?.steps.length ?? 0)

const progressPercentage = computed(() => {
  if (totalSteps.value === 0) return 0
  return Math.round((completedCount.value / totalSteps.value) * 100)
})

async function fetchStatus() {
  try {
    const result = await aiApi.getPipelineStatus(props.pipelineId)
    pipeline.value = result
    error.value = null

    if (!isRunning.value) {
      stopPolling()
      if (result.status === 'COMPLETED') {
        emit('completed', result)
      }
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '상태 조회 실패'
  }
}

async function handleCancel() {
  cancelling.value = true
  try {
    await aiApi.cancelPipeline(props.pipelineId)
    await fetchStatus()
    emit('cancelled')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '취소 실패'
  } finally {
    cancelling.value = false
  }
}

function startPolling() {
  fetchStatus()
  pollTimer = setInterval(fetchStatus, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}



function stepStatusColor(status: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'text-success-strong bg-success-subtle border-success'
    case 'FAILED':
      return 'text-error-strong bg-error-subtle border-error'
    case 'RUNNING':
      return 'text-info-strong bg-info-subtle border-info'
    case 'SKIPPED':
      return 'text-gray-400 dark:text-gray-500 bg-gray-100 dark:bg-gray-800 border-gray-300 dark:border-gray-700'
    default:
      return 'text-gray-400 dark:text-gray-500 bg-gray-50 dark:bg-gray-800/50 border-gray-200 dark:border-gray-700'
  }
}

const expandedSteps = ref<Set<string>>(new Set())

function toggleExpand(step: string) {
  if (expandedSteps.value.has(step)) {
    expandedSteps.value.delete(step)
  } else {
    expandedSteps.value.add(step)
  }
}

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

watch(
  () => props.pipelineId,
  () => {
    stopPolling()
    startPolling()
  },
)
</script>

<template>
  <!-- 최초 조회 전(로딩) / 최초 조회 실패(에러) / 정상 -->
  <AsyncState
    :loading="!pipeline && !error"
    :error="pipeline ? null : error"
    skeleton="list"
    :skeleton-count="3"
    @retry="fetchStatus"
  >
    <div class="space-y-4">
      <!-- Overall status -->
      <div v-if="pipeline" class="flex items-center justify-between">
        <div>
          <h3 class="text-body font-semibold text-gray-700 dark:text-gray-300">
            AI 파이프라인 진행 상태
          </h3>
          <p class="text-body-xs text-gray-500 dark:text-gray-400 mt-0.5">
            {{ completedCount }}/{{ totalSteps }} 스텝 완료
            <span v-if="pipeline.discountApplied" class="text-success-strong ml-2">
              (20% 할인 적용)
            </span>
          </p>
        </div>
        <div class="flex items-center gap-2">
          <span
            class="inline-flex items-center rounded-full px-2.5 py-0.5 text-body-xs font-medium"
            :class="{
              'bg-info-subtle text-info-strong': pipeline.status === 'RUNNING' || pipeline.status === 'PENDING',
              'bg-success-subtle text-success-strong': pipeline.status === 'COMPLETED',
              'bg-error-subtle text-error-strong': pipeline.status === 'FAILED',
              'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400': pipeline.status === 'CANCELLED',
            }"
          >
            {{ pipeline.status }}
          </span>
          <span class="text-body-xs text-gray-500 dark:text-gray-400">
            {{ pipeline.totalCredits }} 크레딧
          </span>
        </div>
      </div>

      <!-- Progress bar -->
      <div v-if="pipeline" class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
        <div
          class="h-2 rounded-full transition-all duration-500"
          :class="pipeline.status === 'FAILED' ? 'bg-error' : 'bg-primary-600'"
          :style="{ width: progressPercentage + '%' }"
        />
      </div>

      <!-- Step nodes -->
      <div v-if="pipeline" class="space-y-2">
        <div
          v-for="(step, index) in pipeline.steps"
          :key="step.step"
          class="relative"
        >
          <!-- Connection line -->
          <div
            v-if="index > 0"
            class="absolute -top-2 left-5 w-px h-2"
            :class="step.status === 'COMPLETED' ? 'bg-success' : 'bg-gray-300 dark:bg-gray-600'"
          />

          <div
            class="flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-all"
            :class="stepStatusColor(step.status)"
            @click="toggleExpand(step.step)"
          >
            <!-- Status icon -->
            <div class="flex-shrink-0 mt-0.5">
              <div v-if="step.status === 'RUNNING'" class="h-5 w-5">
                <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              </div>
              <svg v-else-if="step.status === 'COMPLETED'" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              <svg v-else-if="step.status === 'FAILED'" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
              <svg v-else-if="step.status === 'SKIPPED'" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M13 5l7 7-7 7M5 5l7 7-7 7" />
              </svg>
              <div v-else class="h-5 w-5 rounded-full border-2 border-current opacity-40" />
            </div>

            <!-- Step info -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <span class="text-body font-medium">{{ step.displayName }}</span>
                <span class="text-body-xs opacity-70">{{ step.creditCost }} 크레딧</span>
              </div>

              <!-- Error message -->
              <p v-if="step.error" class="text-body-xs text-error-strong mt-1">
                {{ step.error }}
              </p>
            </div>

            <!-- Expand indicator -->
            <svg
              v-if="step.result"
              class="h-4 w-4 transition-transform flex-shrink-0"
              :class="{ 'rotate-180': expandedSteps.has(step.step) }"
              fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </div>

          <!-- Expanded result -->
          <div
            v-if="expandedSteps.has(step.step) && step.result"
            class="ml-8 mt-1 p-3 rounded-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 text-body-xs"
          >
            <pre class="whitespace-pre-wrap text-gray-700 dark:text-gray-300 overflow-auto max-h-48">{{ JSON.stringify(step.result, null, 2) }}</pre>
          </div>
        </div>
      </div>

      <!-- Error — 폴링 중 일시적 실패는 진행 상태를 가리지 않고 배너로만 노출 -->
      <div v-if="error" role="alert" class="rounded-lg bg-error-subtle p-3 text-body text-error-strong">
        {{ error }}
      </div>

      <!-- Cancel button -->
      <button
        v-if="isRunning"
        :disabled="cancelling"
        class="w-full rounded-lg border border-error px-4 py-2 text-body font-medium text-error-strong transition-colors hover:bg-error-subtle disabled:opacity-50"
        @click="handleCancel"
      >
        {{ cancelling ? '취소 중...' : '파이프라인 취소' }}
      </button>
    </div>
  </AsyncState>
</template>
