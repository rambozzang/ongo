<template>
  <div v-if="recommendations.length > 0" class="space-y-2">
    <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
      {{ t('schedule.optimalTime.title') }}
    </label>
    <div class="grid gap-2" :class="recommendations.length === 1 ? 'grid-cols-1' : 'grid-cols-1 tablet:grid-cols-3'">
      <button
        v-for="(rec, index) in recommendations"
        :key="index"
        type="button"
        class="group flex flex-col rounded-lg border p-3 text-left transition-all hover:shadow-sm"
        :class="
          selectedIndex === index
            ? 'border-primary-300 dark:border-primary-700 bg-primary-50 dark:bg-primary-900/20 ring-1 ring-primary-200 dark:ring-primary-800'
            : 'border-gray-200 dark:border-gray-700 hover:border-primary-200 dark:hover:border-primary-800'
        "
        @click="selectRecommendation(index)"
      >
        <!-- 시간 + 뱃지 -->
        <div class="mb-1.5 flex items-center justify-between">
          <span class="text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ rec.timeLabel }}
          </span>
          <span
            v-if="index === 0"
            class="rounded-full bg-green-100 dark:bg-green-900/30 px-2 py-0.5 text-[10px] font-semibold text-green-700 dark:text-green-400"
          >
            {{ t('schedule.optimalTime.best') }}
          </span>
        </div>

        <!-- 추천 이유 -->
        <p class="mb-2 text-xs text-gray-500 dark:text-gray-400">
          {{ getRecommendationReason(rec, index) }}
        </p>

        <!-- 신뢰도 바 -->
        <div class="mt-auto">
          <div class="mb-0.5 flex items-center justify-between">
            <span class="text-[10px] text-gray-400 dark:text-gray-500">{{ t('schedule.optimalTime.confidence') }}</span>
            <span class="text-[10px] font-medium text-gray-500 dark:text-gray-400">
              {{ Math.round(rec.confidenceScore * 100) }}%
            </span>
          </div>
          <div class="h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-300"
              :class="getConfidenceBarColor(rec.confidenceScore)"
              :style="{ width: `${rec.confidenceScore * 100}%` }"
            />
          </div>
        </div>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n({ useScope: 'global' })

export interface TimeRecommendation {
  dayOfWeek: number
  dayLabel: string
  hour: number
  timeLabel: string
  expectedViews: number
  engagementRate: number
  confidenceScore: number
  score: number
}

const props = defineProps<{
  recommendations: TimeRecommendation[]
}>()

const emit = defineEmits<{
  select: [recommendation: TimeRecommendation]
}>()

const selectedIndex = ref<number | null>(null)

function selectRecommendation(index: number) {
  selectedIndex.value = index
  emit('select', props.recommendations[index])
}

function getRecommendationReason(rec: TimeRecommendation, index: number): string {
  if (index === 0) {
    return t('schedule.optimalTime.reasonEngagement', {
      day: rec.dayLabel,
      time: rec.timeLabel,
      rate: (rec.engagementRate * 100).toFixed(1),
    })
  }
  if (index === 1) {
    return t('schedule.optimalTime.reasonReach', {
      day: rec.dayLabel,
      time: rec.timeLabel,
      views: rec.expectedViews.toLocaleString(),
    })
  }
  return t('schedule.optimalTime.reasonBalance', {
    day: rec.dayLabel,
    time: rec.timeLabel,
  })
}

function getConfidenceBarColor(score: number): string {
  if (score >= 0.8) return 'bg-green-500'
  if (score >= 0.5) return 'bg-yellow-500'
  return 'bg-gray-400'
}
</script>
