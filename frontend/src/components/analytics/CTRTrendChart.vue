<script setup lang="ts">
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, Filler } from 'chart.js'
import type { CTRResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, Filler)

const props = defineProps<{ data: CTRResponse | null }>()

const chartData = computed(() => {
  if (!props.data) return null
  return {
    labels: props.data.data.map(d => d.date.slice(5)),
    datasets: [
      {
        label: 'CTR (%)',
        data: props.data.data.map(d => d.ctr),
        borderColor: '#8b5cf6',
        backgroundColor: 'rgba(139, 92, 246, 0.1)',
        fill: true,
        yAxisID: 'y',
      },
      {
        label: '노출수',
        data: props.data.data.map(d => d.impressions),
        borderColor: '#94a3b8',
        borderDash: [5, 5],
        yAxisID: 'y1',
      },
    ],
  }
})

const chartOptions = {
  responsive: true,
  interaction: { mode: 'index' as const, intersect: false },
  scales: {
    y: { type: 'linear' as const, position: 'left' as const, title: { display: true, text: 'CTR (%)' } },
    y1: { type: 'linear' as const, position: 'right' as const, title: { display: true, text: '노출수' }, grid: { drawOnChartArea: false } },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white">CTR 트렌드</h3>
      <div v-if="props.data" class="flex items-center gap-4 text-sm">
        <span class="text-gray-500 dark:text-gray-400">평균 CTR: <strong class="text-purple-600">{{ props.data.avgCTR }}%</strong></span>
        <span class="text-gray-500 dark:text-gray-400">총 노출: <strong>{{ props.data.totalImpressions.toLocaleString() }}</strong></span>
      </div>
    </div>
    <Line v-if="chartData" :data="chartData" :options="chartOptions" />
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
