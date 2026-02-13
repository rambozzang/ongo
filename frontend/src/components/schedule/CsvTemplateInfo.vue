<script setup lang="ts">
import { ref } from 'vue'
import {
  ChevronDownIcon,
  ChevronUpIcon,
  ArrowDownTrayIcon,
  InformationCircleIcon,
} from '@heroicons/vue/24/outline'
import { useCsvImportStore } from '@/stores/csvImport'

const store = useCsvImportStore()
const expanded = ref(false)

function toggleExpanded() {
  expanded.value = !expanded.value
}

function downloadTemplate() {
  const headers = store.templateColumns.map((col) => col.label).join(',')
  const exampleRow = store.templateColumns.map((col) => col.example).join(',')
  const csvContent = `${headers}\n${exampleRow}\n`

  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'ongo_schedule_template.csv'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50">
    <!-- Header (always visible) -->
    <button
      type="button"
      class="flex w-full items-center justify-between px-4 py-3 text-left"
      @click="toggleExpanded"
    >
      <div class="flex items-center gap-2">
        <InformationCircleIcon class="h-5 w-5 text-primary-500" />
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
          CSV 템플릿 형식 안내
        </span>
      </div>
      <ChevronUpIcon v-if="expanded" class="h-4 w-4 text-gray-500 dark:text-gray-400" />
      <ChevronDownIcon v-else class="h-4 w-4 text-gray-500 dark:text-gray-400" />
    </button>

    <!-- Expandable content -->
    <div v-if="expanded" class="border-t border-gray-200 px-4 py-4 dark:border-gray-700">
      <!-- Column descriptions table -->
      <div class="mb-4 overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700">
              <th class="pb-2 pr-4 text-left font-medium text-gray-700 dark:text-gray-300">
                컬럼
              </th>
              <th class="pb-2 pr-4 text-left font-medium text-gray-700 dark:text-gray-300">
                필수
              </th>
              <th class="pb-2 pr-4 text-left font-medium text-gray-700 dark:text-gray-300">
                설명
              </th>
              <th class="pb-2 text-left font-medium text-gray-700 dark:text-gray-300">
                예시
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="col in store.templateColumns"
              :key="col.key"
              class="border-b border-gray-100 last:border-b-0 dark:border-gray-700/50"
            >
              <td class="py-2 pr-4 font-mono text-xs text-gray-900 dark:text-gray-100">
                {{ col.label }}
              </td>
              <td class="py-2 pr-4">
                <span
                  v-if="col.required"
                  class="inline-block rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/30 dark:text-red-400"
                >
                  필수
                </span>
                <span
                  v-else
                  class="inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-gray-700 dark:text-gray-400"
                >
                  선택
                </span>
              </td>
              <td class="py-2 pr-4 text-gray-600 dark:text-gray-400">
                {{ col.description }}
              </td>
              <td class="py-2 font-mono text-xs text-gray-500 dark:text-gray-400">
                {{ col.example }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Example row -->
      <div class="mb-4 rounded-md bg-gray-100 p-3 dark:bg-gray-900/50">
        <p class="mb-1 text-xs font-medium text-gray-600 dark:text-gray-400">예시 행:</p>
        <code class="block overflow-x-auto whitespace-nowrap text-xs text-gray-800 dark:text-gray-200">
          {{ store.templateColumns.map((col) => col.example).join(',') }}
        </code>
      </div>

      <!-- Download button -->
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-lg border border-primary-300 bg-white px-4 py-2 text-sm font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:border-primary-700 dark:bg-gray-800 dark:text-primary-400 dark:hover:bg-primary-900/20"
        @click="downloadTemplate"
      >
        <ArrowDownTrayIcon class="h-4 w-4" />
        CSV 템플릿 다운로드
      </button>
    </div>
  </div>
</template>
