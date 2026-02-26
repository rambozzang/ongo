<script setup lang="ts">
import type { RevenueGoal } from '@/types/revenueGoal'
import GoalProgressRing from './GoalProgressRing.vue'

defineProps<{
  goal: RevenueGoal
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  youtube: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  instagram: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  tiktok: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  naverclip: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
  all: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300' },
}

const statusConfig: Record<string, { bg: string; text: string; label: string }> = {
  ACTIVE: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '진행 중' },
  COMPLETED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '달성' },
  EXPIRED: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-400', label: '만료' },
}

const periodLabels: Record<string, string> = {
  MONTHLY: '월간',
  QUARTERLY: '분기',
  YEARLY: '연간',
}

const getPlatformStyle = (platform: string) => platformColors[platform.toLowerCase()] ?? platformColors.all
const getStatusStyle = (status: string) => statusConfig[status] ?? statusConfig.ACTIVE

const formatAmount = (value: number): string => {
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}억원`
  if (value >= 10000) return `${(value / 10000).toFixed(0)}만원`
  return `${value.toLocaleString('ko-KR')}원`
}

const formatDate = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Name + Status -->
    <div class="mb-3 flex items-start justify-between">
      <div class="min-w-0 flex-1">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ goal.name }}
        </h3>
        <div class="mt-1.5 flex flex-wrap items-center gap-1.5">
          <span
            :class="[getPlatformStyle(goal.platform).bg, getPlatformStyle(goal.platform).text]"
            class="rounded-full px-2 py-0.5 text-xs font-medium capitalize"
          >
            {{ goal.platform === 'all' ? '전체' : goal.platform }}
          </span>
          <span
            :class="[getStatusStyle(goal.status).bg, getStatusStyle(goal.status).text]"
            class="rounded-full px-2 py-0.5 text-xs font-medium"
          >
            {{ getStatusStyle(goal.status).label }}
          </span>
          <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
            {{ periodLabels[goal.period] ?? goal.period }}
          </span>
        </div>
      </div>
      <GoalProgressRing :progress="goal.progress" :size="64" :stroke-width="5" />
    </div>

    <!-- Amount info -->
    <div class="mb-3">
      <div class="mb-1.5 flex items-baseline justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">현재 수익</span>
        <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ formatAmount(goal.currentAmount) }}
        </span>
      </div>
      <div class="mb-2 flex items-baseline justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">목표 금액</span>
        <span class="text-xs font-medium text-gray-600 dark:text-gray-400">
          {{ formatAmount(goal.targetAmount) }}
        </span>
      </div>

      <!-- Progress bar -->
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
        <div
          class="h-full rounded-full transition-all duration-700 ease-out"
          :class="{
            'bg-green-500': goal.progress >= 100,
            'bg-blue-500': goal.progress >= 75 && goal.progress < 100,
            'bg-primary-500': goal.progress >= 50 && goal.progress < 75,
            'bg-yellow-500': goal.progress >= 25 && goal.progress < 50,
            'bg-orange-500': goal.progress < 25,
          }"
          :style="{ width: `${Math.min(goal.progress, 100)}%` }"
        />
      </div>
    </div>

    <!-- Footer: Dates -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
        <span>{{ formatDate(goal.startDate) }}</span>
        <span class="text-gray-300 dark:text-gray-600">~</span>
        <span>{{ formatDate(goal.endDate) }}</span>
      </div>
    </div>
  </div>
</template>
