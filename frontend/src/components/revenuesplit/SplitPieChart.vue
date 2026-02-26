<script setup lang="ts">
import { computed } from 'vue'
import type { SplitMember } from '@/types/revenueSplit'

const props = defineProps<{
  members: SplitMember[]
  totalAmount: number
}>()

const chartColors = [
  '#6366f1', // primary / indigo
  '#ec4899', // pink
  '#eab308', // yellow
  '#22c55e', // green
  '#a855f7', // purple
  '#f97316', // orange
  '#14b8a6', // teal
  '#3b82f6', // blue
]

const tailwindColors = [
  'bg-indigo-500',
  'bg-pink-500',
  'bg-yellow-500',
  'bg-green-500',
  'bg-purple-500',
  'bg-orange-500',
  'bg-teal-500',
  'bg-blue-500',
]

const getColor = (index: number) => chartColors[index % chartColors.length]
const getTailwindColor = (index: number) => tailwindColors[index % tailwindColors.length]

const radius = 80
const strokeWidth = 28
const circumference = 2 * Math.PI * radius

const segments = computed(() => {
  const result: { offset: number; length: number; color: string }[] = []
  let accum = 0
  for (let i = 0; i < props.members.length; i++) {
    const pct = props.members[i].percentage / 100
    const len = pct * circumference
    const offset = accum * circumference
    result.push({
      offset: circumference - offset,
      length: len,
      color: getColor(i),
    })
    accum += pct
  }
  return result
})

const formatKRW = (amount: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount)
</script>

<template>
  <div class="flex flex-col items-center">
    <!-- SVG Donut Chart -->
    <div class="relative">
      <svg
        width="220"
        height="220"
        viewBox="0 0 220 220"
        class="transform -rotate-90"
      >
        <!-- Background circle -->
        <circle
          cx="110"
          cy="110"
          :r="radius"
          fill="none"
          stroke="currentColor"
          :stroke-width="strokeWidth"
          class="text-gray-100 dark:text-gray-800"
        />
        <!-- Segments -->
        <circle
          v-for="(seg, index) in segments"
          :key="index"
          cx="110"
          cy="110"
          :r="radius"
          fill="none"
          :stroke="seg.color"
          :stroke-width="strokeWidth"
          :stroke-dasharray="`${seg.length} ${circumference - seg.length}`"
          :stroke-dashoffset="seg.offset"
          stroke-linecap="butt"
          class="transition-all duration-500"
        />
      </svg>
      <!-- Center text -->
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <p class="text-xs text-gray-500 dark:text-gray-400">총 금액</p>
        <p class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ formatKRW(totalAmount) }}
        </p>
      </div>
    </div>

    <!-- Legend -->
    <div class="mt-4 grid w-full grid-cols-2 gap-2">
      <div
        v-for="(member, index) in members"
        :key="member.id"
        class="flex items-center gap-2"
      >
        <span
          :class="getTailwindColor(index)"
          class="h-3 w-3 shrink-0 rounded-full"
        />
        <span class="truncate text-xs text-gray-700 dark:text-gray-300">
          {{ member.name }}
        </span>
        <span class="ml-auto text-xs font-semibold text-gray-600 dark:text-gray-400">
          {{ member.percentage }}%
        </span>
      </div>
    </div>
  </div>
</template>
