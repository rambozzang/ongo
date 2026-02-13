<template>
  <div class="space-y-4">
    <!-- Header -->
    <button
      class="flex w-full items-center justify-between rounded-lg border border-gray-300 bg-white px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:hover:bg-gray-700"
      @click="collapsed = !collapsed"
    >
      <div class="flex items-center gap-2">
        <svg class="h-5 w-5 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span class="font-semibold text-gray-900 dark:text-gray-100">플랫폼 최적화 검사</span>
        <span
          v-if="results.length > 0"
          class="rounded-full px-2 py-0.5 text-xs font-semibold"
          :class="overallScoreClass"
        >
          평균 {{ averageScore }}점
        </span>
      </div>
      <div class="flex items-center gap-2">
        <span v-if="loading" class="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-primary-600" />
        <svg
          class="h-5 w-5 text-gray-600 transition-transform dark:text-gray-400"
          :class="{ 'rotate-180': !collapsed }"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
    </button>

    <div v-if="!collapsed" class="space-y-3">
      <!-- Per-platform results -->
      <div
        v-for="result in results"
        :key="result.platform"
        class="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden"
      >
        <!-- Platform header with score -->
        <div class="flex items-center justify-between bg-gray-50 dark:bg-gray-800 px-4 py-2.5">
          <div class="flex items-center gap-2">
            <div
              class="h-2.5 w-2.5 rounded-full"
              :style="{ backgroundColor: getPlatformColor(result.platform) }"
            />
            <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ getPlatformLabel(result.platform) }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <div class="h-2 w-20 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full transition-all duration-500"
                :class="getScoreBarClass(result.score)"
                :style="{ width: `${result.score}%` }"
              />
            </div>
            <span
              class="text-sm font-bold"
              :class="getScoreTextClass(result.score)"
            >
              {{ result.score }}
            </span>
          </div>
        </div>

        <!-- Suggestions -->
        <div class="divide-y divide-gray-100 dark:divide-gray-700">
          <div
            v-for="(suggestion, idx) in result.suggestions"
            :key="idx"
            class="flex items-start gap-3 px-4 py-2.5"
          >
            <!-- Severity badge -->
            <span
              class="mt-0.5 inline-flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full"
              :class="getSeverityBadgeClass(suggestion.severity)"
            >
              <svg v-if="suggestion.severity === 'GOOD'" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
              </svg>
              <svg v-else-if="suggestion.severity === 'WARNING'" class="h-3 w-3" fill="currentColor" viewBox="0 0 24 24">
                <path d="M12 9v4m0 4h.01M12 2L1 21h22L12 2z" />
              </svg>
              <svg v-else class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </span>

            <div class="flex-1 min-w-0">
              <p class="text-sm text-gray-800 dark:text-gray-200">{{ suggestion.message }}</p>
              <div v-if="suggestion.currentValue || suggestion.recommendedValue" class="mt-1 flex flex-wrap gap-2 text-xs">
                <span v-if="suggestion.currentValue" class="text-gray-500 dark:text-gray-400">
                  현재: <strong>{{ suggestion.currentValue }}</strong>
                </span>
                <span v-if="suggestion.recommendedValue" class="text-gray-500 dark:text-gray-400">
                  권장: <strong class="text-primary-600 dark:text-primary-400">{{ suggestion.recommendedValue }}</strong>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Apply All button -->
      <button
        v-if="hasFixableSuggestions"
        class="flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-primary-300 dark:border-primary-700 py-2.5 text-sm font-medium text-primary-600 dark:text-primary-400 transition-colors hover:bg-primary-50 dark:hover:bg-primary-900/20"
        @click="$emit('applyAll')"
      >
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
        자동 수정 적용
      </button>

      <!-- Empty state -->
      <div v-if="results.length === 0 && !loading" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
        최적화 검사를 실행하려면 메타데이터를 입력하세요.
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { OptimizationResult, OptimizationSeverity } from '@/types/video'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

interface Props {
  results: OptimizationResult[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

defineEmits<{
  applyAll: []
}>()

const collapsed = ref(false)

const averageScore = computed(() => {
  if (props.results.length === 0) return 0
  const sum = props.results.reduce((acc, r) => acc + r.score, 0)
  return Math.round(sum / props.results.length)
})

const overallScoreClass = computed(() => {
  const score = averageScore.value
  if (score >= 80) return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400'
  if (score >= 50) return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
  return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
})

const hasFixableSuggestions = computed(() => {
  return props.results.some(r =>
    r.suggestions.some(s => s.severity === 'ERROR' || s.severity === 'WARNING')
  )
})

function getPlatformColor(platform: Platform): string {
  return PLATFORM_CONFIG[platform]?.color ?? '#6B7280'
}

function getPlatformLabel(platform: Platform): string {
  return PLATFORM_CONFIG[platform]?.label ?? platform
}

function getScoreBarClass(score: number): string {
  if (score >= 80) return 'bg-green-500'
  if (score >= 50) return 'bg-yellow-500'
  return 'bg-red-500'
}

function getScoreTextClass(score: number): string {
  if (score >= 80) return 'text-green-600 dark:text-green-400'
  if (score >= 50) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
}

function getSeverityBadgeClass(severity: OptimizationSeverity): string {
  switch (severity) {
    case 'GOOD': return 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400'
    case 'WARNING': return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600 dark:text-yellow-400'
    case 'ERROR': return 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400'
    default: return 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
  }
}
</script>
