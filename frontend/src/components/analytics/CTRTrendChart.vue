<script setup lang="ts">
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, Filler } from 'chart.js'
import type { CTRResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, Filler)

const props = defineProps<{ data: CTRResponse | null }>()

/**
 * 측정된 포인트가 하나도 없으면 차트를 만들지 않는다.
 *
 * Chart.js 에 빈 배열을 넘기면 축만 있는 빈 그래프가 그려져 "데이터는 있는데 값이 0"
 * 처럼 보인다. 가짜 0 포인트는 더 나쁘다 — 그날 클릭률이 0 이었다는 관측이 된다.
 */
const hasMeasuredPoints = computed(() => (props.data?.data.length ?? 0) > 0)

const chartData = computed(() => {
  if (!props.data || !hasMeasuredPoints.value) return null
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
      <h3 class="text-title font-semibold text-gray-900 dark:text-white">CTR 트렌드</h3>
      <div v-if="props.data" class="flex items-center gap-4 text-body">
        <!--
          측정되지 않았으면 숫자를 만들지 않는다. "평균 CTR 0% · 총 노출 0" 은 재지
          않았는데 성과가 0 이었다는 주장이 된다.
        -->
        <span class="text-gray-500 dark:text-gray-400">
          평균 CTR:
          <strong v-if="props.data.avgCTR != null" data-testid="ctr-avg" class="text-purple-600">{{ props.data.avgCTR }}%</strong>
          <strong v-else data-testid="ctr-avg-unavailable" class="text-gray-500 dark:text-gray-400">측정 불가</strong>
        </span>
        <span class="text-gray-500 dark:text-gray-400">
          총 노출:
          <strong v-if="props.data.totalImpressions != null" data-testid="ctr-impressions">{{ props.data.totalImpressions.toLocaleString() }}</strong>
          <strong v-else data-testid="ctr-impressions-unavailable" class="text-gray-500 dark:text-gray-400">측정 불가</strong>
        </span>
      </div>
    </div>
    <Line v-if="chartData" :data="chartData" :options="chartOptions" />
    <p v-else data-testid="ctr-empty" class="text-center text-gray-400 py-8">
      {{ props.data?.unavailableReason ?? '노출 수가 수집되지 않아 클릭률을 계산할 수 없습니다' }}
    </p>
  </div>
</template>
