<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  ChevronDownIcon,
  ChevronUpIcon,
} from '@heroicons/vue/24/outline'
import type { PlatformHealthScore, HealthIssue } from '@/types/platformHealth'
import HealthScoreBar from './HealthScoreBar.vue'
import HealthIssueRow from './HealthIssueRow.vue'

const props = defineProps<{
  score: PlatformHealthScore
  issues: HealthIssue[]
}>()

const emit = defineEmits<{
  expand: [healthScoreId: number]
}>()

const expanded = ref(false)

const platformNames: Record<string, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM: 'Instagram',
  NAVER_CLIP: 'Naver Clip',
}

const platformColors: Record<string, { ring: string; gauge: string }> = {
  YOUTUBE: { ring: 'stroke-red-500', gauge: 'text-red-500' },
  TIKTOK: { ring: 'stroke-gray-700 dark:stroke-gray-300', gauge: 'text-gray-700 dark:text-gray-300' },
  INSTAGRAM: { ring: 'stroke-pink-500', gauge: 'text-pink-500' },
  NAVER_CLIP: { ring: 'stroke-green-500', gauge: 'text-green-500' },
}

const getPlatformColor = (platform: string) =>
  platformColors[platform.toUpperCase()] ?? platformColors.YOUTUBE

const overallScoreColor = computed(() => {
  if (props.score.overallScore >= 80) return 'text-green-600 dark:text-green-400'
  if (props.score.overallScore >= 60) return 'text-yellow-600 dark:text-yellow-400'
  if (props.score.overallScore >= 40) return 'text-orange-600 dark:text-orange-400'
  return 'text-red-600 dark:text-red-400'
})

const gaugeStrokeColor = computed(() => {
  if (props.score.overallScore >= 80) return 'stroke-green-500'
  if (props.score.overallScore >= 60) return 'stroke-yellow-500'
  if (props.score.overallScore >= 40) return 'stroke-orange-500'
  return 'stroke-red-500'
})

// SVG circle gauge values
const radius = 40
const circumference = 2 * Math.PI * radius
const dashOffset = computed(() => circumference - (props.score.overallScore / 100) * circumference)

const trendIcon = computed(() => {
  if (props.score.trend === 'UP') return ArrowTrendingUpIcon
  if (props.score.trend === 'DOWN') return ArrowTrendingDownIcon
  return MinusIcon
})

const trendColor = computed(() => {
  if (props.score.trend === 'UP') return 'text-green-500'
  if (props.score.trend === 'DOWN') return 'text-red-500'
  return 'text-gray-400'
})

const formatDate = (dateStr: string) => {
  try {
    return new Date(dateStr).toLocaleDateString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}

const toggleExpand = () => {
  if (!expanded.value) {
    emit('expand', props.score.id)
  }
  expanded.value = !expanded.value
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <div class="p-4">
      <!-- Header -->
      <div class="mb-4 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ platformNames[score.platform] ?? score.platform }}
          </h3>
          <component
            :is="trendIcon"
            :class="trendColor"
            class="h-4 w-4"
          />
        </div>
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ formatDate(score.checkedAt) }}
        </span>
      </div>

      <div class="flex items-center gap-6">
        <!-- Circular gauge -->
        <div class="relative flex-shrink-0">
          <svg width="100" height="100" viewBox="0 0 100 100" class="transform -rotate-90">
            <!-- Background circle -->
            <circle
              cx="50" cy="50" :r="radius"
              fill="none"
              stroke-width="8"
              class="stroke-gray-200 dark:stroke-gray-700"
            />
            <!-- Score circle -->
            <circle
              cx="50" cy="50" :r="radius"
              fill="none"
              stroke-width="8"
              stroke-linecap="round"
              :class="gaugeStrokeColor"
              :stroke-dasharray="circumference"
              :stroke-dashoffset="dashOffset"
              class="transition-all duration-700"
            />
          </svg>
          <div class="absolute inset-0 flex items-center justify-center">
            <span :class="overallScoreColor" class="text-xl font-bold">
              {{ score.overallScore }}
            </span>
          </div>
        </div>

        <!-- Sub scores -->
        <div class="flex-1 space-y-2">
          <HealthScoreBar
            :label="$t('platformHealth.growth')"
            :score="score.growthScore"
          />
          <HealthScoreBar
            :label="$t('platformHealth.engagement')"
            :score="score.engagementScore"
          />
          <HealthScoreBar
            :label="$t('platformHealth.consistency')"
            :score="score.consistencyScore"
          />
          <HealthScoreBar
            :label="$t('platformHealth.audience')"
            :score="score.audienceScore"
          />
        </div>
      </div>
    </div>

    <!-- Expand / Collapse issues -->
    <button
      class="flex w-full items-center justify-center gap-1 border-t border-gray-100 px-4 py-2.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-800/50"
      @click="toggleExpand"
    >
      <span>{{ expanded ? $t('platformHealth.hideIssues') : $t('platformHealth.showIssues') }}</span>
      <ChevronUpIcon v-if="expanded" class="h-3.5 w-3.5" />
      <ChevronDownIcon v-else class="h-3.5 w-3.5" />
    </button>

    <!-- Issues (expandable) -->
    <div
      v-if="expanded"
      class="border-t border-gray-100 p-4 dark:border-gray-700"
    >
      <div v-if="issues.length > 0" class="space-y-3">
        <HealthIssueRow
          v-for="issue in issues"
          :key="issue.id"
          :issue="issue"
        />
      </div>
      <p v-else class="text-center text-sm text-gray-400 dark:text-gray-500">
        {{ $t('platformHealth.noIssues') }}
      </p>
    </div>
  </div>
</template>
