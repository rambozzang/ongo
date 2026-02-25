<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Platform header -->
    <div class="mb-3 flex items-center gap-2">
      <div
        class="flex h-8 w-8 items-center justify-center rounded-lg"
        :class="platformStyle.bg"
      >
        <component :is="platformStyle.icon" class="h-4 w-4" :class="platformStyle.text" />
      </div>
      <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ platformName }}
      </span>
    </div>

    <!-- Best day + hour -->
    <div class="mb-3 grid grid-cols-2 gap-3">
      <div>
        <div class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('performanceHeatmap.recommendationBestDay') }}
        </div>
        <div class="mt-0.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ dayLabel }}
        </div>
      </div>
      <div>
        <div class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('performanceHeatmap.recommendationBestHour') }}
        </div>
        <div class="mt-0.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ recommendation.bestHour }}{{ $t('performanceHeatmap.hourSuffix') }}
        </div>
      </div>
    </div>

    <!-- Expected lift -->
    <div class="mb-3">
      <div class="text-xs text-gray-400 dark:text-gray-500">
        {{ $t('performanceHeatmap.recommendationExpectedLift') }}
      </div>
      <div class="mt-0.5 flex items-center gap-1">
        <ArrowTrendingUpIcon class="h-4 w-4 text-green-500 dark:text-green-400" />
        <span class="text-sm font-bold text-green-600 dark:text-green-400">
          +{{ recommendation.expectedLift }}%
        </span>
      </div>
    </div>

    <!-- Confidence bar -->
    <div>
      <div class="flex items-center justify-between">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('performanceHeatmap.recommendationConfidence') }}
        </span>
        <span class="text-xs font-medium text-gray-600 dark:text-gray-300">
          {{ Math.round(recommendation.confidence * 100) }}%
        </span>
      </div>
      <div class="mt-1 h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="confidenceBarColor"
          :style="{ width: `${recommendation.confidence * 100}%` }"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowTrendingUpIcon, VideoCameraIcon, MusicalNoteIcon, CameraIcon, GlobeAltIcon } from '@heroicons/vue/24/outline'
import type { UploadTimeRecommendation, DayOfWeek } from '@/types/performanceHeatmap'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  recommendation: UploadTimeRecommendation
}>()

const dayLabel = computed(() => {
  const map: Record<DayOfWeek, string> = {
    MON: t('performanceHeatmap.dayMon'),
    TUE: t('performanceHeatmap.dayTue'),
    WED: t('performanceHeatmap.dayWed'),
    THU: t('performanceHeatmap.dayThu'),
    FRI: t('performanceHeatmap.dayFri'),
    SAT: t('performanceHeatmap.daySat'),
    SUN: t('performanceHeatmap.daySun'),
  }
  return map[props.recommendation.bestDay] ?? props.recommendation.bestDay
})

const platformName = computed(() => {
  const map: Record<string, string> = {
    youtube: t('performanceHeatmap.platformYoutube'),
    tiktok: t('performanceHeatmap.platformTiktok'),
    instagram: t('performanceHeatmap.platformInstagram'),
    naverclip: t('performanceHeatmap.platformNaverClip'),
  }
  return map[props.recommendation.platform] ?? props.recommendation.platform
})

const platformStyle = computed(() => {
  const styles: Record<string, { bg: string; text: string; icon: typeof VideoCameraIcon }> = {
    youtube: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-600 dark:text-red-400', icon: VideoCameraIcon },
    tiktok: { bg: 'bg-gray-100 dark:bg-gray-700', text: 'text-gray-900 dark:text-gray-100', icon: MusicalNoteIcon },
    instagram: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-600 dark:text-pink-400', icon: CameraIcon },
    naverclip: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-600 dark:text-green-400', icon: GlobeAltIcon },
  }
  return styles[props.recommendation.platform] ?? { bg: 'bg-gray-100 dark:bg-gray-700', text: 'text-gray-500 dark:text-gray-400', icon: GlobeAltIcon }
})

const confidenceBarColor = computed(() => {
  const conf = props.recommendation.confidence
  if (conf >= 0.8) return 'bg-green-500 dark:bg-green-400'
  if (conf >= 0.6) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-red-500 dark:bg-red-400'
})
</script>
