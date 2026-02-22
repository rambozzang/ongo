<script setup lang="ts">
import { CheckIcon, XMarkIcon, ClockIcon } from '@heroicons/vue/24/solid'
import type { WorkflowStep } from '@/types/approval'

defineProps<{
  steps: WorkflowStep[]
}>()

function stepClass(status: string): string {
  switch (status) {
    case 'APPROVED':
      return 'bg-green-500 text-white'
    case 'REJECTED':
      return 'bg-red-500 text-white'
    case 'PENDING':
      return 'bg-yellow-400 text-white ring-2 ring-yellow-200 dark:ring-yellow-800'
    case 'WAITING':
    default:
      return 'bg-gray-200 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
  }
}

function lineClass(status: string): string {
  switch (status) {
    case 'APPROVED':
      return 'bg-green-400'
    case 'REJECTED':
      return 'bg-red-400'
    default:
      return 'bg-gray-200 dark:bg-gray-700'
  }
}
</script>

<template>
  <div class="flex items-center">
    <template v-for="(step, index) in steps" :key="step.stepOrder">
      <!-- Connector Line -->
      <div
        v-if="index > 0"
        class="h-0.5 flex-1"
        :class="lineClass(steps[index - 1].status)"
      ></div>

      <!-- Step Circle -->
      <div
        class="relative flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full transition-all"
        :class="stepClass(step.status)"
        :title="`${step.stepName}: ${step.reviewerName ?? '미정'} (${step.status})`"
      >
        <CheckIcon v-if="step.status === 'APPROVED'" class="h-3.5 w-3.5" />
        <XMarkIcon v-else-if="step.status === 'REJECTED'" class="h-3.5 w-3.5" />
        <ClockIcon v-else-if="step.status === 'PENDING'" class="h-3.5 w-3.5" />
        <span v-else class="text-[10px] font-bold">{{ step.stepOrder }}</span>
      </div>
    </template>
  </div>
</template>
