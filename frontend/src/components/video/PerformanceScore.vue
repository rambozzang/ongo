<template>
  <div class="card">
    <h3 class="mb-6 text-lg font-semibold text-gray-900 dark:text-gray-100">성과 점수</h3>

    <div class="mb-8 grid gap-6 desktop:grid-cols-2">
      <!-- Overall Score Circle -->
      <div class="flex items-center justify-center">
        <div class="relative">
          <svg class="h-48 w-48 -rotate-90 transform">
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
            <span class="text-5xl font-bold text-gray-900 dark:text-gray-100">
              {{ displayOverallScore }}
            </span>
            <span class="text-sm font-medium text-gray-500 dark:text-gray-400">성과 점수</span>
          </div>
        </div>
      </div>

      <!-- Sub-scores Grid -->
      <div class="grid grid-cols-2 gap-4">
        <!-- Reach Score -->
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
              :stroke-dashoffset="getSubStrokeDashoffset(displayReachScore)"
              class="text-blue-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ displayReachScore }}
          </span>
          <span class="text-xs font-medium text-gray-500 dark:text-gray-400">도달률</span>
        </div>

        <!-- Engagement Score -->
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
              :stroke-dashoffset="getSubStrokeDashoffset(displayEngagementScore)"
              class="text-green-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ displayEngagementScore }}
          </span>
          <span class="text-xs font-medium text-gray-500 dark:text-gray-400">참여율</span>
        </div>

        <!-- Growth Score -->
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
              :stroke-dashoffset="getSubStrokeDashoffset(displayGrowthScore)"
              class="text-purple-500 transition-all duration-1000 ease-out"
              stroke-linecap="round"
            />
          </svg>
          <span class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ displayGrowthScore }}
          </span>
          <span class="text-xs font-medium text-gray-500 dark:text-gray-400">성장세</span>
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
          <span class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ displayCoverageScore }}
          </span>
          <span class="text-xs font-medium text-gray-500 dark:text-gray-400">플랫폼 커버리지</span>
        </div>
      </div>
    </div>

    <!-- Improvement Suggestions -->
    <div v-if="scoreResult.suggestions.length > 0" class="border-t border-gray-200 dark:border-gray-700 pt-6">
      <h4 class="mb-4 flex items-center gap-2 text-base font-semibold text-gray-900 dark:text-gray-100">
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
              <span class="text-xs font-bold" :class="suggestionTextClass(suggestion.priority)">
                {{ idx + 1 }}
              </span>
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <p class="text-sm text-gray-700 dark:text-gray-300">{{ suggestion.text }}</p>
            <button
              v-if="suggestion.actionLabel"
              class="mt-2 inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors"
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
  if (score <= 30) return 'text-red-500'
  if (score <= 60) return 'text-yellow-500'
  if (score <= 80) return 'text-green-500'
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
      return 'border-red-200 dark:border-red-900/30 bg-red-50/50 dark:bg-red-900/10'
    case 'medium':
      return 'border-yellow-200 dark:border-yellow-900/30 bg-yellow-50/50 dark:bg-yellow-900/10'
    case 'low':
      return 'border-blue-200 dark:border-blue-900/30 bg-blue-50/50 dark:bg-blue-900/10'
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
      return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 hover:bg-red-200 dark:hover:bg-red-900/50'
    case 'medium':
      return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400 hover:bg-yellow-200 dark:hover:bg-yellow-900/50'
    case 'low':
      return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 hover:bg-blue-200 dark:hover:bg-blue-900/50'
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
  const overallIncrement = scoreResult.value.overall / frames
  const reachIncrement = scoreResult.value.reach / frames
  const engagementIncrement = scoreResult.value.engagement / frames
  const growthIncrement = scoreResult.value.growth / frames
  const coverageIncrement = scoreResult.value.coverage / frames

  let currentFrame = 0

  animationInterval = setInterval(() => {
    currentFrame++

    displayOverallScore.value = Math.min(
      scoreResult.value.overall,
      Math.round(overallIncrement * currentFrame)
    )
    displayReachScore.value = Math.min(
      scoreResult.value.reach,
      Math.round(reachIncrement * currentFrame)
    )
    displayEngagementScore.value = Math.min(
      scoreResult.value.engagement,
      Math.round(engagementIncrement * currentFrame)
    )
    displayGrowthScore.value = Math.min(
      scoreResult.value.growth,
      Math.round(growthIncrement * currentFrame)
    )
    displayCoverageScore.value = Math.min(
      scoreResult.value.coverage,
      Math.round(coverageIncrement * currentFrame)
    )

    if (currentFrame >= frames) {
      clearInterval(animationInterval!)
      // Ensure final values are exact
      displayOverallScore.value = scoreResult.value.overall
      displayReachScore.value = scoreResult.value.reach
      displayEngagementScore.value = scoreResult.value.engagement
      displayGrowthScore.value = scoreResult.value.growth
      displayCoverageScore.value = scoreResult.value.coverage
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
