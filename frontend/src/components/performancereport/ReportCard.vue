<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowDownTrayIcon,
  CalendarDaysIcon,
  EyeIcon,
  HeartIcon,
  StarIcon,
} from '@heroicons/vue/24/outline'
import ReportStatusBadge from './ReportStatusBadge.vue'
import type { PerformanceReport } from '@/types/performanceReport'

interface Props {
  report: PerformanceReport
}

interface Emits {
  (e: 'select', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const periodLabel = computed(() => {
  const labels: Record<string, string> = {
    WEEKLY: '주간',
    MONTHLY: '월간',
    QUARTERLY: '분기',
    YEARLY: '연간',
  }
  return labels[props.report.period] ?? props.report.period
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

function handleDownload(event: Event) {
  event.stopPropagation()
  if (props.report.reportUrl) {
    window.open(props.report.reportUrl, '_blank')
  }
}
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer"
    @click="emit('select', report.id)"
  >
    <!-- Header: Title + Status -->
    <div class="flex items-start justify-between mb-3">
      <div class="flex-1 min-w-0">
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ report.title }}
        </h3>
        <div class="flex items-center gap-2 mt-1">
          <CalendarDaysIcon class="w-4 h-4 text-gray-400 dark:text-gray-500" />
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ formatDate(report.startDate) }} ~ {{ formatDate(report.endDate) }}
          </span>
        </div>
      </div>
      <ReportStatusBadge :status="report.status" />
    </div>

    <!-- Period badge -->
    <div class="mb-3">
      <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400">
        {{ periodLabel }}
      </span>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-2 gap-2 mb-3">
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2.5 flex items-center gap-2">
        <EyeIcon class="w-4 h-4 text-gray-400 dark:text-gray-500 flex-shrink-0" />
        <div>
          <p class="text-xs text-gray-500 dark:text-gray-400">총 조회수</p>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(report.totalViews) }}</p>
        </div>
      </div>
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2.5 flex items-center gap-2">
        <HeartIcon class="w-4 h-4 text-gray-400 dark:text-gray-500 flex-shrink-0" />
        <div>
          <p class="text-xs text-gray-500 dark:text-gray-400">인게이지먼트</p>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(report.totalEngagement) }}</p>
        </div>
      </div>
    </div>

    <!-- Top video -->
    <div
      v-if="report.topVideoTitle"
      class="flex items-center gap-2 mb-3 text-sm text-gray-600 dark:text-gray-400"
    >
      <StarIcon class="w-4 h-4 text-yellow-500 flex-shrink-0" />
      <span class="truncate">인기 영상: {{ report.topVideoTitle }}</span>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end border-t border-gray-200 dark:border-gray-700 pt-3">
      <button
        v-if="report.reportUrl"
        @click="handleDownload"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition-colors"
      >
        <ArrowDownTrayIcon class="w-4 h-4" />
        다운로드
      </button>
      <span
        v-else
        class="text-xs text-gray-400 dark:text-gray-500"
      >
        {{ report.status === 'GENERATING' ? '생성 진행 중...' : report.status === 'SCHEDULED' ? '예약 대기' : '' }}
      </span>
    </div>
  </div>
</template>
