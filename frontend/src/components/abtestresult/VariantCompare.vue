<script setup lang="ts">
import { computed } from 'vue'
import type { TestVariant } from '@/types/abTestResult'

const props = defineProps<{
  variants: TestVariant[]
}>()

const maxValues = computed(() => ({
  views: Math.max(...props.variants.map((v) => v.views)),
  ctr: Math.max(...props.variants.map((v) => v.ctr)),
  avgWatchTime: Math.max(...props.variants.map((v) => v.avgWatchTime)),
  engagement: Math.max(...props.variants.map((v) => v.engagement)),
  conversions: Math.max(...props.variants.map((v) => v.conversions)),
}))

const isMax = (field: keyof typeof maxValues.value, value: number) => {
  return value === maxValues.value[field] && value > 0
}

const formatTime = (seconds: number) => {
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return `${min}:${String(sec).padStart(2, '0')}`
}
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-gray-200 dark:border-gray-700">
          <th class="py-3 pr-4 text-left text-xs font-semibold text-gray-500 dark:text-gray-400">변형명</th>
          <th class="px-3 py-3 text-right text-xs font-semibold text-gray-500 dark:text-gray-400">조회수</th>
          <th class="px-3 py-3 text-right text-xs font-semibold text-gray-500 dark:text-gray-400">CTR</th>
          <th class="px-3 py-3 text-right text-xs font-semibold text-gray-500 dark:text-gray-400">평균 시청시간</th>
          <th class="px-3 py-3 text-right text-xs font-semibold text-gray-500 dark:text-gray-400">참여율</th>
          <th class="pl-3 py-3 text-right text-xs font-semibold text-gray-500 dark:text-gray-400">전환수</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="variant in variants"
          :key="variant.id"
          class="border-b border-gray-100 transition-colors dark:border-gray-800"
          :class="variant.isWinner
            ? 'bg-green-50 dark:bg-green-900/10'
            : 'hover:bg-gray-50 dark:hover:bg-gray-800/50'"
        >
          <td class="py-3 pr-4">
            <div class="flex items-center gap-2">
              <span class="font-medium text-gray-900 dark:text-gray-100">{{ variant.name }}</span>
              <span
                v-if="variant.isControl"
                class="rounded bg-gray-200 px-1.5 py-0.5 text-[10px] font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400"
              >
                컨트롤
              </span>
            </div>
          </td>
          <td class="px-3 py-3 text-right text-gray-700 dark:text-gray-300" :class="{ 'font-bold': isMax('views', variant.views) }">
            {{ variant.views.toLocaleString('ko-KR') }}
          </td>
          <td class="px-3 py-3 text-right text-gray-700 dark:text-gray-300" :class="{ 'font-bold': isMax('ctr', variant.ctr) }">
            {{ variant.ctr }}%
          </td>
          <td class="px-3 py-3 text-right text-gray-700 dark:text-gray-300" :class="{ 'font-bold': isMax('avgWatchTime', variant.avgWatchTime) }">
            {{ formatTime(variant.avgWatchTime) }}
          </td>
          <td class="px-3 py-3 text-right text-gray-700 dark:text-gray-300" :class="{ 'font-bold': isMax('engagement', variant.engagement) }">
            {{ variant.engagement }}%
          </td>
          <td class="pl-3 py-3 text-right text-gray-700 dark:text-gray-300" :class="{ 'font-bold': isMax('conversions', variant.conversions) }">
            {{ variant.conversions.toLocaleString('ko-KR') }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
