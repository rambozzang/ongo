<script setup lang="ts">
import {
  ShieldCheckIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { BrandSafetyCheck } from '@/types/brandSafety'
import CheckItemRow from './CheckItemRow.vue'

defineProps<{
  check: BrandSafetyCheck
}>()

const statusConfig: Record<string, { label: string; bg: string; text: string }> = {
  SAFE: { label: '안전', bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
  WARNING: { label: '경고', bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
  VIOLATION: { label: '위반', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  PENDING: { label: '대기중', bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
}

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getStatusStyle = (status: string) => statusConfig[status] ?? statusConfig.PENDING
const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK

const scoreColor = (score: number) => {
  if (score >= 80) return 'text-green-600 dark:text-green-400'
  if (score >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
}

const formatDate = (dateStr: string) => {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <ShieldCheckIcon class="h-5 w-5 text-primary-500" />
      <span class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">
        {{ check.contentTitle }}
      </span>
    </div>

    <!-- Badges Row -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span
        :class="[getPlatformStyle(check.platform).bg, getPlatformStyle(check.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ check.platform }}
      </span>
      <span
        :class="[getStatusStyle(check.status).bg, getStatusStyle(check.status).text]"
        class="rounded-full px-2 py-0.5 text-xs font-semibold"
      >
        {{ getStatusStyle(check.status).label }}
      </span>
    </div>

    <!-- Overall Score -->
    <div class="mb-3 flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">전체 점수</span>
      <span class="text-lg font-bold" :class="scoreColor(check.overallScore)">
        {{ check.overallScore }}점
      </span>
    </div>

    <!-- Check Items -->
    <div v-if="check.checks.length > 0" class="mb-3 space-y-2 border-t border-gray-100 pt-3 dark:border-gray-800">
      <CheckItemRow
        v-for="item in check.checks"
        :key="item.id"
        :item="item"
      />
    </div>

    <!-- Checked At -->
    <div class="flex items-center gap-1.5 border-t border-gray-100 pt-3 dark:border-gray-800">
      <ClockIcon class="h-3.5 w-3.5 text-gray-400" />
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ formatDate(check.checkedAt) }}
      </span>
    </div>
  </div>
</template>
