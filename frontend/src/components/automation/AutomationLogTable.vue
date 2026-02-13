<script setup lang="ts">
import { ref, computed } from 'vue'
import { CheckCircleIcon, XCircleIcon } from '@heroicons/vue/24/outline'
import type { AutomationLog } from '@/types/automation'

const props = defineProps<{
  logs: AutomationLog[]
}>()

const currentPage = ref(1)
const itemsPerPage = 10
const filterRuleName = ref('')

const filteredLogs = computed(() => {
  if (!filterRuleName.value) return props.logs
  return props.logs.filter(log =>
    log.ruleName.toLowerCase().includes(filterRuleName.value.toLowerCase())
  )
})

const paginatedLogs = computed(() => {
  const start = (currentPage.value - 1) * itemsPerPage
  const end = start + itemsPerPage
  return filteredLogs.value.slice(start, end)
})

const totalPages = computed(() => {
  return Math.ceil(filteredLogs.value.length / itemsPerPage)
})

const formatDateTime = (dateString: string) => {
  const date = new Date(dateString)
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const goToPage = (page: number) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
  }
}

const pageNumbers = computed(() => {
  const pages: number[] = []
  const maxVisible = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2))
  const end = Math.min(totalPages.value, start + maxVisible - 1)

  if (end - start + 1 < maxVisible) {
    start = Math.max(1, end - maxVisible + 1)
  }

  for (let i = start; i <= end; i++) {
    pages.push(i)
  }

  return pages
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
    <!-- Filter -->
    <div class="p-4 border-b border-gray-200 dark:border-gray-700">
      <input
        v-model="filterRuleName"
        type="text"
        placeholder="규칙명으로 검색..."
        class="w-full md:w-64 px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
      >
    </div>

    <!-- Table -->
    <div class="overflow-x-auto">
      <table class="w-full">
        <thead class="bg-gray-50 dark:bg-gray-900/50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
              규칙명
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
              상태
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
              메시지
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
              실행시간
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
          <tr
            v-for="log in paginatedLogs"
            :key="log.id"
            class="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
          >
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="text-sm font-medium text-gray-900 dark:text-white">
                {{ log.ruleName }}
              </div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span
                v-if="log.status === 'success'"
                class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300"
              >
                <CheckCircleIcon class="w-4 h-4" />
                성공
              </span>
              <span
                v-else
                class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300"
              >
                <XCircleIcon class="w-4 h-4" />
                실패
              </span>
            </td>
            <td class="px-6 py-4">
              <div class="text-sm text-gray-900 dark:text-white">
                {{ log.message }}
              </div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="text-sm text-gray-500 dark:text-gray-400">
                {{ formatDateTime(log.executedAt) }}
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Empty State -->
      <div
        v-if="paginatedLogs.length === 0"
        class="text-center py-12"
      >
        <p class="text-gray-500 dark:text-gray-400">
          {{ filterRuleName ? '검색 결과가 없습니다' : '실행 로그가 없습니다' }}
        </p>
      </div>
    </div>

    <!-- Pagination -->
    <div
      v-if="totalPages > 1"
      class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-between"
    >
      <div class="text-sm text-gray-700 dark:text-gray-300">
        전체 <span class="font-medium">{{ filteredLogs.length }}</span>개 중
        <span class="font-medium">{{ (currentPage - 1) * itemsPerPage + 1 }}</span>-<span class="font-medium">{{ Math.min(currentPage * itemsPerPage, filteredLogs.length) }}</span>
      </div>

      <div class="flex items-center gap-2">
        <button
          @click="goToPage(currentPage - 1)"
          :disabled="currentPage === 1"
          class="px-3 py-1 rounded-lg border border-gray-300 dark:border-gray-600 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          이전
        </button>

        <button
          v-for="page in pageNumbers"
          :key="page"
          @click="goToPage(page)"
          :class="[
            'px-3 py-1 rounded-lg text-sm font-medium transition-colors',
            page === currentPage
              ? 'bg-blue-600 text-white'
              : 'border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
          ]"
        >
          {{ page }}
        </button>

        <button
          @click="goToPage(currentPage + 1)"
          :disabled="currentPage === totalPages"
          class="px-3 py-1 rounded-lg border border-gray-300 dark:border-gray-600 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          다음
        </button>
      </div>
    </div>
  </div>
</template>
