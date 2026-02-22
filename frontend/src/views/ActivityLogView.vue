<script setup lang="ts">
import { onMounted, computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  ArrowDownTrayIcon,
  ArrowUpTrayIcon,
  CheckCircleIcon,
  SparklesIcon,
  ClockIcon,
  TrashIcon,
  LinkIcon,
} from '@heroicons/vue/24/outline'
import ActivityLogFilter from '@/components/activitylog/ActivityLogFilter.vue'
import ActivityLogTimeline from '@/components/activitylog/ActivityLogTimeline.vue'
import { useActivityLogsStore } from '@/stores/activityLogs'
import PageGuide from '@/components/common/PageGuide.vue'
import type { ActivityLog, ActivityAction, ActivityDateRange, ActivityDateCustomRange } from '@/types/activitylog'

const router = useRouter()
const activityLogsStore = useActivityLogsStore()

const { isLoading, filter, customDateRange, groupedByDate, actionCounts, uniqueUsers } =
  storeToRefs(activityLogsStore)

// Summary cards showing today's activity counts
interface SummaryCardData {
  label: string
  key: ActivityAction
  icon: Component
  colorClass: string
}

const summaryCards: SummaryCardData[] = [
  { label: '업로드', key: 'upload', icon: ArrowUpTrayIcon, colorClass: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30' },
  { label: '게시', key: 'publish', icon: CheckCircleIcon, colorClass: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30' },
  { label: '예약', key: 'schedule', icon: ClockIcon, colorClass: 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30' },
  { label: 'AI 사용', key: 'ai_generate', icon: SparklesIcon, colorClass: 'text-indigo-600 dark:text-indigo-400 bg-indigo-100 dark:bg-indigo-900/30' },
  { label: '삭제', key: 'delete', icon: TrashIcon, colorClass: 'text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-900/30' },
  { label: '채널 연결', key: 'channel_connect', icon: LinkIcon, colorClass: 'text-teal-600 dark:text-teal-400 bg-teal-100 dark:bg-teal-900/30' },
]

const selectedAction = computed(() => filter.value.action ?? null)
const selectedDateRange = computed(() => filter.value.dateRange ?? null)
const selectedUserId = computed(() => filter.value.userId ?? null)
const searchQuery = computed(() => filter.value.searchQuery ?? '')

function handleActionUpdate(action: ActivityAction | null) {
  activityLogsStore.filterByAction(action)
}

function handleDateRangeUpdate(range: ActivityDateRange | null, custom?: ActivityDateCustomRange) {
  activityLogsStore.filterByDate(range, custom)
}

function handleUserUpdate(userId: string | null) {
  activityLogsStore.filterByUser(userId)
}

function handleSearchUpdate(query: string) {
  activityLogsStore.setSearchQuery(query)
}

function handleResetFilters() {
  activityLogsStore.resetFilters()
}

function handleExport() {
  activityLogsStore.exportLogs()
}

function handleEntityClick(log: ActivityLog) {
  switch (log.entityType) {
    case 'video':
      if (log.entityId) {
        router.push(`/videos/${log.entityId}`)
      }
      break
    case 'schedule':
      router.push('/schedule')
      break
    case 'channel':
      router.push('/channels')
      break
    default:
      break
  }
}

onMounted(() => {
  activityLogsStore.fetchLogs()
})
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">활동 로그</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          모든 활동 내역을 확인하고 관리할 수 있습니다
        </p>
      </div>
      <button
        class="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        @click="handleExport"
      >
        <ArrowDownTrayIcon class="h-4 w-4" />
        CSV 내보내기
      </button>
    </div>

    <PageGuide title="활동 로그" :items="[
      '상단 요약 카드(업로드·게시·예약·AI 생성·삭제·채널 연결)에서 오늘의 활동 통계를 확인하세요',
      '활동 유형·날짜 범위(오늘/이번 주/이번 달/직접 지정)·사용자·검색으로 원하는 로그를 필터링하세요',
      '날짜별로 그룹화된 타임라인에서 각 작업의 시간·대상·결과를 상세히 확인할 수 있습니다',
      '로그 항목을 클릭하면 관련 영상·채널 등의 상세 페이지로 이동합니다',
      '내보내기 버튼으로 활동 로그를 CSV/JSON으로 다운로드하여 외부에서 분석할 수 있습니다',
    ]" />

    <!-- Today's activity summary cards -->
    <div class="mb-6 grid grid-cols-2 gap-3 tablet:grid-cols-3 desktop:grid-cols-6">
      <div
        v-for="card in summaryCards"
        :key="card.key"
        class="rounded-xl border border-gray-200 bg-white p-4 transition-colors dark:border-gray-700 dark:bg-gray-800"
      >
        <div class="flex items-center gap-3">
          <div
            class="flex h-9 w-9 items-center justify-center rounded-lg"
            :class="card.colorClass"
          >
            <component :is="card.icon" class="h-5 w-5" />
          </div>
          <div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">
              {{ actionCounts[card.key] ?? 0 }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              오늘 {{ card.label }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Filters -->
    <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <ActivityLogFilter
        :selected-action="selectedAction"
        :selected-date-range="selectedDateRange"
        :selected-user-id="selectedUserId"
        :search-query="searchQuery"
        :users="uniqueUsers"
        :custom-date-range="customDateRange"
        @update:action="handleActionUpdate"
        @update:date-range="handleDateRangeUpdate"
        @update:user-id="handleUserUpdate"
        @update:search-query="handleSearchUpdate"
        @reset="handleResetFilters"
      />
    </div>

    <!-- Timeline -->
    <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <ActivityLogTimeline
        :grouped-logs="groupedByDate"
        :is-loading="isLoading"
        @click-entity="handleEntityClick"
      />
    </div>
  </div>
</template>
