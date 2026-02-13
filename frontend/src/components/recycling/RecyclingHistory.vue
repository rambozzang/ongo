<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  CheckCircleIcon,
  ClockIcon,
  XCircleIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import type { RecyclingHistory as RecyclingHistoryType, RecyclingStatus } from '@/types/recycling'

const props = defineProps<{
  history: RecyclingHistoryType[]
}>()

const dateFrom = ref('')
const dateTo = ref('')
const statusFilter = ref<RecyclingStatus | ''>('')

const PLATFORM_LABELS: Record<string, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM: 'Instagram',
  NAVER_CLIP: 'Naver Clip',
}

const STATUS_CONFIG: Record<RecyclingStatus, { label: string; icon: typeof CheckCircleIcon; classes: string }> = {
  published: {
    label: '게시됨',
    icon: CheckCircleIcon,
    classes: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  },
  pending: {
    label: '대기중',
    icon: ClockIcon,
    classes: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  },
  failed: {
    label: '실패',
    icon: XCircleIcon,
    classes: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  },
}

const filteredHistory = computed(() => {
  let items = [...props.history]

  if (statusFilter.value) {
    items = items.filter((h) => h.status === statusFilter.value)
  }

  if (dateFrom.value) {
    const from = new Date(dateFrom.value)
    items = items.filter((h) => new Date(h.scheduledAt) >= from)
  }

  if (dateTo.value) {
    const to = new Date(dateTo.value)
    to.setHours(23, 59, 59, 999)
    items = items.filter((h) => new Date(h.scheduledAt) <= to)
  }

  return items.sort(
    (a, b) => new Date(b.scheduledAt).getTime() - new Date(a.scheduledAt).getTime()
  )
})

const hasActiveFilters = computed(() => {
  return statusFilter.value !== '' || dateFrom.value !== '' || dateTo.value !== ''
})

function clearFilters() {
  dateFrom.value = ''
  dateTo.value = ''
  statusFilter.value = ''
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${year}.${month}.${day} ${hours}:${minutes}`
}

function getPlatformLabels(platforms: string[]): string {
  return platforms.map((p) => PLATFORM_LABELS[p] || p).join(', ')
}
</script>

<template>
  <div>
    <!-- Filters -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 mb-4">
      <div class="flex items-center gap-2 mb-3">
        <FunnelIcon class="w-4 h-4 text-gray-500 dark:text-gray-400" />
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">필터</span>
        <button
          v-if="hasActiveFilters"
          @click="clearFilters"
          class="ml-auto text-xs text-blue-600 dark:text-blue-400 hover:underline"
        >
          초기화
        </button>
      </div>

      <div class="flex flex-wrap gap-3">
        <div class="flex items-center gap-2">
          <label class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">시작일</label>
          <input
            v-model="dateFrom"
            type="date"
            class="px-3 py-1.5 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
          />
        </div>
        <div class="flex items-center gap-2">
          <label class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">종료일</label>
          <input
            v-model="dateTo"
            type="date"
            class="px-3 py-1.5 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
          />
        </div>
        <div class="flex items-center gap-2">
          <label class="text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">상태</label>
          <select
            v-model="statusFilter"
            class="px-3 py-1.5 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
          >
            <option value="">전체</option>
            <option value="published">게시됨</option>
            <option value="pending">대기중</option>
            <option value="failed">실패</option>
          </select>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700">
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                영상 제목
              </th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                플랫폼
              </th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                예정일
              </th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                게시일
              </th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                상태
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
            <tr
              v-for="item in filteredHistory"
              :key="item.id"
              class="hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
            >
              <td class="px-6 py-4">
                <span class="text-sm font-medium text-gray-900 dark:text-white">
                  {{ item.videoTitle }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-gray-600 dark:text-gray-400">
                  {{ getPlatformLabels(item.platforms) }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-gray-600 dark:text-gray-400">
                  {{ formatDate(item.scheduledAt) }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span class="text-sm text-gray-600 dark:text-gray-400">
                  {{ item.publishedAt ? formatDate(item.publishedAt) : '-' }}
                </span>
              </td>
              <td class="px-6 py-4">
                <span
                  :class="[
                    'inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium',
                    STATUS_CONFIG[item.status].classes,
                  ]"
                >
                  <component :is="STATUS_CONFIG[item.status].icon" class="w-3.5 h-3.5" />
                  {{ STATUS_CONFIG[item.status].label }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Empty State -->
      <div
        v-if="filteredHistory.length === 0"
        class="text-center py-12"
      >
        <ClockIcon class="w-12 h-12 mx-auto text-gray-400 dark:text-gray-600 mb-3" />
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ hasActiveFilters ? '조건에 맞는 기록이 없습니다' : '재활용 기록이 없습니다' }}
        </p>
      </div>
    </div>
  </div>
</template>
