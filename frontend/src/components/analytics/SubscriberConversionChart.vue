<script setup lang="ts">
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, LineController, BarController } from 'chart.js'
import type { ChartData } from 'chart.js'
import type { SubscriberConversionResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, LineController, BarController)

const props = defineProps<{ data: SubscriberConversionResponse | null }>()

/**
 * 측정된 포인트가 하나도 없으면 차트를 만들지 않는다.
 *
 * Chart.js 에 빈 배열을 넘기면 축만 있는 빈 그래프가 그려져 "데이터는 있는데 값이 0"
 * 처럼 보인다. 가짜 0 포인트는 더 나쁘다 — 그날 전환율이 0 이었다는 관측이 된다.
 */
const hasMeasuredPoints = computed(() => (props.data?.data.length ?? 0) > 0)

const chartData = computed(() => {
  if (!props.data || !hasMeasuredPoints.value) return null
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
      <h3 class="text-title font-semibold text-gray-900 dark:text-white">구독 전환 분석</h3>
      <!--
        측정되지 않았으면 숫자를 만들지 않는다. 초록색 "+0" 은 재지 않았는데 성과가
        0 이었다는 주장이 된다.
      -->
      <span v-if="props.data" class="text-body text-gray-500 dark:text-gray-400">
        총 신규 구독:
        <strong v-if="props.data.totalGained != null" data-testid="subscriber-total" class="text-success-strong">+{{ props.data.totalGained.toLocaleString() }}</strong>
        <strong v-else data-testid="subscriber-total-unavailable" class="text-gray-500 dark:text-gray-400">측정 불가</strong>
      </span>
    </div>
    <Bar v-if="chartData" :data="chartData" :options="chartOptions" />
    <p v-else data-testid="subscriber-empty" class="text-center text-gray-400 py-8">
      {{ props.data?.unavailableReason ?? '구독 증가 수가 수집되지 않아 전환율을 계산할 수 없습니다' }}
    </p>
  </div>
</template>
