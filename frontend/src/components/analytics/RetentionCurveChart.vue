<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
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
  type ChartDataset,
} from 'chart.js'
import {
  ArrowPathIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { analyticsApi } from '@/api/analytics'
import type { RetentionCurveResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const props = defineProps<{
  videoId?: number
}>()

const loading = ref(false)
const retentionData = ref<RetentionCurveResponse | null>(null)
const selectedVideoId = ref<number | undefined>(props.videoId)

watch(
  () => props.videoId,
  (newVal) => {
    selectedVideoId.value = newVal
    if (newVal) fetchRetention(newVal)
  },
)

async function fetchRetention(videoId: number) {
  loading.value = true
  try {
    retentionData.value = await analyticsApi.retentionCurve(videoId)
  } catch {
    retentionData.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (selectedVideoId.value) {
    fetchRetention(selectedVideoId.value)
  }
})

function formatTimestamp(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const chartData = computed(() => {
  if (!retentionData.value) return { labels: [], datasets: [] }

  const labels = retentionData.value.retentionPoints.map((p) => formatTimestamp(p.timestamp))

  // Create gradient-like colors for the area fill
  const datasets = [
    {
      label: '이 영상',
      data: retentionData.value.retentionPoints.map((p) => p.retentionRate),
      borderColor: '#6366F1',
      backgroundColor: (ctx: { chart: { ctx: CanvasRenderingContext2D; chartArea: { top: number; bottom: number } } }) => {
        const chart = ctx.chart
        if (!chart.chartArea) return 'rgba(99, 102, 241, 0.1)'
        const gradient = chart.ctx.createLinearGradient(0, chart.chartArea.top, 0, chart.chartArea.bottom)
        gradient.addColorStop(0, 'rgba(16, 185, 129, 0.3)') // green at top
        gradient.addColorStop(0.5, 'rgba(245, 158, 11, 0.2)') // amber in middle
        gradient.addColorStop(1, 'rgba(239, 68, 68, 0.1)') // red at bottom
        return gradient
      },
      fill: true,
      tension: 0.4,
      pointRadius: 3,
      pointHoverRadius: 6,
      borderWidth: 2.5,
    },
    {
      label: '채널 평균',
      data: retentionData.value.avgRetention.map((p) => p.retentionRate),
      borderColor: '#9CA3AF',
      backgroundColor: 'transparent',
      borderDash: [6, 3],
      fill: false,
      tension: 0.4,
      pointRadius: 0,
      pointHoverRadius: 4,
      borderWidth: 1.5,
    },
  ]

  // Add drop-off point markers
  if (retentionData.value.dropOffPoints.length > 0) {
    const dropOffData = new Array(labels.length).fill(null)
    for (const drop of retentionData.value.dropOffPoints) {
      const idx = retentionData.value.retentionPoints.findIndex(
        (p) => p.timestamp === drop.timestamp,
      )
      if (idx >= 0) {
        dropOffData[idx] = retentionData.value.retentionPoints[idx].retentionRate
      }
    }

    datasets.push({
      label: '이탈 구간',
      data: dropOffData,
      borderColor: '#EF4444',
      backgroundColor: '#EF4444',
      borderDash: [],
      fill: false,
      tension: 0,
      pointRadius: 6,
      pointHoverRadius: 8,
      borderWidth: 0,
    } as ChartDataset<'line'>)
  }

  return { labels, datasets }
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
        usePointStyle: true,
        padding: 16,
        font: { size: 11 },
      },
    },
    tooltip: {
      callbacks: {
        label: (ctx: { dataset: { label?: string }; parsed: { y: number | null } }) => {
          if (ctx.parsed.y == null) return ''
          return `${ctx.dataset.label}: ${ctx.parsed.y.toFixed(1)}%`
        },
      },
    },
  },
  scales: {
    y: {
      beginAtZero: true,
      max: 105,
      title: {
        display: true,
        text: '시청자 리텐션 (%)',
        font: { size: 12 },
      },
      ticks: {
        callback: (value: number | string) => `${value}%`,
      },
    },
    x: {
      title: {
        display: true,
        text: '영상 재생 시간',
        font: { size: 12 },
      },
    },
  },
}))
</script>

<template>
  <div>
    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-16">
      <ArrowPathIcon class="h-6 w-6 animate-spin text-gray-400" />
    </div>

    <!-- No video selected -->
    <div v-else-if="!selectedVideoId" class="py-16 text-center">
      <p class="text-sm text-gray-400 dark:text-gray-500">
        리텐션 분석할 영상을 선택해주세요.
      </p>
    </div>

    <!-- Chart + Drop-off Analysis -->
    <template v-else-if="retentionData && retentionData.retentionPoints.length > 0">
      <div class="relative h-72 w-full">
        <Line :data="chartData" :options="chartOptions" />
      </div>

      <!-- Drop-off Points -->
      <div
        v-if="retentionData.dropOffPoints.length > 0"
        class="mt-4 space-y-2"
      >
        <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
          주요 이탈 구간
        </h4>
        <div
          v-for="drop in retentionData.dropOffPoints"
          :key="drop.timestamp"
          class="flex items-start gap-3 rounded-lg border border-red-100 bg-red-50/50 p-3 dark:border-red-900/30 dark:bg-red-900/10"
        >
          <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-red-500" />
          <div class="text-sm">
            <span class="font-medium text-red-700 dark:text-red-400">
              {{ formatTimestamp(drop.timestamp) }}
            </span>
            <span class="text-gray-600 dark:text-gray-400">
              &mdash; {{ drop.dropRate.toFixed(1) }}% 이탈
            </span>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
              {{ drop.possibleReason }}
            </p>
          </div>
        </div>
      </div>
    </template>

    <!-- Empty State -->
    <div v-else class="py-16 text-center">
      <p class="text-sm text-gray-400 dark:text-gray-500">
        리텐션 데이터가 없습니다.
      </p>
    </div>
  </div>
</template>
