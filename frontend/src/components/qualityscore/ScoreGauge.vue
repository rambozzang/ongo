<template>
  <div class="flex flex-col items-center">
    <!-- Circular Gauge SVG -->
    <div class="relative">
      <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
        <!-- Background circle -->
        <circle
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          stroke="currentColor"
          class="text-gray-200 dark:text-gray-700"
          :stroke-width="strokeWidth"
        />
        <!-- Score arc -->
        <circle
          :cx="center"
          :cy="center"
          :r="radius"
          fill="none"
          :stroke="gradeColor"
          :stroke-width="strokeWidth"
          stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="scoreOffset"
          class="transition-all duration-1000 ease-out"
          :transform="`rotate(-90 ${center} ${center})`"
        />
        <!-- Competitor average marker -->
        <template v-if="competitorAvg !== undefined">
          <circle
            :cx="competitorMarkerX"
            :cy="competitorMarkerY"
            r="5"
            fill="currentColor"
            class="text-gray-500 dark:text-gray-400"
          />
          <line
            :x1="competitorLineX1"
            :y1="competitorLineY1"
            :x2="competitorLineX2"
            :y2="competitorLineY2"
            stroke="currentColor"
            class="text-gray-400 dark:text-gray-500"
            stroke-width="2"
            stroke-dasharray="3 3"
          />
        </template>
      </svg>
      <!-- Center content -->
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span
          class="text-4xl font-bold tablet:text-5xl"
          :style="{ color: gradeColor }"
        >
          {{ score }}
        </span>
        <span
          class="mt-0.5 text-2xl font-black tablet:text-3xl"
          :style="{ color: gradeColor }"
        >
          {{ grade }}
        </span>
      </div>
    </div>

    <!-- Competitor comparison -->
    <div v-if="competitorAvg !== undefined" class="mt-4 text-center">
      <div class="flex items-center justify-center gap-2">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-gray-400 dark:bg-gray-500" />
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ $t('qualityScore.competitorAvg') }}: {{ competitorAvg }}
        </span>
      </div>
      <p
        class="mt-1 text-sm font-semibold"
        :class="score >= competitorAvg
          ? 'text-green-600 dark:text-green-400'
          : 'text-red-600 dark:text-red-400'"
      >
        {{ score >= competitorAvg
          ? $t('qualityScore.aboveAverage')
          : $t('qualityScore.belowAverage') }}
        <span class="font-normal text-gray-500 dark:text-gray-400">
          ({{ score >= competitorAvg ? '+' : '' }}{{ score - competitorAvg }}{{ $t('qualityScore.points') }})
        </span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QualityGrade } from '@/types/qualityScore'

const props = withDefaults(defineProps<{
  score: number
  grade: QualityGrade
  competitorAvg?: number
}>(), {
  competitorAvg: undefined,
})

const size = 200
const center = size / 2
const strokeWidth = 14
const radius = (size - strokeWidth) / 2
const circumference = 2 * Math.PI * radius

const gradeColorMap: Record<QualityGrade, string> = {
  S: '#9333ea',
  A: '#16a34a',
  B: '#2563eb',
  C: '#ca8a04',
  D: '#dc2626',
}

const gradeColor = computed(() => gradeColorMap[props.grade] ?? '#6b7280')

const scoreOffset = computed(() => {
  const progress = Math.min(props.score, 100) / 100
  return circumference * (1 - progress)
})

// Competitor average marker position on the arc
const competitorAngle = computed(() => {
  if (props.competitorAvg === undefined) return 0
  const progress = Math.min(props.competitorAvg, 100) / 100
  return progress * 360 - 90 // offset -90 to start from top
})

const competitorMarkerX = computed(() => {
  const angleRad = (competitorAngle.value * Math.PI) / 180
  return center + radius * Math.cos(angleRad)
})

const competitorMarkerY = computed(() => {
  const angleRad = (competitorAngle.value * Math.PI) / 180
  return center + radius * Math.sin(angleRad)
})

const competitorLineX1 = computed(() => {
  const angleRad = (competitorAngle.value * Math.PI) / 180
  return center + (radius - strokeWidth) * Math.cos(angleRad)
})

const competitorLineY1 = computed(() => {
  const angleRad = (competitorAngle.value * Math.PI) / 180
  return center + (radius - strokeWidth) * Math.sin(angleRad)
})

const competitorLineX2 = computed(() => {
  const angleRad = (competitorAngle.value * Math.PI) / 180
  return center + (radius + strokeWidth) * Math.cos(angleRad)
})

const competitorLineY2 = computed(() => {
  const angleRad = (competitorAngle.value * Math.PI) / 180
  return center + (radius + strokeWidth) * Math.sin(angleRad)
})
</script>
