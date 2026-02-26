<script setup lang="ts">
import {
  BeakerIcon,
  TrophyIcon,
} from '@heroicons/vue/24/outline'
import type { ABTestResult } from '@/types/abTestResult'

const props = defineProps<{
  result: ABTestResult
}>()

const emit = defineEmits<{
  click: [result: ABTestResult]
}>()

const statusConfig: Record<string, { bg: string; text: string; label: string; pulse?: boolean }> = {
  RUNNING: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '진행중', pulse: true },
  COMPLETED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '완료' },
  PAUSED: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '일시중지' },
  CANCELLED: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300', label: '취소됨' },
}

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getStatusStyle = (status: string) => statusConfig[status] ?? statusConfig.CANCELLED
const getPlatformStyle = (platform: string) => platformColors[platform.toUpperCase()] ?? platformColors.YOUTUBE

const confidenceColor = (val: number) => {
  if (val > 95) return 'bg-green-500'
  if (val >= 80) return 'bg-yellow-500'
  return 'bg-red-500'
}

const winnerVariant = () => {
  if (!props.result.winner) return null
  return props.result.variants.find((v) => v.isWinner) ?? null
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('click', result)"
  >
    <!-- Header: Test name + badges -->
    <div class="mb-3 flex flex-wrap items-start justify-between gap-2">
      <div class="flex items-center gap-2">
        <BeakerIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ result.testName }}
        </h3>
      </div>
      <div class="flex items-center gap-2">
        <span
          :class="[getPlatformStyle(result.platform).bg, getPlatformStyle(result.platform).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium"
        >
          {{ result.platform }}
        </span>
        <span
          :class="[getStatusStyle(result.status).bg, getStatusStyle(result.status).text]"
          class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium"
        >
          <span
            v-if="getStatusStyle(result.status).pulse"
            class="h-1.5 w-1.5 animate-pulse rounded-full bg-green-500"
          />
          {{ getStatusStyle(result.status).label }}
        </span>
      </div>
    </div>

    <!-- Metric + Confidence -->
    <div class="mb-4 space-y-2">
      <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
        <span>측정 지표: <span class="font-semibold text-gray-700 dark:text-gray-300">{{ result.metric }}</span></span>
        <span>신뢰도: <span class="font-semibold text-gray-700 dark:text-gray-300">{{ result.confidence }}%</span></span>
      </div>
      <div class="h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
        <div
          :class="confidenceColor(result.confidence)"
          class="h-full rounded-full transition-all duration-500"
          :style="{ width: `${result.confidence}%` }"
        />
      </div>
    </div>

    <!-- Variants summary -->
    <div class="mb-3 grid gap-2" :class="result.variants.length <= 2 ? 'grid-cols-2' : 'grid-cols-3'">
      <div
        v-for="variant in result.variants"
        :key="variant.id"
        class="rounded-lg border p-2.5"
        :class="variant.isWinner
          ? 'border-green-300 bg-green-50 dark:border-green-700 dark:bg-green-900/20'
          : 'border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50'"
      >
        <div class="mb-1 flex items-center gap-1">
          <span class="text-xs font-semibold text-gray-900 dark:text-gray-100">{{ variant.name }}</span>
          <span
            v-if="variant.isControl"
            class="rounded bg-gray-200 px-1 py-0.5 text-[10px] font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400"
          >
            컨트롤
          </span>
          <TrophyIcon v-if="variant.isWinner" class="h-3.5 w-3.5 text-yellow-500" />
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          CTR <span class="font-semibold text-gray-700 dark:text-gray-300">{{ variant.ctr }}%</span>
        </p>
      </div>
    </div>

    <!-- Winner -->
    <div
      v-if="winnerVariant()"
      class="flex items-center gap-2 rounded-lg bg-yellow-50 px-3 py-2 dark:bg-yellow-900/20"
    >
      <TrophyIcon class="h-4 w-4 text-yellow-500" />
      <span class="text-xs font-semibold text-yellow-800 dark:text-yellow-300">
        우승: {{ winnerVariant()?.name }}
      </span>
    </div>
  </div>
</template>
