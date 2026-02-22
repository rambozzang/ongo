<template>
  <div class="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
    <table class="w-full">
      <thead class="bg-gray-50 dark:bg-gray-800">
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            class="cursor-pointer px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
            @click="sortBy(column.key)"
          >
            <div class="flex items-center gap-1">
              {{ column.label }}
              <span v-if="sortKey === column.key" class="text-primary-600">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </div>
          </th>
        </tr>
      </thead>
      <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
        <tr
          v-for="(row, index) in sortedData"
          :key="row.period"
          class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-800"
          :class="index % 2 === 0 ? 'bg-white dark:bg-gray-900' : 'bg-gray-50/50 dark:bg-gray-800/50'"
        >
          <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-900 dark:text-gray-100">
            {{ formatPeriod(row.period) }}
          </td>
          <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
            {{ formatCurrency(row.youtube) }}
          </td>
          <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
            {{ formatCurrency(row.tiktok) }}
          </td>
          <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
            {{ formatCurrency(row.instagram) }}
          </td>
          <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
            {{ formatCurrency(row.naverClip) }}
          </td>
          <td class="whitespace-nowrap px-4 py-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ formatCurrency(row.total) }}
          </td>
          <td class="whitespace-nowrap px-4 py-3 text-sm">
            <span
              v-if="row.change !== null"
              :class="row.change >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
            >
              {{ row.change >= 0 ? '+' : '' }}{{ row.change.toFixed(1) }}%
            </span>
            <span v-else class="text-gray-400">-</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RevenueData } from '@/stores/revenue'

interface Props {
  data: RevenueData[]
}

const props = defineProps<Props>()

interface Column {
  key: string
  label: string
}

const columns: Column[] = [
  { key: 'period', label: '기간' },
  { key: 'youtube', label: 'YouTube' },
  { key: 'tiktok', label: 'TikTok' },
  { key: 'instagram', label: 'Instagram' },
  { key: 'naverClip', label: 'Naver Clip' },
  { key: 'total', label: '합계' },
  { key: 'change', label: '증감' },
]

const sortKey = ref<string>('period')
const sortOrder = ref<'asc' | 'desc'>('asc')

// Calculate change percentages
const dataWithChange = computed(() => {
  return props.data.map((item, index) => {
    let change: number | null = null
    if (index > 0) {
      const previous = props.data[index - 1].total
      change = ((item.total - previous) / previous) * 100
    }
    return { ...item, change }
  })
})

const sortedData = computed(() => {
  const data = [...dataWithChange.value]

  data.sort((a, b) => {
    let aVal: string | number | null = a[sortKey.value as keyof typeof a] as string | number | null
    let bVal: string | number | null = b[sortKey.value as keyof typeof b] as string | number | null

    // Handle null values for change column
    if (aVal === null) aVal = -Infinity
    if (bVal === null) bVal = -Infinity

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return sortOrder.value === 'asc'
        ? aVal.localeCompare(bVal)
        : bVal.localeCompare(aVal)
    }
    
    const numA = typeof aVal === 'number' ? aVal : Number(aVal)
    const numB = typeof bVal === 'number' ? bVal : Number(bVal)

    return sortOrder.value === 'asc' ? numA - numB : numB - numA
  })

  return data
})

function sortBy(key: string) {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortOrder.value = 'asc'
  }
}

function formatPeriod(period: string): string {
  const [year, month] = period.split('-')
  return `${year}년 ${month}월`
}

function formatCurrency(value: number): string {
  return `₩${value.toLocaleString('ko-KR')}`
}
</script>
