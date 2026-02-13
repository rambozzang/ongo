<template>
  <div class="relative">
    <Doughnut :data="chartData" :options="chartOptions" />
    <div
      class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-center"
    >
      <div class="text-xs text-gray-500 dark:text-gray-400">총 수익</div>
      <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
        {{ formatCurrency(totalRevenue) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import { PLATFORM_CONFIG } from '@/types/channel'

ChartJS.register(ArcElement, Tooltip, Legend)

interface PlatformBreakdown {
  platform: string
  revenue: number
  percentage: number
}

interface Props {
  data: PlatformBreakdown[]
}

const props = defineProps<Props>()

const totalRevenue = computed(() =>
  props.data.reduce((sum, item) => sum + item.revenue, 0)
)

const chartData = computed(() => {
  const platformColors = {
    YOUTUBE: PLATFORM_CONFIG.YOUTUBE.color,
    TIKTOK: '#000000',
    INSTAGRAM: PLATFORM_CONFIG.INSTAGRAM.color,
    NAVER_CLIP: PLATFORM_CONFIG.NAVER_CLIP.color,
  }

  return {
    labels: props.data.map(item => {
      const platform = item.platform as keyof typeof PLATFORM_CONFIG
      return PLATFORM_CONFIG[platform]?.label || item.platform
    }),
    datasets: [
      {
        data: props.data.map(item => item.revenue),
        backgroundColor: props.data.map(item => {
          const platform = item.platform as keyof typeof platformColors
          return platformColors[platform] || '#6B7280'
        }),
        borderWidth: 0,
      },
    ],
  }
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: true,
  cutout: '70%',
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        padding: 16,
        usePointStyle: true,
        generateLabels: (chart: any) => {
          const data = chart.data
          if (data.labels.length && data.datasets.length) {
            return data.labels.map((label: string, i: number) => {
              const value = data.datasets[0].data[i]
              const total = data.datasets[0].data.reduce((a: number, b: number) => a + b, 0)
              const percentage = ((value / total) * 100).toFixed(1)
              return {
                text: `${label} (${percentage}%)`,
                fillStyle: data.datasets[0].backgroundColor[i],
                hidden: false,
                index: i,
              }
            })
          }
          return []
        },
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#4B5563',
      },
    },
    tooltip: {
      callbacks: {
        label: (context: any) => {
          const label = context.label || ''
          const value = context.parsed
          const total = context.dataset.data.reduce((a: number, b: number) => a + b, 0)
          const percentage = ((value / total) * 100).toFixed(1)
          return `${label}: ₩${value.toLocaleString('ko-KR')} (${percentage}%)`
        },
      },
    },
  },
}))

function formatCurrency(value: number): string {
  if (value >= 100000000) {
    return `₩${(value / 100000000).toFixed(1)}억`
  } else if (value >= 10000) {
    return `₩${(value / 10000).toFixed(0)}만`
  }
  return `₩${value.toLocaleString('ko-KR')}`
}
</script>
