<template>
  <div
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$router.push('/channels')"
  >
    <div class="flex items-center justify-between">
      <p class="text-sm font-medium text-gray-500 dark:text-gray-400">구독자 증가</p>
      <UsersIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
    </div>
    <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
      {{ totalSubscribers.toLocaleString() }}
    </p>
    <div v-if="change !== undefined" class="mt-1 flex items-center gap-1 text-sm">
      <span :class="changeColor">
        {{ changeIcon }}{{ Math.abs(change) }}
      </span>
      <span class="text-gray-400 dark:text-gray-500">전주 대비</span>
    </div>
    <div class="mt-3 h-12">
      <Line :data="chartData" :options="chartOptions" />
    </div>
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
  Filler,
} from 'chart.js'
import { UsersIcon } from '@heroicons/vue/24/outline'
import type { TrendDataPoint } from '@/types/analytics'
import { useThemeStore } from '@/stores/theme'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Filler)

const themeStore = useThemeStore()

const props = defineProps<{
  totalSubscribers: number
  change?: number
  trendData: TrendDataPoint[]
}>()

const changeIcon = computed(() => {
  if (props.change === undefined) return ''
  return props.change >= 0 ? '+' : ''
})

const changeColor = computed(() => {
  if (props.change === undefined) return ''
  return props.change >= 0 ? 'text-green-600' : 'text-red-600'
})

const chartData = computed(() => {
  // Use last 7 days for sparkline
  const data = props.trendData.slice(-7)

  return {
    labels: data.map(() => ''),
    datasets: [
      {
        data: data.map((d) => d.totalViews),
        borderColor: '#8b5cf6',
        backgroundColor: themeStore.isDark
          ? 'rgba(139, 92, 246, 0.1)'
          : 'rgba(139, 92, 246, 0.2)',
        tension: 0.4,
        pointRadius: 0,
        borderWidth: 2,
        fill: true,
      },
    ],
  }
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: { enabled: false },
  },
  scales: {
    x: {
      display: false,
    },
    y: {
      display: false,
    },
  },
  elements: {
    line: {
      borderJoinStyle: 'round' as const,
    },
  },
}))
</script>
