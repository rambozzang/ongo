<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import type { TrafficSourceResponse } from '@/types/analytics'

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps<{ data: TrafficSourceResponse | null }>()

const TRAFFIC_LABELS: Record<string, string> = {
  SEARCH: '검색',
  SUGGESTED: '추천 영상',
  EXTERNAL: '외부 링크',
  BROWSE: '탐색',
  CHANNEL: '채널 페이지',
  NOTIFICATION: '알림',
  OTHER: '기타',
}

const COLORS = ['#8b5cf6', '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#6366f1', '#94a3b8']

const chartData = computed(() => {
  if (!props.data) return null
  const entries = Object.entries(props.data.sources)
  return {
    labels: entries.map(([k]) => TRAFFIC_LABELS[k] || k),
    datasets: [{
      data: entries.map(([, v]) => v),
      backgroundColor: COLORS.slice(0, entries.length),
    }],
  }
})

const chartOptions = {
  responsive: true,
  plugins: {
    legend: { position: 'right' as const },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">트래픽 소스</h3>
    <div v-if="chartData" class="max-w-md mx-auto">
      <Doughnut :data="chartData" :options="chartOptions" />
    </div>
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
