<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { SentimentTrend } from '@/types/socialListening'

const props = defineProps<{
  trends: SentimentTrend[]
}>()

const svgRef = ref<SVGSVGElement | null>(null)
const tooltip = ref<{ show: boolean; x: number; y: number; data: SentimentTrend | null }>({
  show: false,
  x: 0,
  y: 0,
  data: null,
})

const padding = { top: 20, right: 20, bottom: 40, left: 40 }
const chartWidth = 600
const chartHeight = 260

const innerWidth = chartWidth - padding.left - padding.right
const innerHeight = chartHeight - padding.top - padding.bottom

const maxValue = computed(() => {
  if (props.trends.length === 0) return 10
  return Math.max(...props.trends.map((t) => t.total)) * 1.15
})

const yTicks = computed(() => {
  const max = maxValue.value
  const step = Math.ceil(max / 5)
  const ticks: number[] = []
  for (let i = 0; i <= 5; i++) {
    ticks.push(step * i)
  }
  return ticks
})

function xPos(index: number): number {
  if (props.trends.length <= 1) return padding.left + innerWidth / 2
  return padding.left + (index / (props.trends.length - 1)) * innerWidth
}

function yPos(value: number): number {
  return padding.top + innerHeight - (value / maxValue.value) * innerHeight
}

function buildAreaPath(key: 'positive' | 'neutral' | 'negative', stackBelow?: 'positive' | 'neutral'): string {
  if (props.trends.length === 0) return ''
  const points: string[] = []
  const bottomPoints: string[] = []

  for (let i = 0; i < props.trends.length; i++) {
    const x = xPos(i)
    let stackValue = props.trends[i][key]
    if (stackBelow === 'positive') {
      stackValue += props.trends[i].positive
    } else if (stackBelow === 'neutral') {
      stackValue += props.trends[i].positive + props.trends[i].neutral
    }
    const y = yPos(stackValue)
    points.push(`${x},${y}`)

    let baseValue = 0
    if (stackBelow === 'positive') {
      baseValue = props.trends[i].positive
    } else if (stackBelow === 'neutral') {
      baseValue = props.trends[i].positive + props.trends[i].neutral
    }
    bottomPoints.unshift(`${x},${yPos(baseValue)}`)
  }

  return `M${points.join(' L')} L${bottomPoints.join(' L')} Z`
}

function buildLinePath(key: 'positive' | 'neutral' | 'negative', stackBelow?: 'positive' | 'neutral'): string {
  if (props.trends.length === 0) return ''
  const points: string[] = []
  for (let i = 0; i < props.trends.length; i++) {
    const x = xPos(i)
    let stackValue = props.trends[i][key]
    if (stackBelow === 'positive') {
      stackValue += props.trends[i].positive
    } else if (stackBelow === 'neutral') {
      stackValue += props.trends[i].positive + props.trends[i].neutral
    }
    const y = yPos(stackValue)
    points.push(`${i === 0 ? 'M' : 'L'}${x},${y}`)
  }
  return points.join(' ')
}

const positiveArea = computed(() => buildAreaPath('positive'))
const neutralArea = computed(() => buildAreaPath('neutral', 'positive'))
const negativeArea = computed(() => buildAreaPath('negative', 'neutral'))

const positiveLine = computed(() => buildLinePath('positive'))
const neutralLine = computed(() => buildLinePath('neutral', 'positive'))
const negativeLine = computed(() => buildLinePath('negative', 'neutral'))

const dateLabels = computed(() => {
  if (props.trends.length <= 7) return props.trends.map((t) => t.date)
  const step = Math.ceil(props.trends.length / 7)
  return props.trends.filter((_, i) => i % step === 0 || i === props.trends.length - 1).map((t) => t.date)
})

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function handleMouseMove(event: MouseEvent, index: number) {
  if (!svgRef.value) return
  const rect = svgRef.value.getBoundingClientRect()
  tooltip.value = {
    show: true,
    x: event.clientX - rect.left,
    y: event.clientY - rect.top - 10,
    data: props.trends[index],
  }
}

function handleMouseLeave() {
  tooltip.value.show = false
}

const resizeKey = ref(0)
function handleResize() {
  resizeKey.value++
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="relative w-full">
    <svg
      ref="svgRef"
      :key="resizeKey"
      :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
      class="w-full"
      preserveAspectRatio="xMidYMid meet"
      @mouseleave="handleMouseLeave"
    >
      <!-- Grid lines -->
      <line
        v-for="tick in yTicks"
        :key="'grid-' + tick"
        :x1="padding.left"
        :y1="yPos(tick)"
        :x2="chartWidth - padding.right"
        :y2="yPos(tick)"
        class="stroke-gray-200 dark:stroke-gray-700"
        stroke-width="0.5"
        stroke-dasharray="4 2"
      />

      <!-- Y-axis labels -->
      <text
        v-for="tick in yTicks"
        :key="'ylabel-' + tick"
        :x="padding.left - 8"
        :y="yPos(tick) + 4"
        text-anchor="end"
        class="fill-gray-400 dark:fill-gray-500 text-[10px]"
      >
        {{ tick }}
      </text>

      <!-- Stacked areas -->
      <path :d="negativeArea" fill="rgba(239, 68, 68, 0.15)" class="dark:opacity-80" />
      <path :d="neutralArea" fill="rgba(156, 163, 175, 0.15)" class="dark:opacity-80" />
      <path :d="positiveArea" fill="rgba(34, 197, 94, 0.15)" class="dark:opacity-80" />

      <!-- Lines -->
      <path :d="negativeLine" fill="none" stroke="#ef4444" stroke-width="2" stroke-linejoin="round" />
      <path :d="neutralLine" fill="none" stroke="#9ca3af" stroke-width="2" stroke-linejoin="round" />
      <path :d="positiveLine" fill="none" stroke="#22c55e" stroke-width="2" stroke-linejoin="round" />

      <!-- Hover points -->
      <rect
        v-for="(_trend, i) in trends"
        :key="'hover-' + i"
        :x="xPos(i) - (innerWidth / trends.length) / 2"
        :y="padding.top"
        :width="innerWidth / trends.length"
        :height="innerHeight"
        fill="transparent"
        @mousemove="(e: MouseEvent) => handleMouseMove(e, i)"
        @mouseleave="handleMouseLeave"
      />

      <!-- Data points -->
      <circle
        v-for="(trend, i) in trends"
        :key="'dot-pos-' + i"
        :cx="xPos(i)"
        :cy="yPos(trend.positive)"
        r="3"
        fill="#22c55e"
        class="pointer-events-none"
      />
      <circle
        v-for="(trend, i) in trends"
        :key="'dot-neu-' + i"
        :cx="xPos(i)"
        :cy="yPos(trend.positive + trend.neutral)"
        r="3"
        fill="#9ca3af"
        class="pointer-events-none"
      />
      <circle
        v-for="(trend, i) in trends"
        :key="'dot-neg-' + i"
        :cx="xPos(i)"
        :cy="yPos(trend.positive + trend.neutral + trend.negative)"
        r="3"
        fill="#ef4444"
        class="pointer-events-none"
      />

      <!-- X-axis labels -->
      <text
        v-for="label in dateLabels"
        :key="'xlabel-' + label"
        :x="xPos(trends.findIndex((t) => t.date === label))"
        :y="chartHeight - 8"
        text-anchor="middle"
        class="fill-gray-400 dark:fill-gray-500 text-[10px]"
      >
        {{ formatDate(label) }}
      </text>
    </svg>

    <!-- Tooltip -->
    <div
      v-if="tooltip.show && tooltip.data"
      class="pointer-events-none absolute z-10 rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-lg dark:border-gray-600 dark:bg-gray-800"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px', transform: 'translate(-50%, -100%)' }"
    >
      <p class="mb-1 font-medium text-gray-900 dark:text-gray-100">
        {{ tooltip.data.date }}
      </p>
      <div class="flex items-center gap-2">
        <span class="inline-block h-2 w-2 rounded-full bg-green-500" />
        <span class="text-gray-600 dark:text-gray-300">{{ $t('socialListening.positive') }}: {{ tooltip.data.positive }}</span>
      </div>
      <div class="flex items-center gap-2">
        <span class="inline-block h-2 w-2 rounded-full bg-gray-400" />
        <span class="text-gray-600 dark:text-gray-300">{{ $t('socialListening.neutral') }}: {{ tooltip.data.neutral }}</span>
      </div>
      <div class="flex items-center gap-2">
        <span class="inline-block h-2 w-2 rounded-full bg-red-500" />
        <span class="text-gray-600 dark:text-gray-300">{{ $t('socialListening.negative') }}: {{ tooltip.data.negative }}</span>
      </div>
      <p class="mt-1 border-t border-gray-200 pt-1 font-medium text-gray-900 dark:border-gray-600 dark:text-gray-100">
        {{ $t('socialListening.total') }}: {{ tooltip.data.total }}
      </p>
    </div>

    <!-- Legend -->
    <div class="mt-2 flex items-center justify-center gap-4 text-xs text-gray-500 dark:text-gray-400">
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-green-500" />
        {{ $t('socialListening.positive') }}
      </div>
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-gray-400" />
        {{ $t('socialListening.neutral') }}
      </div>
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-red-500" />
        {{ $t('socialListening.negative') }}
      </div>
    </div>
  </div>
</template>
