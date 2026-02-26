<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: Category + Grade badge -->
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t(`qualityScore.category.${metric.category}`) }}
      </h3>
      <span
        class="inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold text-white"
        :class="gradeBgClass"
      >
        {{ metric.grade }}
      </span>
    </div>

    <!-- Score bar -->
    <div class="mt-3">
      <div class="flex items-center justify-between text-xs">
        <span class="text-gray-500 dark:text-gray-400">{{ $t('qualityScore.score') }}</span>
        <span class="font-semibold" :class="gradeTextClass">
          {{ metric.score }} / {{ metric.maxScore }}
        </span>
      </div>
      <div class="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-full rounded-full transition-all duration-700 ease-out"
          :class="gradeBarClass"
          :style="{ width: `${percentage}%` }"
        />
      </div>
    </div>

    <!-- Feedback -->
    <p class="mt-3 text-xs leading-relaxed text-gray-600 dark:text-gray-400">
      {{ metric.feedback }}
    </p>

    <!-- Expandable suggestions -->
    <div v-if="metric.suggestions.length > 0" class="mt-3">
      <button
        class="flex w-full items-center gap-1.5 text-xs font-medium text-primary-600 transition-colors hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
        @click="expanded = !expanded"
      >
        <LightBulbIcon class="h-3.5 w-3.5" />
        {{ expanded
          ? $t('qualityScore.hideSuggestions')
          : $t('qualityScore.showSuggestions', { count: metric.suggestions.length }) }}
        <ChevronUpIcon v-if="expanded" class="ml-auto h-3.5 w-3.5" />
        <ChevronDownIcon v-else class="ml-auto h-3.5 w-3.5" />
      </button>

      <div
        class="grid transition-all duration-300 ease-in-out"
        :class="expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'"
      >
        <div class="overflow-hidden">
          <ul class="mt-2 space-y-1.5">
            <li
              v-for="(suggestion, idx) in metric.suggestions"
              :key="idx"
              class="flex items-start gap-2 rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-700 dark:bg-gray-800/50 dark:text-gray-300"
            >
              <ArrowRightIcon class="mt-0.5 h-3 w-3 flex-shrink-0" :class="gradeTextClass" />
              <span>{{ suggestion }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  LightBulbIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ArrowRightIcon,
} from '@heroicons/vue/24/outline'
import type { QualityMetric, QualityGrade } from '@/types/qualityScore'

const props = defineProps<{
  metric: QualityMetric
}>()

const expanded = ref(false)

const percentage = computed(() =>
  Math.round((props.metric.score / props.metric.maxScore) * 100),
)

const gradeBgMap: Record<QualityGrade, string> = {
  S: 'bg-purple-600',
  A: 'bg-green-600',
  B: 'bg-blue-600',
  C: 'bg-yellow-600',
  D: 'bg-red-600',
}

const gradeTextMap: Record<QualityGrade, string> = {
  S: 'text-purple-600 dark:text-purple-400',
  A: 'text-green-600 dark:text-green-400',
  B: 'text-blue-600 dark:text-blue-400',
  C: 'text-yellow-600 dark:text-yellow-400',
  D: 'text-red-600 dark:text-red-400',
}

const gradeBarMap: Record<QualityGrade, string> = {
  S: 'bg-purple-500',
  A: 'bg-green-500',
  B: 'bg-blue-500',
  C: 'bg-yellow-500',
  D: 'bg-red-500',
}

const gradeBgClass = computed(() => gradeBgMap[props.metric.grade] ?? 'bg-gray-600')
const gradeTextClass = computed(() => gradeTextMap[props.metric.grade] ?? 'text-gray-600 dark:text-gray-400')
const gradeBarClass = computed(() => gradeBarMap[props.metric.grade] ?? 'bg-gray-500')
</script>
