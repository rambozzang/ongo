<script setup lang="ts">
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, LineController, BarController } from 'chart.js'
import type { ChartData } from 'chart.js'
import type { SubscriberConversionResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, LineController, BarController)

const props = defineProps<{ data: SubscriberConversionResponse | null }>()

const chartData = computed(() => {
  if (!props.data) return null
  return {
    labels: props.data.data.map(d => d.date.slice(5)),
    datasets: [
      {
        type: 'bar' as const,
        label: '신규 구독자',
        data: props.data.data.map(d => d.gained),
        backgroundColor: '#10b981',
        yAxisID: 'y',
      },
      {
        type: 'line' as const,
        label: '전환율 (%)',
        data: props.data.data.map(d => d.conversionRate),
        borderColor: '#f59e0b',
        yAxisID: 'y1',
      },
    ],
  } as ChartData<'bar'>
})

const chartOptions = {
  responsive: true,
  scales: {
    y: { type: 'linear' as const, position: 'left' as const, title: { display: true, text: '구독자' } },
    y1: { type: 'linear' as const, position: 'right' as const, title: { display: true, text: '전환율 (%)' }, grid: { drawOnChartArea: false } },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white">구독 전환 분석</h3>
      <span v-if="props.data" class="text-sm text-gray-500 dark:text-gray-400">
        총 신규 구독: <strong class="text-green-600">+{{ props.data.totalGained.toLocaleString() }}</strong>
      </span>
    </div>
    <Bar v-if="chartData" :data="chartData" :options="chartOptions" />
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
