<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRightIcon } from '@heroicons/vue/24/outline'
import TrendDirectionBadge from './TrendDirectionBadge.vue'
import type { TrendPrediction } from '@/types/trendPredictor'

interface Props {
  prediction: TrendPrediction
}

interface Emits {
  (e: 'select', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const platformConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    YOUTUBE: {
      label: 'YouTube',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    },
    TIKTOK: {
      label: 'TikTok',
      color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
    },
    INSTAGRAM: {
      label: 'Instagram',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    },
    NAVERCLIP: {
      label: 'Naver Clip',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    },
  }
  return configs[props.prediction.platform] ?? { label: props.prediction.platform, color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300' }
})

const confidencePercent = computed(() => Math.round(props.prediction.confidence * 100))

const scoreChange = computed(() => props.prediction.predictedScore - props.prediction.currentScore)

const scoreChangeColor = computed(() => {
  if (scoreChange.value > 0) return 'text-green-600 dark:text-green-400'
  if (scoreChange.value < 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-600 dark:text-gray-400'
})
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer"
    @click="emit('select', prediction.id)"
  >
    <!-- Header: Keyword + Direction -->
    <div class="flex items-start justify-between mb-3">
      <div class="flex-1 min-w-0">
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ prediction.keyword }}
        </h3>
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ prediction.category }}</span>
      </div>
      <TrendDirectionBadge :direction="prediction.direction" />
    </div>

    <!-- Platform badge -->
    <div class="mb-3">
      <span
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', platformConfig.color]"
      >
        {{ platformConfig.label }}
      </span>
    </div>

    <!-- Score transition -->
    <div class="flex items-center justify-center gap-3 mb-3 bg-gray-50 dark:bg-gray-800 rounded-lg p-3">
      <div class="text-center">
        <p class="text-xs text-gray-500 dark:text-gray-400">현재 점수</p>
        <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ prediction.currentScore }}</p>
      </div>
      <ArrowRightIcon class="w-5 h-5 text-gray-400 dark:text-gray-500 flex-shrink-0" />
      <div class="text-center">
        <p class="text-xs text-gray-500 dark:text-gray-400">예측 점수</p>
        <p class="text-xl font-bold" :class="scoreChangeColor">{{ prediction.predictedScore }}</p>
      </div>
      <span
        :class="['text-sm font-medium', scoreChangeColor]"
      >
        {{ scoreChange > 0 ? '+' : '' }}{{ scoreChange }}
      </span>
    </div>

    <!-- Confidence -->
    <div class="mb-2">
      <div class="flex items-center justify-between text-sm mb-1">
        <span class="text-gray-600 dark:text-gray-400">신뢰도</span>
        <span class="font-medium text-gray-900 dark:text-gray-100">{{ confidencePercent }}%</span>
      </div>
      <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400 rounded-full transition-all duration-500"
          :style="{ width: `${confidencePercent}%` }"
        ></div>
      </div>
    </div>

    <!-- Peak date -->
    <div v-if="prediction.peakDate" class="text-xs text-gray-500 dark:text-gray-400 mt-2">
      예상 피크: {{ new Date(prediction.peakDate).toLocaleDateString('ko-KR') }}
    </div>
  </div>
</template>
