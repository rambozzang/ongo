<template>
  <div class="card">
    <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">플랫폼별 비교</h3>
    <div class="flex items-center justify-center">
      <div class="h-56 w-56">
        <Doughnut v-if="hasData" ref="chartRef" :data="chartData" :options="chartOptions" :plugins="chartPlugins" />
        <div v-else class="flex h-full items-center justify-center text-sm text-gray-400 dark:text-gray-500">
          데이터가 없습니다
        </div>
      </div>
    </div>
    <div v-if="hasData" class="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-1 md:grid-cols-2">
      <div
        v-for="item in data"
        :key="item.platform"
        class="flex flex-col gap-1 rounded-lg border border-gray-100 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 px-3 py-2"
      >
        <div class="flex items-center gap-2">
          <span
            class="inline-block h-2.5 w-2.5 rounded-full"
            :style="{ backgroundColor: platformColor(item.platform) }"
          />
          <span class="text-xs font-medium text-gray-600 dark:text-gray-400">{{ platformLabel(item.platform) }}</span>
        </div>
        <div class="flex items-baseline justify-between">
          <span class="text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ item.views.toLocaleString('ko-KR') }}
          </span>
          <span class="text-xs text-gray-500 dark:text-gray-500">
            {{ calculatePercentage(item.views) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import type { Chart, Plugin } from 'chart.js'
import type { PlatformComparison } from '@/types/analytics'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { useThemeStore } from '@/stores/theme'

ChartJS.register(ArcElement, Tooltip, Legend)

const themeStore = useThemeStore()
const chartRef = ref<InstanceType<typeof Doughnut> | null>(null)

onBeforeUnmount(() => {
  const instance = chartRef.value as unknown as { chart?: Chart }
  if (instance?.chart) {
    instance.chart.destroy()
  }
})

const props = defineProps<{
  data: PlatformComparison[]
}>()

const hasData = computed(() => props.data.length > 0)

const totalViews = computed(() =>
  props.data.reduce((sum, item) => sum + item.views, 0)
)

function platformColor(platform: Platform): string {
  // Use exact brand colors from the spec
  const colorMap: Partial<Record<Platform, string>> = {
    YOUTUBE: '#FF0000',
    TIKTOK: themeStore.isDark ? '#E5E7EB' : '#000000',
    INSTAGRAM: '#E1306C',
    NAVER_CLIP: '#03C75A',
  }
  return colorMap[platform] || PLATFORM_CONFIG[platform].color
}

function platformLabel(platform: Platform): string {
  return PLATFORM_CONFIG[platform].label
}

function calculatePercentage(views: number): string {
  if (totalViews.value === 0) return '0%'
  return ((views / totalViews.value) * 100).toFixed(1) + '%'
}

// Center text plugin
const centerTextPlugin: Plugin<'doughnut'> = {
  id: 'centerText',
  beforeDraw: (chart: Chart) => {
    const { width, height, ctx } = chart
    ctx.save()

    const total = chart.data.datasets[0]?.data.reduce((a, b) =>
      (typeof a === 'number' ? a : 0) + (typeof b === 'number' ? b : 0), 0
    ) || 0

    // Draw total number
    ctx.font = 'bold 24px Pretendard Variable, sans-serif'
    ctx.fillStyle = themeStore.isDark ? '#f1f5f9' : '#111827'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(
      typeof total === 'number' ? total.toLocaleString('ko-KR') : '0',
      width / 2,
      height / 2 - 8
    )

    // Draw label
    ctx.font = '12px Pretendard Variable, sans-serif'
    ctx.fillStyle = themeStore.isDark ? '#94a3b8' : '#6b7280'
    ctx.fillText('전체', width / 2, height / 2 + 14)

    ctx.restore()
  }
}

const chartPlugins = [centerTextPlugin]

const chartData = computed(() => ({
  labels: props.data.map((d) => platformLabel(d.platform)),
  datasets: [
    {
      data: props.data.map((d) => d.views),
      backgroundColor: props.data.map((d) => platformColor(d.platform)),
      borderWidth: 3,
      borderColor: themeStore.isDark ? '#1f2937' : '#ffffff',
      hoverOffset: 8,
      hoverBorderWidth: 3,
    },
  ],
}))

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: true,
  cutout: '65%',
  animation: {
    animateRotate: true,
    animateScale: true,
    duration: 1000,
    easing: 'easeInOutQuart' as const,
  },
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: themeStore.isDark ? '#1f2937' : 'white',
      titleColor: themeStore.isDark ? '#f3f4f6' : '#111827',
      bodyColor: themeStore.isDark ? '#9ca3af' : '#6B7280',
      borderColor: themeStore.isDark ? '#374151' : '#E5E7EB',
      borderWidth: 1,
      padding: 12,
      displayColors: false,
      callbacks: {
        label: (ctx: { label?: string; parsed: number }) => {
          const percentage = ctx.parsed > 0 && totalViews.value > 0
            ? ((ctx.parsed / totalViews.value) * 100).toFixed(1)
            : '0'
          return ` ${ctx.label}: ${ctx.parsed.toLocaleString('ko-KR')}회 (${percentage}%)`
        },
      },
    },
  },
}))

watch(() => themeStore.isDark, () => {
  const instance = chartRef.value as unknown as { chart?: { update: () => void } }
  instance?.chart?.update()
})
</script>
