<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { ApprovalChainSlaStatus } from '@/types/approval'
import {
  CheckCircleIcon,
  ClockIcon,
  XCircleIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { approvalApi } from '@/api/approval'

const props = defineProps<{
  approvalId: number
}>()

const steps = ref<ApprovalChainSlaStatus[]>([])
const loading = ref(true)
let timer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await fetchSla()
  timer = setInterval(fetchSla, 60_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

async function fetchSla() {
  try {
    steps.value = await approvalApi.getSlaStatus(props.approvalId)
  } catch {
    // silent
  } finally {
    loading.value = false
  }
}

function stepIcon(step: ApprovalChainSlaStatus) {
  if (step.status === 'APPROVED') return CheckCircleIcon
  if (step.status === 'REJECTED') return XCircleIcon
  if (step.isOverdue) return ExclamationTriangleIcon
  return ClockIcon
}

function stepColor(step: ApprovalChainSlaStatus): string {
  if (step.status === 'APPROVED') return 'text-green-500'
  if (step.status === 'REJECTED') return 'text-red-500'
  if (step.isOverdue) return 'text-red-500'
  if (step.status === 'PENDING') return 'text-yellow-500'
  return 'text-gray-400'
}

function stepBg(step: ApprovalChainSlaStatus): string {
  if (step.status === 'APPROVED') return 'bg-green-100 dark:bg-green-900/30'
  if (step.status === 'REJECTED') return 'bg-red-100 dark:bg-red-900/30'
  if (step.isOverdue) return 'bg-red-100 dark:bg-red-900/30'
  if (step.status === 'PENDING') return 'bg-yellow-100 dark:bg-yellow-900/30'
  return 'bg-gray-100 dark:bg-gray-800'
}

function connectorColor(index: number): string {
  if (index >= steps.value.length - 1) return ''
  const step = steps.value[index]
  if (step.status === 'APPROVED') return 'bg-green-400 dark:bg-green-600'
  return 'bg-gray-300 dark:bg-gray-600'
}

const countdown = computed(() => {
  return steps.value.map((step) => {
    if (step.remainingMinutes == null || step.status !== 'PENDING') return ''
    const remaining = step.remainingMinutes
    if (remaining < 0) {
      const overdue = Math.abs(remaining)
      const hours = Math.floor(overdue / 60)
      const mins = overdue % 60
      return hours > 0 ? `${hours}시간 ${mins}분 초과` : `${mins}분 초과`
    }
    const hours = Math.floor(remaining / 60)
    const mins = remaining % 60
    return hours > 0 ? `${hours}시간 ${mins}분 남음` : `${mins}분 남음`
  })
})
</script>

<template>
  <div v-if="loading" class="flex items-center gap-2 py-2">
    <div class="h-3 w-3 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" />
    <span class="text-xs text-gray-400">체인 로딩 중...</span>
  </div>
  <div v-else-if="steps.length > 0" class="py-2">
    <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">승인 체인</p>
    <div class="flex items-center gap-0">
      <template v-for="(step, idx) in steps" :key="step.stepId">
        <div class="flex flex-col items-center">
          <div
            class="flex h-8 w-8 items-center justify-center rounded-full"
            :class="stepBg(step)"
            :title="`${step.approverName} (${step.status})`"
          >
            <component :is="stepIcon(step)" class="h-4 w-4" :class="stepColor(step)" />
          </div>
          <span class="mt-1 max-w-[60px] truncate text-center text-[10px] text-gray-600 dark:text-gray-400">
            {{ step.approverName }}
          </span>
          <span
            v-if="countdown[idx]"
            class="mt-0.5 text-[10px] font-medium"
            :class="step.isOverdue ? 'text-red-500' : 'text-yellow-600 dark:text-yellow-400'"
          >
            {{ countdown[idx] }}
          </span>
        </div>
        <div
          v-if="idx < steps.length - 1"
          class="mx-1 h-0.5 w-6 flex-shrink-0"
          :class="connectorColor(idx)"
        />
      </template>
    </div>
  </div>
</template>
