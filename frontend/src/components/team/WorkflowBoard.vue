<script setup lang="ts">
import { onMounted, computed } from 'vue'
import {
  DocumentTextIcon,
  PencilSquareIcon,
  MagnifyingGlassIcon,
  CheckCircleIcon,
  GlobeAltIcon,
  ClockIcon,
  UserIcon,
} from '@heroicons/vue/24/outline'
import { useApprovalStore } from '@/stores/approval'
import WorkflowStepIndicator from '@/components/team/WorkflowStepIndicator.vue'
import type { WorkflowColumn, WorkflowItem } from '@/types/approval'

const approvalStore = useApprovalStore()

const columns = computed(() => approvalStore.workflowBoard?.columns ?? [])
const stats = computed(() => approvalStore.workflowBoard?.stats)

const columnIcons: Record<string, any> = {
  DRAFT: DocumentTextIcon,
  EDITING: PencilSquareIcon,
  PENDING: MagnifyingGlassIcon,
  APPROVED: CheckCircleIcon,
  PUBLISHED: GlobeAltIcon,
}

const columnColors: Record<string, string> = {
  DRAFT: 'border-gray-300 dark:border-gray-600',
  EDITING: 'border-blue-400 dark:border-blue-500',
  PENDING: 'border-yellow-400 dark:border-yellow-500',
  APPROVED: 'border-green-400 dark:border-green-500',
  PUBLISHED: 'border-purple-400 dark:border-purple-500',
}

const columnHeaderBg: Record<string, string> = {
  DRAFT: 'bg-gray-100 dark:bg-gray-800',
  EDITING: 'bg-blue-50 dark:bg-blue-900/20',
  PENDING: 'bg-yellow-50 dark:bg-yellow-900/20',
  APPROVED: 'bg-green-50 dark:bg-green-900/20',
  PUBLISHED: 'bg-purple-50 dark:bg-purple-900/20',
}

const statusBadgeClass: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  EDITING: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  PENDING: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
  APPROVED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  PUBLISHED: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  REJECTED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
}

const platformColors: Record<string, string> = {
  youtube: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  tiktok: 'bg-gray-900 text-white dark:bg-gray-700',
  instagram: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-300',
  naverclip: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))

  if (diffHours < 1) return '방금 전'
  if (diffHours < 24) return `${diffHours}시간 전`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays}일 전`
  return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

onMounted(() => {
  approvalStore.fetchWorkflowBoard()
})
</script>

<template>
  <div>
    <!-- Stats Summary -->
    <div v-if="stats" class="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
      <div class="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800">
        <p class="text-2xl font-bold text-yellow-600 dark:text-yellow-400">{{ stats.totalPending }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">대기 중</p>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800">
        <p class="text-2xl font-bold text-blue-600 dark:text-blue-400">{{ stats.totalInReview }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">검수 중</p>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800">
        <p class="text-2xl font-bold text-green-600 dark:text-green-400">{{ stats.totalApproved }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">승인됨</p>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800">
        <p class="text-2xl font-bold text-red-600 dark:text-red-400">{{ stats.totalRejected }}</p>
        <p class="text-xs text-gray-500 dark:text-gray-400">거절됨</p>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 text-center dark:border-gray-700 dark:bg-gray-800">
        <p class="text-2xl font-bold text-indigo-600 dark:text-indigo-400">
          {{ stats.avgApprovalTimeHours != null ? `${stats.avgApprovalTimeHours.toFixed(1)}h` : '-' }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400">평균 승인 시간</p>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="approvalStore.workflowLoading" class="flex items-center justify-center py-12">
      <div class="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent"></div>
      <span class="ml-3 text-sm text-gray-500 dark:text-gray-400">워크플로우 보드 로딩 중...</span>
    </div>

    <!-- Kanban Board -->
    <div v-else class="flex gap-4 overflow-x-auto pb-4">
      <div
        v-for="col in columns"
        :key="col.status"
        class="flex w-72 min-w-[18rem] flex-shrink-0 flex-col rounded-lg border-t-4"
        :class="columnColors[col.status] ?? 'border-gray-300'"
      >
        <!-- Column Header -->
        <div
          class="flex items-center justify-between rounded-t-lg px-4 py-3"
          :class="columnHeaderBg[col.status] ?? 'bg-gray-100 dark:bg-gray-800'"
        >
          <div class="flex items-center space-x-2">
            <component
              :is="columnIcons[col.status]"
              class="h-5 w-5 text-gray-600 dark:text-gray-400"
            />
            <span class="text-sm font-semibold text-gray-900 dark:text-white">
              {{ col.statusLabel }}
            </span>
          </div>
          <span
            class="inline-flex h-6 min-w-[1.5rem] items-center justify-center rounded-full px-2 text-xs font-medium"
            :class="statusBadgeClass[col.status] ?? 'bg-gray-100 text-gray-700'"
          >
            {{ col.count }}
          </span>
        </div>

        <!-- Cards -->
        <div class="flex-1 space-y-3 overflow-y-auto bg-gray-50 p-3 dark:bg-gray-900/50" style="max-height: 60vh;">
          <div
            v-for="item in col.items"
            :key="item.approvalId"
            class="rounded-lg border border-gray-200 bg-white p-3 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
          >
            <!-- Video Title -->
            <h4 class="truncate text-sm font-medium text-gray-900 dark:text-white">
              {{ item.videoTitle }}
            </h4>

            <!-- Platforms -->
            <div class="mt-2 flex flex-wrap gap-1">
              <span
                v-for="platform in item.platforms"
                :key="platform"
                class="inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium"
                :class="platformColors[platform.toLowerCase()] ?? 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'"
              >
                {{ platform }}
              </span>
            </div>

            <!-- Requester / Reviewer -->
            <div class="mt-2 space-y-1">
              <div class="flex items-center text-xs text-gray-500 dark:text-gray-400">
                <UserIcon class="mr-1 h-3.5 w-3.5" />
                <span>{{ item.requesterName }}</span>
                <template v-if="item.reviewerName">
                  <span class="mx-1">&rarr;</span>
                  <span class="font-medium text-gray-700 dark:text-gray-300">{{ item.reviewerName }}</span>
                </template>
              </div>
            </div>

            <!-- Scheduled date -->
            <div v-if="item.scheduledAt" class="mt-1.5 flex items-center text-xs text-gray-400 dark:text-gray-500">
              <ClockIcon class="mr-1 h-3.5 w-3.5" />
              <span>{{ formatDate(item.scheduledAt) }}</span>
            </div>

            <!-- Chain Steps -->
            <WorkflowStepIndicator
              v-if="item.chainSteps && item.chainSteps.length > 0"
              :steps="item.chainSteps"
              class="mt-3"
            />

            <!-- Requested time -->
            <div class="mt-2 text-right text-[10px] text-gray-400 dark:text-gray-500">
              {{ formatDate(item.requestedAt) }}
            </div>
          </div>

          <!-- Empty state -->
          <div v-if="col.items.length === 0" class="py-8 text-center">
            <component
              :is="columnIcons[col.status]"
              class="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600"
            />
            <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">항목 없음</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
