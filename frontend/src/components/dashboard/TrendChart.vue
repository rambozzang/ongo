<template>
  <div class="card">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">조회수 트렌드</h3>
      <div class="flex rounded-lg border border-gray-200 dark:border-gray-700">
        <button
          class="px-3 py-1 text-sm transition-colors"
          :class="period === '7d' ? 'bg-primary-500 text-white' : 'text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'"
          @click="$emit('update:period', '7d')"
        >
          7일
        </button>
        <button
          class="px-3 py-1 text-sm transition-colors"
          :class="period === '30d' ? 'bg-primary-500 text-white' : 'text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'"
          @click="$emit('update:period', '30d')"
        >
          30일
        </button>
      </div>
    </div>
    <div class="relative">
      <Line ref="chartRef" :data="chartData" :options="chartOptions" :plugins="chartPlugins" />
      <div id="chartjs-tooltip" style="position: absolute; pointer-events: none; opacity: 0;"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
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
import type { Chart, Plugin, TooltipModel } from 'chart.js'
import type { TrendDataPoint } from '@/types/analytics'
import { useThemeStore } from '@/stores/theme'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const themeStore = useThemeStore()
const chartRef = ref<InstanceType<typeof Line> | null>(null)

onBeforeUnmount(() => {
  const instance = chartRef.value as unknown as { chart?: Chart }
  if (instance?.chart) {
    instance.chart.destroy()
  }
})

const props = defineProps<{
  data: TrendDataPoint[]
  period: '7d' | '30d'
}>()

defineEmits<{
  'update:period': [value: '7d' | '30d']
}>()

// Helper function to format large numbers
function formatCompactNumber(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}만`
  } else if (value >= 1000) {
    return `${(value / 1000).toFixed(1)}K`
  }
  return value.toString()
}

// External tooltip handler
function getOrCreateTooltip(_chart: unknown) {
  let tooltipEl = document.getElementById('chartjs-tooltip')

  if (!tooltipEl) {
    tooltipEl = document.createElement('div')
    tooltipEl.id = 'chartjs-tooltip'
    tooltipEl.style.position = 'absolute'
    tooltipEl.style.pointerEvents = 'none'
    tooltipEl.style.opacity = '0'
    tooltipEl.style.transition = 'all 0.2s ease'
    document.body.appendChild(tooltipEl)
  }

  return tooltipEl
}

const externalTooltipHandler = (context: { chart: Chart; tooltip: TooltipModel<'line'> }) => {
  const { chart, tooltip } = context
  const tooltipEl = getOrCreateTooltip(chart)

  if (tooltip.opacity === 0) {
    tooltipEl.style.opacity = '0'
    return
  }

  if (tooltip.body) {
    const titleLines = tooltip.title || []
    let innerHtml = '<div class="rounded-lg shadow-lg border ' +
      (themeStore.isDark ? 'bg-gray-800 border-gray-700' : 'bg-white border-gray-200') +
      ' px-3 py-2">'

    titleLines.forEach((title) => {
      innerHtml += '<div class="font-semibold text-sm mb-1 ' +
        (themeStore.isDark ? 'text-gray-100' : 'text-gray-900') +
        '">' + title + '</div>'
    })

    tooltip.dataPoints.forEach((dataPoint, i) => {
      const colors = tooltip.labelColors[i]
      const style = 'background:' + colors.backgroundColor + ';border-color:' + colors.borderColor
      const span = '<span class="inline-block w-2 h-2 rounded-full mr-2" style="' + style + '"></span>'
      const value = (dataPoint.raw as number).toLocaleString('ko-KR')
      innerHtml += '<div class="flex items-center justify-between gap-4 text-xs ' +
        (themeStore.isDark ? 'text-gray-300' : 'text-gray-600') +
        '">'
      innerHtml += '<span>' + span + dataPoint.dataset.label + '</span>'
      innerHtml += '<span class="font-medium">' + value + '</span>'
      innerHtml += '</div>'
    })

    innerHtml += '</div>'
    tooltipEl.innerHTML = innerHtml
  }

  const { offsetLeft: positionX, offsetTop: positionY } = chart.canvas
  tooltipEl.style.opacity = '1'
  tooltipEl.style.left = positionX + tooltip.caretX + 'px'
  tooltipEl.style.top = positionY + tooltip.caretY + 'px'
  tooltipEl.style.transform = 'translate(-50%, -120%)'
}

// Crosshair plugin
const crosshairPlugin: Plugin<'line'> = {
  id: 'crosshair',
  afterDraw: (chart) => {
    if (chart.tooltip?.opacity && chart.tooltip.opacity > 0) {
      const ctx = chart.ctx
      const x = chart.tooltip.caretX
      const topY = chart.scales.y.top
      const bottomY = chart.scales.y.bottom

      ctx.save()
      ctx.beginPath()
      ctx.setLineDash([5, 5])
      ctx.moveTo(x, topY)
      ctx.lineTo(x, bottomY)
      ctx.lineWidth = 1
      ctx.strokeStyle = themeStore.isDark ? '#4b5563' : '#d1d5db'
      ctx.stroke()
      ctx.restore()
    }
  }
}

// Best day marker plugin
const bestDayPlugin: Plugin<'line'> = {
  id: 'bestDay',
  afterDatasetsDraw: (chart) => {
    const ctx = chart.ctx
    let maxValue = -Infinity
    let maxPoint: { x: number; y: number; datasetIndex: number; index: number } | null = null

    chart.data.datasets.forEach((dataset, datasetIndex) => {
      const meta = chart.getDatasetMeta(datasetIndex)
      if (!meta.hidden) {
        dataset.data.forEach((value, index) => {
          if (typeof value === 'number' && value > maxValue) {
            maxValue = value
            const point = meta.data[index]
            maxPoint = {
              x: point.x,
              y: point.y,
              datasetIndex,
              index
            }
          }
        })
      }
    })

    if (maxPoint && maxValue > 0) {
      ctx.save()
      ctx.beginPath()
      ctx.arc((maxPoint as any).x, (maxPoint as any).y, 6, 0, 2 * Math.PI)
      ctx.fillStyle = '#8b5cf6'
      ctx.fill()
      ctx.strokeStyle = themeStore.isDark ? '#1f2937' : '#ffffff'
      ctx.lineWidth = 2
      ctx.stroke()
      ctx.restore()
    }
  }
}

const chartPlugins = [crosshairPlugin, bestDayPlugin]

// Create gradient for each dataset
function hexToRgb(hex: string): [number, number, number] | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? [parseInt(result[1], 16), parseInt(result[2], 16), parseInt(result[3], 16)] : null
}

function createGradient(ctx: CanvasRenderingContext2D, color: string, height: number) {
  const gradient = ctx.createLinearGradient(0, 0, 0, height)
  let r = 0, g = 0, b = 0
  if (color.startsWith('#')) {
    const rgb = hexToRgb(color)
    if (rgb) { [r, g, b] = rgb }
  } else {
    const match = color.match(/(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/)
    if (match) { r = +match[1]; g = +match[2]; b = +match[3] }
  }
  gradient.addColorStop(0, `rgba(${r}, ${g}, ${b}, 0.25)`)
  gradient.addColorStop(1, `rgba(${r}, ${g}, ${b}, 0)`)
  return gradient
}

const chartData = computed(() => {
  const instance = chartRef.value as unknown as { chart?: Chart }
  const chart = instance?.chart
  const ctx = chart?.ctx
  const chartArea = chart?.chartArea
  const height = chartArea ? chartArea.bottom - chartArea.top : 300

  return {
    labels: props.data.map((d) => {
      const date = new Date(d.date)
      return `${date.getMonth() + 1}/${date.getDate()}`
    }),
    datasets: [
      {
        label: 'YouTube',
        data: props.data.map((d) => d.platformViews['youtube'] ?? 0),
        borderColor: '#FF0000',
        backgroundColor: ctx ? createGradient(ctx, '#FF0000', height) : 'rgba(255, 0, 0, 0.15)',
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 6,
        borderWidth: 2.5,
        fill: true,
      },
      {
        label: 'TikTok',
        data: props.data.map((d) => d.platformViews['tiktok'] ?? 0),
        borderColor: themeStore.isDark ? '#E5E7EB' : '#000000',
        backgroundColor: ctx ? createGradient(ctx, themeStore.isDark ? '#E5E7EB' : '#000000', height) :
          themeStore.isDark ? 'rgba(229, 231, 235, 0.15)' : 'rgba(0, 0, 0, 0.15)',
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 6,
        borderWidth: 2.5,
        fill: true,
      },
      {
        label: 'Instagram',
        data: props.data.map((d) => d.platformViews['instagram'] ?? 0),
        borderColor: '#E1306C',
        backgroundColor: ctx ? createGradient(ctx, '#E1306C', height) : 'rgba(225, 48, 108, 0.15)',
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 6,
        borderWidth: 2.5,
        fill: true,
      },
      {
        label: 'Naver Clip',
        data: props.data.map((d) => d.platformViews['naverClip'] ?? 0),
        borderColor: '#03C75A',
        backgroundColor: ctx ? createGradient(ctx, '#03C75A', height) : 'rgba(3, 199, 90, 0.15)',
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 6,
        borderWidth: 2.5,
        fill: true,
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
      position: 'top' as const,
      align: 'end' as const,
      labels: {
        usePointStyle: true,
        pointStyle: 'circle',
        padding: 20,
        font: {
          size: 12,
          family: 'Pretendard Variable, sans-serif'
        },
        color: themeStore.isDark ? '#d1d5db' : '#374151',
      },
    },
    tooltip: {
      enabled: false,
      external: externalTooltipHandler,
    },
  },
  scales: {
    x: {
      grid: { display: false },
      ticks: {
        color: themeStore.isDark ? '#9ca3af' : '#6B7280',
        font: { size: 11 },
      },
    },
    y: {
      beginAtZero: true,
      grid: {
        color: themeStore.isDark ? 'rgba(55, 65, 81, 0.5)' : 'rgba(0, 0, 0, 0.05)',
        drawTicks: false,
      },
      ticks: {
        color: themeStore.isDark ? '#9ca3af' : '#6B7280',
        font: { size: 11 },
        callback: (value: number | string) => {
          return formatCompactNumber(Number(value))
        },
        padding: 8,
      },
    },
  },
}))

watch(() => themeStore.isDark, () => {
  const instance = chartRef.value as unknown as { chart?: { update: () => void } }
  instance?.chart?.update()
})

watch(() => props.data, () => {
  const instance = chartRef.value as unknown as { chart?: { update: () => void } }
  instance?.chart?.update()
})
</script>
