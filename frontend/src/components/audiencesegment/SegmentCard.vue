<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  ClockIcon,
  UserGroupIcon,
} from '@heroicons/vue/24/outline'
import type { AudienceSegment } from '@/types/audienceSegment'

interface Props {
  segment: AudienceSegment
  selected?: boolean
}

interface Emits {
  (e: 'select', segment: AudienceSegment): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const isGrowing = computed(() => props.segment.growthRate > 0)

const typeBadgeColor = computed(() => {
  const map: Record<string, string> = {
    AGE: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    GENDER: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    REGION: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    INTEREST: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    BEHAVIOR: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
    CUSTOM: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
  }
  return map[props.segment.type] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const typeLabel = computed(() => {
  const map: Record<string, string> = {
    AGE: '연령',
    GENDER: '성별',
    REGION: '지역',
    INTEREST: '관심사',
    BEHAVIOR: '행동',
    CUSTOM: '커스텀',
  }
  return map[props.segment.type] ?? props.segment.type
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString()
}

function formatTime(minutes: number): string {
  if (minutes >= 60) {
    const h = Math.floor(minutes / 60)
    const m = Math.round(minutes % 60)
    return `${h}시간 ${m}분`
  }
  return `${minutes.toFixed(1)}분`
}
</script>

<template>
  <div
    :class="[
      'relative rounded-xl border p-5 transition-all cursor-pointer hover:shadow-lg group',
      selected
        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 dark:border-primary-400 ring-1 ring-primary-500 dark:ring-primary-400'
        : 'border-gray-200 bg-white dark:bg-gray-800 dark:border-gray-700',
    ]"
    @click="emit('select', segment)"
  >
    <!-- Header -->
    <div class="flex items-start justify-between mb-4">
      <div class="flex items-center gap-3 min-w-0">
        <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
          <UserGroupIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
        </div>
        <div class="min-w-0">
          <h3 class="font-semibold text-gray-900 dark:text-white truncate">
            {{ segment.name }}
          </h3>
          <div class="flex items-center gap-2 mt-1">
            <span
              :class="['inline-block px-2 py-0.5 text-xs font-medium rounded-full', typeBadgeColor]"
            >
              {{ typeLabel }}
            </span>
            <span class="text-sm text-gray-500 dark:text-gray-400">
              {{ formatNumber(segment.size) }}명 ({{ segment.percentage }}%)
            </span>
          </div>
        </div>
      </div>

      <!-- Growth rate -->
      <div class="flex items-center gap-1 shrink-0">
        <component
          :is="isGrowing ? ArrowTrendingUpIcon : ArrowTrendingDownIcon"
          :class="[
            'w-4 h-4',
            isGrowing ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400',
          ]"
        />
        <span
          :class="[
            'text-sm font-semibold',
            isGrowing ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400',
          ]"
        >
          {{ isGrowing ? '+' : '' }}{{ segment.growthRate }}%
        </span>
      </div>
    </div>

    <!-- Key Metrics -->
    <div class="grid grid-cols-2 gap-3 mb-4">
      <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-2.5">
        <span class="block text-xs text-gray-500 dark:text-gray-400">평균 시청시간</span>
        <span class="text-sm font-semibold text-gray-900 dark:text-white">
          {{ formatTime(segment.avgWatchTime) }}
        </span>
      </div>
      <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-2.5">
        <span class="block text-xs text-gray-500 dark:text-gray-400">유지율</span>
        <span class="text-sm font-semibold text-gray-900 dark:text-white">
          {{ segment.avgRetention }}%
        </span>
      </div>
      <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-2.5">
        <span class="block text-xs text-gray-500 dark:text-gray-400">CTR</span>
        <span class="text-sm font-semibold text-gray-900 dark:text-white">
          {{ segment.avgCtr }}%
        </span>
      </div>
      <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-2.5">
        <span class="block text-xs text-gray-500 dark:text-gray-400">참여율</span>
        <span class="text-sm font-semibold text-gray-900 dark:text-white">
          {{ segment.avgEngagement }}%
        </span>
      </div>
    </div>

    <!-- Revenue Contribution -->
    <div class="mb-4">
      <div class="flex items-center justify-between mb-1">
        <span class="text-xs text-gray-500 dark:text-gray-400">수익 기여도</span>
        <span class="text-xs font-semibold text-gray-900 dark:text-white">
          {{ segment.revenueContribution }}%
        </span>
      </div>
      <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400 rounded-full transition-all duration-500"
          :style="{ width: `${Math.min(segment.revenueContribution, 100)}%` }"
        />
      </div>
    </div>

    <!-- Top Content Categories -->
    <div class="mb-3">
      <span class="block text-xs text-gray-500 dark:text-gray-400 mb-1.5">인기 카테고리</span>
      <div class="flex flex-wrap gap-1.5">
        <span
          v-for="category in segment.topContentCategories"
          :key="category"
          class="inline-block rounded-full bg-gray-100 dark:bg-gray-700 px-2.5 py-0.5 text-xs text-gray-700 dark:text-gray-300"
        >
          {{ category }}
        </span>
      </div>
    </div>

    <!-- Best Posting Times -->
    <div class="border-t border-gray-200 dark:border-gray-700 pt-3">
      <div class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
        <ClockIcon class="w-3.5 h-3.5" />
        <span>최적 업로드:</span>
        <span
          v-for="(time, index) in segment.bestPostingTimes.slice(0, 2)"
          :key="index"
          class="font-medium text-gray-700 dark:text-gray-300"
        >
          {{ time.day }} {{ time.hour }}시<span v-if="index < Math.min(segment.bestPostingTimes.length, 2) - 1">,</span>
        </span>
      </div>
    </div>
  </div>
</template>
