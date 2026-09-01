<template>
  <div class="card">
    <!--
      아래 'AI 성과 점수' 카드와 계산 근거가 다르다. 제목만으로는 두 숫자가 왜 다른지
      알 수 없어 부제로 근거를 밝힌다.
    -->
    <h3 class="mb-1 text-title font-semibold text-gray-900 dark:text-gray-100">성과 점수</h3>
    <p class="mb-6 text-caption text-gray-500 dark:text-gray-400">
      {{ $t('videoDetail.selfScoreBasis') }}
    </p>

    <div class="mb-8 grid gap-6 desktop:grid-cols-2">
      <!-- Overall Score Circle -->
      <div class="flex items-center justify-center">
        <div class="relative">
          <svg v-if="scoreResult.overall !== null" class="h-48 w-48 -rotate-90 transform">
            <!-- Background circle -->
            <circle
              cx="96"
              cy="96"
              r="88"
              stroke="currentColor"
              :stroke-width="12"
              fill="none"
              class="text-gray-200 dark:text-gray-700"
            />
            <!-- Progress circle -->
            <circle
              cx="96"
              cy="96"
              r="88"
              stroke="currentColor"
              :stroke-width="12"
              fill="none"
              :stroke-dasharray="circumference"
              :stroke-dashoffset="overallStrokeDashoffset"
              :class="overallScoreColor"
              class="transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <!-- Score text -->
          <div class="absolute inset-0 flex flex-col items-center justify-center">
            <span v-if="scoreResult.overall !== null" class="text-5xl font-bold text-gray-900 dark:text-gray-100">
              {{ displayOverallScore }}
            </span>
            <span v-else data-testid="overall-score-unavailable" class="px-4 text-center text-body font-semibold text-gray-500 dark:text-gray-400">
              {{ $t('videoDetail.scoreUnavailable') }}
            </span>
            <span class="text-body font-medium text-gray-500 dark:text-gray-400">성과 점수</span>
          </div>
        </div>
      </div>

      <!-- Sub-scores Grid -->
      <div class="grid grid-cols-2 gap-4">
        <!-- Reach Score -->
        <div class="flex flex-col items-center rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
          <svg v-if="scoreResult.reach !== null" class="h-20 w-20 -rotate-90 transform">
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              class="text-gray-200 dark:text-gray-700"
            />
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              :stroke-dasharray="subCircumference"
              :stroke-dashoffset="getSubStrokeDashoffset(displayReachScore)"
              class="text-blue-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span v-if="scoreResult.reach !== null" class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
            {{ displayReachScore }}
          </span>
          <span v-else data-testid="reach-score-unavailable" class="mt-2 text-body font-semibold text-gray-500 dark:text-gray-400">
            {{ $t('videoDetail.scoreUnavailable') }}
          </span>
          <span class="text-caption text-gray-500 dark:text-gray-400">도달률</span>
        </div>

        <!-- Engagement Score -->
        <div class="flex flex-col items-center rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
          <svg v-if="scoreResult.engagement !== null" class="h-20 w-20 -rotate-90 transform">
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              class="text-gray-200 dark:text-gray-700"
            />
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              :stroke-dasharray="subCircumference"
              :stroke-dashoffset="getSubStrokeDashoffset(displayEngagementScore)"
              class="text-green-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span v-if="scoreResult.engagement !== null" class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
            {{ displayEngagementScore }}
          </span>
          <span v-else data-testid="engagement-score-unavailable" class="mt-2 text-body font-semibold text-gray-500 dark:text-gray-400">
            {{ $t('videoDetail.scoreUnavailable') }}
          </span>
          <span class="text-caption text-gray-500 dark:text-gray-400">참여율</span>
        </div>

        <!-- Growth Score -->
        <div class="flex flex-col items-center rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
          <svg v-if="scoreResult.growth !== null" class="h-20 w-20 -rotate-90 transform">
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              class="text-gray-200 dark:text-gray-700"
            />
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              :stroke-dasharray="subCircumference"
              :stroke-dashoffset="getSubStrokeDashoffset(displayGrowthScore)"
              class="text-purple-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span v-if="scoreResult.growth !== null" class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
            {{ displayGrowthScore }}
          </span>
          <span v-else data-testid="growth-score-unavailable" class="mt-2 text-body font-semibold text-gray-500 dark:text-gray-400">
            {{ $t('videoDetail.scoreUnavailable') }}
          </span>
          <span class="text-caption text-gray-500 dark:text-gray-400">성장세</span>
        </div>

        <!-- Coverage Score -->
        <div class="flex flex-col items-center rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
          <svg class="h-20 w-20 -rotate-90 transform">
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              class="text-gray-200 dark:text-gray-700"
            />
            <circle
              cx="40"
              cy="40"
              r="36"
              stroke="currentColor"
              :stroke-width="8"
              fill="none"
              :stroke-dasharray="subCircumference"
              :stroke-dashoffset="getSubStrokeDashoffset(displayCoverageScore)"
              class="text-orange-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
            {{ displayCoverageScore }}
          </span>
          <span class="text-caption text-gray-500 dark:text-gray-400">플랫폼 커버리지</span>
        </div>
      </div>
    </div>

    <!-- Improvement Suggestions -->
    <div v-if="scoreResult.suggestions.length > 0" class="border-t border-gray-200 dark:border-gray-700 pt-6">
      <h4 class="mb-4 flex items-center gap-2 text-h3 text-gray-900 dark:text-gray-100">
        <LightBulbIcon class="h-5 w-5 text-yellow-500" />
        개선 제안
      </h4>
      <div class="space-y-3">
        <div
          v-for="(suggestion, idx) in scoreResult.suggestions"
          :key="idx"
          class="flex items-start gap-3 rounded-lg border p-3 transition-colors"
          :class="suggestionBorderClass(suggestion.priority)"
        >
          <div class="flex-shrink-0">
            <div
              class="flex h-8 w-8 items-center justify-center rounded-full"
              :class="suggestionBgClass(suggestion.priority)"
            >
              <span class="text-body-xs font-bold" :class="suggestionTextClass(suggestion.priority)">
                {{ idx + 1 }}
              </span>
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-body text-gray-700 dark:text-gray-300">{{ suggestion.text }}</p>
            <button
              v-if="suggestion.actionLabel"
              class="mt-2 inline-flex items-center gap-1 rounded-md px-2 py-1 text-caption transition-colors"
              :class="suggestionButtonClass(suggestion.priority)"
              @click="handleSuggestionAction(suggestion)"
            >
              {{ suggestion.actionLabel }}
              <ArrowRightIcon class="h-3 w-3" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { LightBulbIcon, ArrowRightIcon } from '@heroicons/vue/24/outline'
import type { Video } from '@/types/video'
import type { VideoAnalytics } from '@/types/analytics'
import { calculateVideoScore, type Suggestion } from '@/utils/scoreCalculator'

// ---- Props ----

const props = defineProps<{
  video: Video
  analytics?: VideoAnalytics[]
}>()

// ---- Router ----

const router = useRouter()

// ---- Reactive State ----

const displayOverallScore = ref(0)
const displayReachScore = ref(0)
const displayEngagementScore = ref(0)
const displayGrowthScore = ref(0)
const displayCoverageScore = ref(0)

let animationInterval: ReturnType<typeof setInterval> | null = null

// ---- Computed ----

/** Calculate the performance score */
const scoreResult = computed(() => {
  return calculateVideoScore(props.video, props.analytics)
})

/** SVG circle circumference for overall score (radius 88, stroke-width 12) */
const circumference = 2 * Math.PI * 88

/** SVG circle circumference for sub-scores (radius 36, stroke-width 8) */
const subCircumference = 2 * Math.PI * 36

/** Calculate stroke-dashoffset for overall score circle animation */
const overallStrokeDashoffset = computed(() => {
  const progress = displayOverallScore.value / 100
  return circumference * (1 - progress)
})

/** Get color class for overall score */
const overallScoreColor = computed(() => {
  const score = displayOverallScore.value
  if (score <= 30) return 'text-error-strong'
  if (score <= 60) return 'text-warning-strong'
  if (score <= 80) return 'text-success-strong'
  return 'text-primary-500'
})

// ---- Methods ----

/** Calculate stroke-dashoffset for sub-score circles */
function getSubStrokeDashoffset(score: number): number {
  const progress = score / 100
  return subCircumference * (1 - progress)
}

/** Get border class based on suggestion priority */
function suggestionBorderClass(priority: 'high' | 'medium' | 'low'): string {
  switch (priority) {
    case 'high':
      return 'border-error bg-error-subtle'
    case 'medium':
      return 'border-warning bg-warning-subtle'
    case 'low':
      return 'border-info bg-info-subtle'
  }
}

/** Get background class for suggestion number badge */
function suggestionBgClass(priority: 'high' | 'medium' | 'low'): string {
  switch (priority) {
    case 'high':
      return 'bg-red-500'
    case 'medium':
      return 'bg-yellow-500'
    case 'low':
      return 'bg-blue-500'
  }
}

/** Get text class for suggestion number badge */
function suggestionTextClass(_priority: 'high' | 'medium' | 'low'): string {
  return 'text-white'
}

/** Get button class for suggestion action button */
function suggestionButtonClass(priority: 'high' | 'medium' | 'low'): string {
  switch (priority) {
    case 'high':
      return 'bg-error-subtle text-error-strong hover:opacity-80'
    case 'medium':
      return 'bg-warning-subtle text-warning-strong hover:opacity-80'
    case 'low':
      return 'bg-info-subtle text-info-strong hover:opacity-80'
  }
}

/** Handle suggestion action click */
function handleSuggestionAction(suggestion: Suggestion): void {
  if (suggestion.actionRoute) {
    router.push(suggestion.actionRoute)
  } else if (suggestion.actionLabel === '재활용') {
    // Emit event to parent to open recycle modal
    // Since we can't emit from composition API without emits, we'll navigate
    router.push(`/videos/${props.video.id}`)
  }
}

/** Animate score count-up on mount */
function animateScores(): void {
  const duration = 1500 // 1.5 seconds
  const fps = 60
  const frames = (duration / 1000) * fps
  const overallTarget = scoreResult.value.overall ?? 0
  const reachTarget = scoreResult.value.reach ?? 0
  const engagementTarget = scoreResult.value.engagement ?? 0
  const growthTarget = scoreResult.value.growth ?? 0
  const coverageTarget = scoreResult.value.coverage
  const overallIncrement = overallTarget / frames
  const reachIncrement = reachTarget / frames
  const engagementIncrement = engagementTarget / frames
  const growthIncrement = growthTarget / frames
  const coverageIncrement = coverageTarget / frames

  let currentFrame = 0

  animationInterval = setInterval(() => {
    currentFrame++

    displayOverallScore.value = Math.min(
      overallTarget,
      Math.round(overallIncrement * currentFrame)
    )
    displayReachScore.value = Math.min(
      reachTarget,
      Math.round(reachIncrement * currentFrame)
    )
    displayEngagementScore.value = Math.min(
      engagementTarget,
      Math.round(engagementIncrement * currentFrame)
    )
    displayGrowthScore.value = Math.min(
      growthTarget,
      Math.round(growthIncrement * currentFrame)
    )
    displayCoverageScore.value = Math.min(
      coverageTarget,
      Math.round(coverageIncrement * currentFrame)
    )

    if (currentFrame >= frames) {
      clearInterval(animationInterval!)
      // Ensure final values are exact
      displayOverallScore.value = overallTarget
      displayReachScore.value = reachTarget
      displayEngagementScore.value = engagementTarget
      displayGrowthScore.value = growthTarget
      displayCoverageScore.value = coverageTarget
    }
  }, 1000 / fps)
}

// ---- Lifecycle ----

onUnmounted(() => {
  if (animationInterval) {
    clearInterval(animationInterval)
    animationInterval = null
  }
})

onMounted(() => {
  // Start animation after a brief delay
  setTimeout(() => {
    animateScores()
  }, 100)
})
</script>
