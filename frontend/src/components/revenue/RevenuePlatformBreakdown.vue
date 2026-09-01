<template>
  <!--
    수익 데이터가 없으면 **도넛을 그리지 않는다.**

    예전에는 `data = []` 여도 빈 링과 가운데 **"₩0"** 을 그렸다. 그 화면은 "측정했더니
    0원"으로 읽힌다 — 아직 아무것도 수집되지 않은 상태와 구분되지 않는다. 게다가 합계가
    0 이면 범례·툴팁의 퍼센트 계산이 `0/0` 이라 `NaN%` 가 된다.
  -->
  <div v-if="hasRevenue" class="relative">
    <Doughnut :data="chartData" :options="chartOptions" />
    <div
      class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-center"
    >
      <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('revenue.platformBreakdown.totalRevenue') }}</div>
      <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
        {{ formatCurrency(totalRevenue) }}
      </div>
    </div>
  </div>
  <p
    v-else
    data-testid="platform-breakdown-empty"
    class="flex min-h-[220px] items-center justify-center rounded-lg border border-dashed border-gray-300 p-4 text-center text-body text-muted-strong dark:border-gray-600"
  >
    {{ $t('revenue.noRevenueData') }}
  </p>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend, type TooltipItem } from 'chart.js'
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

/**
 * 도넛을 그릴 수 있는가.
 *
 * 행이 없거나 합계가 0 이면 그릴 조각이 없다. 그때 차트를 그리면 빈 링과 "₩0" 만 남아
 * **측정된 0원처럼** 보이고, 퍼센트는 `0/0` 이라 `NaN%` 가 된다.
 */
const hasRevenue = computed(() => props.data.length > 0 && totalRevenue.value > 0)

const chartData = computed(() => {
  const platformColors = {
    YOUTUBE: PLATFORM_CONFIG.YOUTUBE.color,
    TIKTOK: '#000000',
    INSTAGRAM: PLATFORM_CONFIG.INSTAGRAM.color,
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
        generateLabels: (chart: ChartJS<'doughnut'>) => {
          const data = chart.data
          if (data.labels && data.labels.length && data.datasets.length) {
            return data.labels.map((label: unknown, i: number) => {
              const dataset = data.datasets[0]
              const bgColors = dataset.backgroundColor as string[] | undefined
              const value = dataset.data[i] as number
              const total = dataset.data.reduce((a: number, b: number) => a + b, 0)
              const percentage = ((value / total) * 100).toFixed(1)
              return {
                text: `${String(label)} (${percentage}%)`,
                fillStyle: bgColors ? bgColors[i] : '#000',
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
        label: (context: TooltipItem<'doughnut'>) => {
          const label = context.label || ''
          const value = context.parsed
          const total = (context.dataset.data as number[]).reduce((a: number, b: number) => a + b, 0)
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
