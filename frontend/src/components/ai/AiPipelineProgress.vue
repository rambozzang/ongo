<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { aiApi } from '@/api/ai'
import type { AiPipelineResponse, PipelineStepStatusDto, PipelineStatusType } from '@/types/ai'

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

function stepStatusIcon(status: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'check'
    case 'FAILED':
      return 'x'
    case 'RUNNING':
      return 'spinner'
    case 'SKIPPED':
      return 'skip'
    default:
      return 'pending'
  }
}

function stepStatusColor(status: string): string {
  switch (status) {
    case 'COMPLETED':
      return 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30 border-green-300 dark:border-green-700'
    case 'FAILED':
      return 'text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-900/30 border-red-300 dark:border-red-700'
    case 'RUNNING':
      return 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30 border-blue-300 dark:border-blue-700'
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
  <div class="space-y-4">
    <!-- Overall status -->
    <div v-if="pipeline" class="flex items-center justify-between">
      <div>
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
          AI 파이프라인 진행 상태
        </h3>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          {{ completedCount }}/{{ totalSteps }} 스텝 완료
          <span v-if="pipeline.discountApplied" class="text-green-600 dark:text-green-400 ml-2">
            (20% 할인 적용)
          </span>
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span
          class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
          :class="{
            'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300': pipeline.status === 'RUNNING' || pipeline.status === 'PENDING',
            'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300': pipeline.status === 'COMPLETED',
            'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300': pipeline.status === 'FAILED',
            'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400': pipeline.status === 'CANCELLED',
          }"
        >
          {{ pipeline.status }}
        </span>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ pipeline.totalCredits }} 크레딧
        </span>
      </div>
    </div>

    <!-- Progress bar -->
    <div v-if="pipeline" class="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
      <div
        class="h-2 rounded-full transition-all duration-500"
        :class="pipeline.status === 'FAILED' ? 'bg-red-500' : 'bg-primary-600'"
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
          :class="step.status === 'COMPLETED' ? 'bg-green-400 dark:bg-green-600' : 'bg-gray-300 dark:bg-gray-600'"
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
              <span class="text-sm font-medium">{{ step.displayName }}</span>
              <span class="text-xs opacity-70">{{ step.creditCost }} 크레딧</span>
            </div>

            <!-- Error message -->
            <p v-if="step.error" class="text-xs text-red-600 dark:text-red-400 mt-1">
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
          class="ml-8 mt-1 p-3 rounded-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 text-xs"
        >
          <pre class="whitespace-pre-wrap text-gray-700 dark:text-gray-300 overflow-auto max-h-48">{{ JSON.stringify(step.result, null, 2) }}</pre>
        </div>
      </div>
    </div>

    <!-- Error -->
    <div v-if="error" class="rounded-lg bg-red-50 dark:bg-red-900/20 p-3 text-sm text-red-700 dark:text-red-300">
      {{ error }}
    </div>

    <!-- Cancel button -->
    <button
      v-if="isRunning"
      :disabled="cancelling"
      class="w-full rounded-lg border border-red-300 dark:border-red-700 px-4 py-2 text-sm font-medium text-red-600 dark:text-red-400 transition-colors hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50"
      @click="handleCancel"
    >
      {{ cancelling ? '취소 중...' : '파이프라인 취소' }}
    </button>

    <!-- Loading state -->
    <div v-if="!pipeline && !error" class="flex items-center justify-center py-8">
      <svg class="animate-spin h-6 w-6 text-primary-600" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      <span class="ml-2 text-sm text-gray-600 dark:text-gray-400">상태 조회 중...</span>
    </div>
  </div>
</template>
