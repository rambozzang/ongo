<template>
  <div class="space-y-4">
    <div class="relative">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="태그 검색..."
        class="input-field w-full pl-9"
      />
      <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
    </div>

    <div class="overflow-x-auto">
      <table v-if="displayedTags.length > 0" class="w-full text-left text-sm">
        <thead>
          <tr class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <th class="cursor-pointer whitespace-nowrap px-3 py-3 font-medium hover:text-gray-700 dark:hover:text-gray-300" @click="sortBy('tag')">
              <div class="flex items-center gap-1">
                태그
                <component :is="getSortIcon('tag')" class="h-3 w-3" />
              </div>
            </th>
            <th class="cursor-pointer whitespace-nowrap px-3 py-3 text-right font-medium hover:text-gray-700 dark:hover:text-gray-300" @click="sortBy('videoCount')">
              <div class="flex items-center justify-end gap-1">
                영상 수
                <component :is="getSortIcon('videoCount')" class="h-3 w-3" />
              </div>
            </th>
            <th class="hidden cursor-pointer whitespace-nowrap px-3 py-3 text-right font-medium hover:text-gray-700 tablet:table-cell dark:hover:text-gray-300" @click="sortBy('totalViews')">
              <div class="flex items-center justify-end gap-1">
                총 조회수
                <component :is="getSortIcon('totalViews')" class="h-3 w-3" />
              </div>
            </th>
            <th class="cursor-pointer whitespace-nowrap px-3 py-3 text-right font-medium hover:text-gray-700 dark:hover:text-gray-300" @click="sortBy('avgViews')">
              <div class="flex items-center justify-end gap-1">
                평균 조회수
                <component :is="getSortIcon('avgViews')" class="h-3 w-3" />
              </div>
            </th>
            <th class="hidden cursor-pointer whitespace-nowrap px-3 py-3 text-right font-medium hover:text-gray-700 tablet:table-cell dark:hover:text-gray-300" @click="sortBy('avgEngagement')">
              <div class="flex items-center justify-end gap-1">
                평균 참여율
                <component :is="getSortIcon('avgEngagement')" class="h-3 w-3" />
              </div>
            </th>
            <th class="whitespace-nowrap px-3 py-3 text-center font-medium">
              트렌드
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
          <tr
            v-for="tag in paginatedTags"
            :key="tag.tag"
            class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
          >
            <td class="whitespace-nowrap px-3 py-3 font-medium text-primary-600 dark:text-primary-400">
              #{{ tag.tag }}
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right text-gray-900 dark:text-gray-100">
              {{ tag.videoCount }}
            </td>
            <td class="hidden whitespace-nowrap px-3 py-3 text-right text-gray-700 tablet:table-cell dark:text-gray-300">
              {{ formatNumber(tag.totalViews) }}
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-right text-gray-700 dark:text-gray-300">
              {{ formatNumber(tag.avgViews) }}
            </td>
            <td class="hidden whitespace-nowrap px-3 py-3 text-right text-gray-700 tablet:table-cell dark:text-gray-300">
              {{ tag.avgEngagement.toFixed(1) }}%
            </td>
            <td class="whitespace-nowrap px-3 py-3 text-center">
              <span
                class="inline-flex items-center justify-center"
                :class="{
                  'text-green-600 dark:text-green-400': tag.trend === 'up',
                  'text-red-600 dark:text-red-400': tag.trend === 'down',
                  'text-gray-400 dark:text-gray-500': tag.trend === 'stable',
                }"
              >
                <ArrowTrendingUpIcon v-if="tag.trend === 'up'" class="h-4 w-4" />
                <ArrowTrendingDownIcon v-if="tag.trend === 'down'" class="h-4 w-4" />
                <MinusIcon v-if="tag.trend === 'stable'" class="h-4 w-4" />
              </span>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="flex h-32 items-center justify-center text-sm text-gray-400 dark:text-gray-500">
        {{ searchQuery ? '검색 결과가 없습니다' : '태그 데이터가 없습니다' }}
      </div>
    </div>

    <div v-if="filteredTags.length > limit && !showAll" class="flex justify-center">
      <button class="btn-secondary text-xs" @click="showAll = true">
        더 보기 ({{ filteredTags.length - limit }}개 더)
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TagPerformance } from '@/types/analytics'
import {
  MagnifyingGlassIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  ChevronUpIcon,
  ChevronDownIcon,
} from '@heroicons/vue/24/outline'

interface Props {
  tags: TagPerformance[]
}

const props = defineProps<Props>()

const searchQuery = ref('')
const sortColumn = ref<keyof TagPerformance>('videoCount')
const sortDirection = ref<'asc' | 'desc'>('desc')
const limit = ref(20)
const showAll = ref(false)

const filteredTags = computed(() => {
  let result = props.tags

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase().replace(/^#/, '')
    result = result.filter((tag) => tag.tag.toLowerCase().includes(query))
  }

  return result
})

const sortedTags = computed(() => {
  const sorted = [...filteredTags.value]

  sorted.sort((a, b) => {
    const aVal = a[sortColumn.value]
    const bVal = b[sortColumn.value]

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return sortDirection.value === 'asc'
        ? aVal.localeCompare(bVal, 'ko')
        : bVal.localeCompare(aVal, 'ko')
    }

    if (typeof aVal === 'number' && typeof bVal === 'number') {
      return sortDirection.value === 'asc' ? aVal - bVal : bVal - aVal
    }

    return 0
  })

  return sorted
})

const paginatedTags = computed(() => {
  if (showAll.value) {
    return sortedTags.value
  }
  return sortedTags.value.slice(0, limit.value)
})

const displayedTags = computed(() => {
  return paginatedTags.value
})

function sortBy(column: keyof TagPerformance) {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortColumn.value = column
    sortDirection.value = 'desc'
  }
}

function getSortIcon(column: keyof TagPerformance) {
  if (sortColumn.value !== column) {
    return 'span'
  }
  return sortDirection.value === 'asc' ? ChevronUpIcon : ChevronDownIcon
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString('ko-KR')
}
</script>
