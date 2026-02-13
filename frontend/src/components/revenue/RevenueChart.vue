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
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'
import type { RevenueData } from '@/stores/revenue'
import { PLATFORM_CONFIG } from '@/types/channel'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
)

interface Props {
  data: RevenueData[]
  period: string
}

const props = defineProps<Props>()

const chartData = computed(() => {
  const labels = props.data.map(item => {
    const [, month] = item.period.split('-')
    return `${month}월`
  })

  return {
    labels,
    datasets: [
      {
        label: 'YouTube',
        data: props.data.map(item => item.youtube),
        borderColor: PLATFORM_CONFIG.YOUTUBE.color,
        backgroundColor: `${PLATFORM_CONFIG.YOUTUBE.color}20`,
        borderWidth: 2,
        tension: 0.4,
        fill: false,
      },
      {
        label: 'TikTok',
        data: props.data.map(item => item.tiktok),
        borderColor: '#000000',
        backgroundColor: '#00000020',
        borderWidth: 2,
        tension: 0.4,
        fill: false,
      },
      {
        label: 'Instagram',
        data: props.data.map(item => item.instagram),
        borderColor: PLATFORM_CONFIG.INSTAGRAM.color,
        backgroundColor: `${PLATFORM_CONFIG.INSTAGRAM.color}20`,
        borderWidth: 2,
        tension: 0.4,
        fill: false,
      },
      {
        label: 'Naver Clip',
        data: props.data.map(item => item.naverClip),
        borderColor: PLATFORM_CONFIG.NAVER_CLIP.color,
        backgroundColor: `${PLATFORM_CONFIG.NAVER_CLIP.color}20`,
        borderWidth: 2,
        tension: 0.4,
        fill: false,
      },
    ],
  }
})

const chartOptions = computed(() => ({
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
        label: (context: any) => {
          const label = context.dataset.label || ''
          const value = context.parsed.y
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
        callback: (value: any) => `₩${(value / 1000).toFixed(0)}K`,
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
