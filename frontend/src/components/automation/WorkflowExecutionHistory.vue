<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { automationApi } from '@/api/automation'
import type { WorkflowExecution } from '@/types/automation'

const props = defineProps<{
  workflowId: number
}>()

const executions = ref<WorkflowExecution[]>([])
const loading = ref(false)
const expandedId = ref<number | null>(null)

async function fetchHistory() {
  loading.value = true
  try {
    executions.value = await automationApi.getWorkflowHistory(props.workflowId)
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

function statusBadgeClass(status: string) {
  switch (status) {
    case 'COMPLETED':
      return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
    case 'FAILED':
      return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300'
    case 'RUNNING':
      return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
    case 'CANCELLED':
      return 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
    default:
      return 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
  }
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('ko-KR')
}

onMounted(fetchHistory)
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">실행 이력</h3>
      <button
        class="text-xs text-primary-600 dark:text-primary-400 hover:underline"
        @click="fetchHistory"
      >
        새로고침
      </button>
    </div>

    <div v-if="loading" class="text-center py-6 text-sm text-gray-500">
      로딩 중...
    </div>

    <div v-else-if="executions.length === 0" class="text-center py-6 text-sm text-gray-400">
      실행 이력이 없습니다
    </div>

    <div v-else class="space-y-2">
      <div
        v-for="exec in executions"
        :key="exec.id"
        class="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden"
      >
        <div
          class="flex items-center justify-between p-3 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
          @click="toggleExpand(exec.id)"
        >
          <div class="flex items-center gap-2">
            <span
              class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
              :class="statusBadgeClass(exec.status)"
            >
              {{ exec.status }}
            </span>
            <span class="text-xs text-gray-500 dark:text-gray-400">
              {{ formatDate(exec.startedAt) }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-xs text-gray-500">
              {{ exec.actionResults.length }}개 액션
            </span>
            <svg
              class="w-4 h-4 text-gray-400 transition-transform"
              :class="{ 'rotate-180': expandedId === exec.id }"
              fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>

        <div v-if="expandedId === exec.id" class="border-t border-gray-200 dark:border-gray-700 p-3 space-y-2 bg-gray-50 dark:bg-gray-800/50">
          <div v-if="exec.errorMessage" class="text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded p-2">
            {{ exec.errorMessage }}
          </div>
          <div v-for="(result, i) in exec.actionResults" :key="i" class="flex items-center gap-2 text-xs">
            <span
              class="w-2 h-2 rounded-full flex-shrink-0"
              :class="{
                'bg-green-500': result.status === 'COMPLETED',
                'bg-red-500': result.status === 'FAILED',
                'bg-gray-400': result.status === 'SKIPPED',
              }"
            />
            <span class="text-gray-700 dark:text-gray-300 font-medium">{{ result.actionType }}</span>
            <span class="text-gray-500 dark:text-gray-400">{{ result.message }}</span>
            <span class="ml-auto text-gray-400">{{ formatDate(result.executedAt) }}</span>
          </div>
          <div v-if="exec.completedAt" class="text-xs text-gray-400 pt-1">
            완료: {{ formatDate(exec.completedAt) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
