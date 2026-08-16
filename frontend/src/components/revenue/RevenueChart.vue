<template>
  <div class="revenue-chart">
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
  Legend,
  Filler,
  type TooltipItem,
  type ChartData,
  type ChartOptions,
} from 'chart.js'
import type { RevenueData } from '@/stores/revenue'
import { PLATFORM_CONFIG, type Platform } from '@/types/channel'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,

  Legend,
  Filler
)

interface Props {
  data: RevenueData[]
  period: string
}

const props = defineProps<Props>()

function platformKeys(data: RevenueData[]): string[] {
  return Array.from(new Set(data.flatMap(item => Object.keys(item.platforms))))
    .filter(platform => platform !== 'NAVER_CLIP')
}

function platformMeta(platform: string) {
  const config = PLATFORM_CONFIG[platform as Platform]
  return config ?? { label: platform, color: '#6B7280' }
}

const chartData = computed<ChartData<'line'>>(() => {
  const labels = props.data.map(item => {
    const [, month] = item.period.split('-')
    return `${month}월`
  })

  const platforms = platformKeys(props.data)

  return {
    labels,
    datasets: platforms.map(platform => {
      const meta = platformMeta(platform)
      return {
        label: meta.label,
        data: props.data.map(item => item.platforms[platform] ?? 0),
        borderColor: meta.color,
        backgroundColor: `${meta.color}20`,
        borderWidth: 2,
        tension: 0.4,
        fill: false,
      }
    }),
  }
})

const chartOptions = computed<ChartOptions<'line'>>(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index' as const,
    intersect: false,
  },
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        padding: 16,
        usePointStyle: true,
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#4B5563',
      },
    },
    tooltip: {
      callbacks: {
        label: (context: TooltipItem<'line'>) => {
          const label = context.dataset.label || ''
          const value = context.parsed.y
          if (value == null) return label
          return `${label}: ₩${value.toLocaleString('ko-KR')}`
        },
      },
    },
  },
  scales: {
    x: {
      grid: {
        color: document.documentElement.classList.contains('dark') ? '#374151' : '#E5E7EB',
      },
      ticks: {
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#6B7280',
      },
    },
    y: {
      beginAtZero: true,
      grid: {
        color: document.documentElement.classList.contains('dark') ? '#374151' : '#E5E7EB',
      },
      ticks: {
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#6B7280',
        callback: (value: string | number) => `₩${(Number(value) / 1000).toFixed(0)}K`,
      },
    },
  },
}))
</script>

<style scoped>
.revenue-chart {
  width: 100%;
  height: 100%;
  min-height: 300px;
}
</style>
