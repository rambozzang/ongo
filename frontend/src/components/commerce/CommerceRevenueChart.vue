<template>
  <div class="commerce-revenue-chart">
    <Line :data="chartData" :options="chartOptions" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
  type ChartData,
  type ChartOptions,
  type TooltipItem,
} from 'chart.js'
import type { CommerceRevenueTrend } from '@/types/commerce'
import { useI18n } from 'vue-i18n'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const { t } = useI18n()

const props = defineProps<{
  data: CommerceRevenueTrend[]
}>()

const chartData = computed<ChartData<'line'>>(() => {
  const labels = props.data.map(item => {
    const parts = item.date.split('-')
    return `${parts[1]}/${parts[2]}`
  })

  return {
    labels,
    datasets: [
      {
        label: t('commerce.revenue'),
        data: props.data.map(item => item.revenue),
        borderColor: '#6366F1',
        backgroundColor: 'rgba(99, 102, 241, 0.1)',
        borderWidth: 2,
        tension: 0.4,
        fill: true,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
      {
        label: t('commerce.clicks'),
        data: props.data.map(item => item.clicks),
        borderColor: '#F59E0B',
        backgroundColor: 'rgba(245, 158, 11, 0.1)',
        borderWidth: 2,
        tension: 0.4,
        fill: false,
        pointRadius: 0,
        pointHoverRadius: 4,
        yAxisID: 'y1',
      },
    ],
  }
})

const chartOptions = computed<ChartOptions<'line'>>(() => {
  const isDark = document.documentElement.classList.contains('dark')
  const gridColor = isDark ? '#374151' : '#E5E7EB'
  const tickColor = isDark ? '#9CA3AF' : '#6B7280'

  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: 'index',
      intersect: false,
    },
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          padding: 16,
          usePointStyle: true,
          color: tickColor,
        },
      },
      tooltip: {
        callbacks: {
          label: (context: TooltipItem<'line'>) => {
            const label = context.dataset.label || ''
            const value = context.parsed.y
            if (value == null) return label
            if (context.datasetIndex === 0) {
              return `${label}: ₩${value.toLocaleString('ko-KR')}`
            }
            return `${label}: ${value.toLocaleString()}`
          },
        },
      },
    },
    scales: {
      x: {
        grid: { color: gridColor },
        ticks: { color: tickColor, maxTicksLimit: 10 },
      },
      y: {
        beginAtZero: true,
        position: 'left',
        grid: { color: gridColor },
        ticks: {
          color: tickColor,
          callback: (value: string | number) => `₩${(Number(value) / 10000).toFixed(0)}만`,
        },
      },
      y1: {
        beginAtZero: true,
        position: 'right',
        grid: { display: false },
        ticks: {
          color: tickColor,
          callback: (value: string | number) => `${Number(value)}`,
        },
      },
    },
  }
})
</script>

<style scoped>
.commerce-revenue-chart {
  width: 100%;
  height: 100%;
  min-height: 300px;
}
</style>
