<script setup lang="ts">
import {
  CheckCircleIcon,
  XCircleIcon,
  LightBulbIcon,
} from '@heroicons/vue/24/outline'
import type { ScriptSuggestion } from '@/types/videoScriptAssistant'

defineProps<{
  suggestion: ScriptSuggestion
}>()

const emit = defineEmits<{
  apply: [id: number]
}>()

const sectionLabels: Record<string, { label: string; color: string }> = {
  HOOK: { label: 'Hook', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300' },
  INTRO: { label: 'Intro', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300' },
  BODY: { label: 'Body', color: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300' },
  TRANSITION: { label: 'Transition', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300' },
  CTA: { label: 'CTA', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300' },
  OUTRO: { label: 'Outro', color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300' },
}
</script>

<template>
  <div
    class="rounded-xl border bg-white p-4 shadow-sm dark:bg-gray-900"
    :class="
      suggestion.isApplied
        ? 'border-green-200 dark:border-green-800'
        : 'border-gray-200 dark:border-gray-700'
    "
  >
    <!-- Header -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span
          class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
          :class="sectionLabels[suggestion.sectionType]?.color"
        >
          {{ sectionLabels[suggestion.sectionType]?.label ?? suggestion.sectionType }}
        </span>
        <span
          v-if="suggestion.isApplied"
          class="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-medium text-green-700 dark:bg-green-900/30 dark:text-green-300"
        >
          <CheckCircleIcon class="h-3 w-3" />
          적용됨
        </span>
      </div>
      <button
        v-if="!suggestion.isApplied"
        class="inline-flex items-center gap-1 rounded-lg bg-primary-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-700"
        @click="emit('apply', suggestion.id)"
      >
        <CheckCircleIcon class="h-3.5 w-3.5" />
        적용
      </button>
    </div>

    <!-- Original vs Suggested -->
    <div class="grid grid-cols-1 gap-3 tablet:grid-cols-2">
      <!-- Original -->
      <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-800/50">
        <p class="mb-1 flex items-center gap-1 text-xs font-medium text-gray-500 dark:text-gray-400">
          <XCircleIcon class="h-3.5 w-3.5 text-gray-400" />
          원본
        </p>
        <p class="text-sm text-gray-700 dark:text-gray-300">
          {{ suggestion.originalText }}
        </p>
      </div>

      <!-- Suggestion -->
      <div class="rounded-lg bg-primary-50 p-3 dark:bg-primary-900/10">
        <p class="mb-1 flex items-center gap-1 text-xs font-medium text-primary-600 dark:text-primary-400">
          <LightBulbIcon class="h-3.5 w-3.5" />
          제안
        </p>
        <p class="text-sm text-gray-800 dark:text-gray-200">
          {{ suggestion.suggestedText }}
        </p>
      </div>
    </div>

    <!-- Reason -->
    <div class="mt-3 flex items-start gap-2 rounded-lg bg-yellow-50 p-2.5 dark:bg-yellow-900/10">
      <LightBulbIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-yellow-500" />
      <p class="text-xs text-yellow-800 dark:text-yellow-300">
        {{ suggestion.reason }}
      </p>
    </div>
  </div>
</template>
