<script setup lang="ts">
import { computed } from 'vue'
import { Bar, Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend } from 'chart.js'
import type { DemographicsResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend)

const props = defineProps<{ data: DemographicsResponse | null }>()

const GENDER_LABELS: Record<string, string> = { male: '남성', female: '여성', other: '기타' }
const GENDER_COLORS = ['#3b82f6', '#ec4899', '#94a3b8']

const ageChartData = computed(() => {
  if (!props.data) return null
  const entries = Object.entries(props.data.ageDistribution).sort(([a], [b]) => a.localeCompare(b))
  return {
    labels: entries.map(([k]) => k),
    datasets: [{ label: '비율 (%)', data: entries.map(([, v]) => v), backgroundColor: '#8b5cf6' }],
  }
})

const genderChartData = computed(() => {
  if (!props.data) return null
  const entries = Object.entries(props.data.genderDistribution)
  return {
    labels: entries.map(([k]) => GENDER_LABELS[k] || k),
    datasets: [{ data: entries.map(([, v]) => v), backgroundColor: GENDER_COLORS.slice(0, entries.length) }],
  }
})

const topCountries = computed(() => {
  if (!props.data) return []
  return Object.entries(props.data.topCountries).sort(([, a], [, b]) => b - a).slice(0, 5)
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">시청자 인구통계</h3>
    <div v-if="props.data" class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">연령대</h4>
        <Bar v-if="ageChartData" :data="ageChartData" :options="{ responsive: true, plugins: { legend: { display: false } } }" />
      </div>
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">성별</h4>
        <Doughnut v-if="genderChartData" :data="genderChartData" :options="{ responsive: true }" />
      </div>
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">상위 국가</h4>
        <ul class="space-y-2">
          <li v-for="[country, count] in topCountries" :key="country" class="flex justify-between text-sm">
            <span class="text-gray-700 dark:text-gray-300">{{ country }}</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ count.toLocaleString() }}</span>
          </li>
        </ul>
        <p v-if="topCountries.length === 0" class="text-gray-400 text-sm">데이터 없음</p>
      </div>
    </div>
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
