<script setup lang="ts">
import { computed } from 'vue'
import type { FanDemographic } from '@/types/fanInsights'

interface Props {
  demographics: FanDemographic[]
}

const props = defineProps<Props>()

// 연령대별 데이터 집계
const ageGroupData = computed(() => {
  const ageMap = new Map<string, number>()
  props.demographics.forEach((d) => {
    ageMap.set(d.ageGroup, (ageMap.get(d.ageGroup) || 0) + d.percentage)
  })
  const entries = Array.from(ageMap.entries()).sort((a, b) => {
    const order = ['13-17', '18-24', '25-34', '35-44', '45-54', '55+']
    return order.indexOf(a[0]) - order.indexOf(b[0])
  })
  const maxVal = Math.max(...entries.map(([, v]) => v), 1)
  return entries.map(([group, value]) => ({
    group,
    value: Math.round(value * 10) / 10,
    widthPercent: (value / maxVal) * 100,
  }))
})

// 성별 분포 집계
const genderData = computed(() => {
  const genderMap = new Map<string, number>()
  props.demographics.forEach((d) => {
    genderMap.set(d.gender, (genderMap.get(d.gender) || 0) + d.percentage)
  })
  const total = Array.from(genderMap.values()).reduce((a, b) => a + b, 0) || 1
  const genderLabels: Record<string, string> = {
    MALE: '남성',
    FEMALE: '여성',
    OTHER: '기타',
  }
  const genderColors: Record<string, string> = {
    MALE: 'bg-blue-500 dark:bg-blue-400',
    FEMALE: 'bg-pink-500 dark:bg-pink-400',
    OTHER: 'bg-gray-500 dark:bg-gray-400',
  }
  const genderDotColors: Record<string, string> = {
    MALE: 'bg-blue-500',
    FEMALE: 'bg-pink-500',
    OTHER: 'bg-gray-500',
  }
  return Array.from(genderMap.entries()).map(([gender, value]) => ({
    gender,
    label: genderLabels[gender] || gender,
    value: Math.round(value * 10) / 10,
    percent: Math.round((value / total) * 100),
    barColor: genderColors[gender] || 'bg-gray-500 dark:bg-gray-400',
    dotColor: genderDotColors[gender] || 'bg-gray-500',
  }))
})

const barColors = [
  'bg-blue-500 dark:bg-blue-400',
  'bg-indigo-500 dark:bg-indigo-400',
  'bg-purple-500 dark:bg-purple-400',
  'bg-violet-500 dark:bg-violet-400',
  'bg-fuchsia-500 dark:bg-fuchsia-400',
  'bg-pink-500 dark:bg-pink-400',
]
</script>

<template>
  <div class="space-y-6">
    <!-- Age Group Bar Chart -->
    <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        연령대별 분포
      </h3>
      <div v-if="ageGroupData.length > 0" class="space-y-3">
        <div
          v-for="(item, index) in ageGroupData"
          :key="item.group"
          class="flex items-center gap-3"
        >
          <span class="w-12 flex-shrink-0 text-xs font-medium text-gray-600 dark:text-gray-400 text-right">
            {{ item.group }}
          </span>
          <div class="flex-1 h-6 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
            <div
              :class="['h-full rounded-full transition-all duration-700 flex items-center justify-end pr-2', barColors[index % barColors.length]]"
              :style="{ width: `${Math.max(item.widthPercent, 8)}%` }"
            >
              <span
                v-if="item.widthPercent > 20"
                class="text-xs font-semibold text-white"
              >
                {{ item.value }}%
              </span>
            </div>
          </div>
          <span
            v-if="item.widthPercent <= 20"
            class="w-12 text-xs font-semibold text-gray-700 dark:text-gray-300"
          >
            {{ item.value }}%
          </span>
        </div>
      </div>
      <div v-else class="py-6 text-center">
        <p class="text-sm text-gray-500 dark:text-gray-400">연령대 데이터가 없습니다</p>
      </div>
    </div>

    <!-- Gender Distribution -->
    <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
      <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
        성별 분포
      </h3>
      <div v-if="genderData.length > 0">
        <!-- Stacked Bar -->
        <div class="mb-4 flex h-8 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
          <div
            v-for="item in genderData"
            :key="item.gender"
            :class="['h-full flex items-center justify-center transition-all duration-700', item.barColor]"
            :style="{ width: `${item.percent}%` }"
          >
            <span v-if="item.percent > 15" class="text-xs font-semibold text-white">
              {{ item.percent }}%
            </span>
          </div>
        </div>

        <!-- Legend -->
        <div class="flex flex-wrap justify-center gap-4">
          <div
            v-for="item in genderData"
            :key="item.gender"
            class="flex items-center gap-2"
          >
            <span :class="['inline-block h-3 w-3 rounded-full', item.dotColor]" />
            <span class="text-sm text-gray-700 dark:text-gray-300">
              {{ item.label }} {{ item.percent }}%
            </span>
          </div>
        </div>
      </div>
      <div v-else class="py-6 text-center">
        <p class="text-sm text-gray-500 dark:text-gray-400">성별 데이터가 없습니다</p>
      </div>
    </div>
  </div>
</template>
