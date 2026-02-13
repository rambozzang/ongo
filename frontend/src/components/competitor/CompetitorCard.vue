<script setup lang="ts">
import { computed } from 'vue'
import { StarIcon, TrashIcon, ArrowTrendingUpIcon, ArrowTrendingDownIcon } from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import type { Competitor } from '@/types/competitor'

interface Props {
  competitor: Competitor
  selected?: boolean
}

interface Emits {
  (e: 'toggle-tracking', id: number): void
  (e: 'remove', id: number): void
  (e: 'select', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const platformBadgeColor = computed(() => {
  switch (props.competitor.platform) {
    case 'YOUTUBE':
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
    case 'TIKTOK':
      return 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400'
    case 'INSTAGRAM':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400'
    case 'NAVER_CLIP':
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
  }
})

const platformLabel = computed(() => {
  switch (props.competitor.platform) {
    case 'YOUTUBE':
      return '유튜브'
    case 'TIKTOK':
      return '틱톡'
    case 'INSTAGRAM':
      return '인스타그램'
    case 'NAVER_CLIP':
      return '네이버 클립'
    default:
      return props.competitor.platform
  }
})

const isGrowing = computed(() => props.competitor.growthRate > 0)

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`
  }
  return num.toString()
}
</script>

<template>
  <div
    :class="[
      'relative rounded-lg border p-4 transition-all cursor-pointer hover:shadow-lg',
      selected
        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 dark:border-blue-400'
        : 'border-gray-200 bg-white dark:bg-gray-800 dark:border-gray-700',
    ]"
    @click="emit('select', competitor.id)"
  >
    <!-- Header -->
    <div class="flex items-start justify-between mb-3">
      <div class="flex items-center space-x-3">
        <img
          :src="competitor.avatarUrl"
          :alt="competitor.name"
          class="w-12 h-12 rounded-full"
        />
        <div>
          <h3 class="font-semibold text-gray-900 dark:text-white">
            {{ competitor.name }}
          </h3>
          <span
            :class="[
              'inline-block px-2 py-0.5 text-xs font-medium rounded-full',
              platformBadgeColor,
            ]"
          >
            {{ platformLabel }}
          </span>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="flex items-center space-x-1">
        <button
          @click.stop="emit('toggle-tracking', competitor.id)"
          :title="competitor.isTracking ? '추적 중지' : '추적 시작'"
          class="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          <StarIconSolid
            v-if="competitor.isTracking"
            class="w-5 h-5 text-yellow-500"
          />
          <StarIcon v-else class="w-5 h-5 text-gray-400 dark:text-gray-500" />
        </button>
        <button
          @click.stop="emit('remove', competitor.id)"
          title="삭제"
          class="p-1.5 rounded-lg hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
        >
          <TrashIcon class="w-5 h-5 text-red-600 dark:text-red-400" />
        </button>
      </div>
    </div>

    <!-- Metrics -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-600 dark:text-gray-400">구독자</span>
        <span class="font-semibold text-gray-900 dark:text-white">
          {{ formatNumber(competitor.subscriberCount) }}
        </span>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-600 dark:text-gray-400">평균 조회수</span>
        <span class="font-semibold text-gray-900 dark:text-white">
          {{ formatNumber(competitor.avgViews) }}
        </span>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-600 dark:text-gray-400">참여율</span>
        <span class="font-semibold text-gray-900 dark:text-white">
          {{ competitor.avgEngagement }}%
        </span>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-600 dark:text-gray-400">월간 성장률</span>
        <div class="flex items-center space-x-1">
          <component
            :is="isGrowing ? ArrowTrendingUpIcon : ArrowTrendingDownIcon"
            :class="[
              'w-4 h-4',
              isGrowing
                ? 'text-green-600 dark:text-green-400'
                : 'text-red-600 dark:text-red-400',
            ]"
          />
          <span
            :class="[
              'font-semibold',
              isGrowing
                ? 'text-green-600 dark:text-green-400'
                : 'text-red-600 dark:text-red-400',
            ]"
          >
            {{ Math.abs(competitor.growthRate) }}%
          </span>
        </div>
      </div>
    </div>

    <!-- Video count -->
    <div class="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        총 {{ competitor.videoCount }}개 영상
      </span>
    </div>
  </div>
</template>
