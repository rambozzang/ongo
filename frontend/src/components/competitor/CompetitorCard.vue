<script setup lang="ts">
import { computed } from 'vue'
import { StarIcon, TrashIcon, ArrowTrendingUpIcon, ArrowTrendingDownIcon, UserCircleIcon } from '@heroicons/vue/24/outline'
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

/** 성장률을 재지 못했으면 좋고 나쁨을 색으로 주장하지 않는다. */
const isGrowing = computed(() => (props.competitor.growthRate ?? 0) > 0)

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
        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
        : 'border-gray-200 bg-white dark:bg-gray-800 dark:border-gray-700',
    ]"
    @click="emit('select', competitor.id)"
  >
    <!-- Header -->
    <div class="flex items-start justify-between mb-3">
      <div class="flex items-center space-x-3">
        <!-- 프로필 이미지가 없으면 로컬 아이콘을 그린다. 근거는 Competitor.avatarUrl 참고. -->
        <img
          v-if="competitor.avatarUrl"
          :src="competitor.avatarUrl"
          :alt="competitor.name"
          class="w-12 h-12 rounded-full"
        />
        <span
          v-else
          role="img"
          :aria-label="competitor.name"
          class="flex h-12 w-12 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700"
        >
          <UserCircleIcon class="h-10 w-10 text-gray-400 dark:text-gray-500" />
        </span>
        <div>
          <h3 class="font-semibold text-gray-900 dark:text-white">
            {{ competitor.name }}
          </h3>
          <span
            :class="[
              'inline-block px-2 py-0.5 text-caption rounded-full',
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
          :title="competitor.isTracking ? '추적 중지' : '추적 시작'"
          class="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          @click.stop="emit('toggle-tracking', competitor.id)"
        >
          <StarIconSolid
            v-if="competitor.isTracking"
            class="w-5 h-5 text-yellow-500"
          />
          <StarIcon v-else class="w-5 h-5 text-gray-400 dark:text-gray-500" />
        </button>
        <button
          title="삭제"
          class="p-1.5 rounded-lg hover:bg-error-subtle transition-colors"
          @click.stop="emit('remove', competitor.id)"
        >
          <TrashIcon class="w-5 h-5 text-error-strong" />
        </button>
      </div>
    </div>

    <!-- Metrics -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-body text-gray-600 dark:text-gray-400">구독자</span>
        <span class="font-semibold text-gray-900 dark:text-white">
          {{ competitor.subscriberCount === null ? $t('analyticsView.notMeasured') : formatNumber(competitor.subscriberCount) }}
        </span>
      </div>

      <!--
        영상이 0 건이면 평균의 분모가 없다. 서버가 `null` 을 주는데 `?? 0` 으로 채우면
        "평균 0회" 라는 관측이 되어, 영상이 있고 조회수가 실제 0 인 채널과 같아진다.
      -->
      <div class="flex items-center justify-between">
        <span class="text-body text-gray-600 dark:text-gray-400">평균 조회수</span>
        <span class="font-semibold text-gray-900 dark:text-white">
          {{ competitor.avgViews === null ? $t('analyticsView.notMeasured') : formatNumber(competitor.avgViews) }}
        </span>
      </div>

      <!--
        참여율은 공개 API 로 경쟁 채널의 좋아요·댓글을 얻을 수 없어 산출되지 않는다.
        예전에는 null 자리의 0 이 "0%" 로 그려져 측정 결과처럼 보였다.
      -->
      <div class="flex items-center justify-between">
        <span class="text-body text-gray-600 dark:text-gray-400">참여율</span>
        <span
          v-if="competitor.avgEngagement !== null"
          data-testid="competitor-engagement"
          class="font-semibold text-gray-900 dark:text-white"
        >
          {{ competitor.avgEngagement }}%
        </span>
        <span
          v-else
          data-testid="competitor-engagement-unavailable"
          class="text-body text-gray-500 dark:text-gray-400"
        >
          측정 불가
        </span>
      </div>

      <div class="flex items-center justify-between">
        <span class="text-body text-gray-600 dark:text-gray-400">월간 성장률</span>
        <!--
          성장률을 재지 못했으면 화살표도 색도 그리지 않는다. `?? 0` 을 하면 수집 이력이
          없는 경쟁사가 "0% · 하락(빨강)" 으로 보인다 — 관측한 적 없는 판정이다.
        -->
        <div v-if="competitor.growthRate === null" class="text-body text-gray-400 dark:text-gray-500">
          {{ $t('analyticsView.notMeasured') }}
        </div>
        <div v-else class="flex items-center space-x-1">
          <component
            :is="isGrowing ? ArrowTrendingUpIcon : ArrowTrendingDownIcon"
            :class="[
              'w-4 h-4',
              isGrowing
                ? 'text-success-strong'
                : 'text-error-strong',
            ]"
          />
          <span
            :class="[
              'font-semibold',
              isGrowing
                ? 'text-success-strong'
                : 'text-error-strong',
            ]"
          >
            {{ Math.abs(competitor.growthRate) }}%
          </span>
        </div>
      </div>
    </div>

    <!-- Video count -->
    <div class="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
      <span class="text-body-xs text-gray-500 dark:text-gray-400">
        총 {{ competitor.videoCount === null ? $t('analyticsView.notMeasured') : `${competitor.videoCount}개` }} 영상
      </span>
    </div>
  </div>
</template>
