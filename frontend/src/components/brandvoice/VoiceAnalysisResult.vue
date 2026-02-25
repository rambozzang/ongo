<template>
  <div class="space-y-5">
    <!-- Detected Tone -->
    <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
      <div class="flex items-center gap-3">
        <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
          <SpeakerWaveIcon class="h-5 w-5 text-primary-600 dark:text-primary-400" />
        </div>
        <div>
          <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('brandVoice.analysis.detectedTone') }}</p>
          <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t(`brandVoice.tones.${analysis.detectedTone}`) }}
          </p>
        </div>
      </div>
    </div>

    <!-- Gauges Row -->
    <div class="grid gap-4 tablet:grid-cols-2">
      <!-- Formality Gauge -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('brandVoice.analysis.formality') }}
        </p>
        <div class="mb-1 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>{{ $t('brandVoice.analysis.formalityLow') }}</span>
          <span>{{ $t('brandVoice.analysis.formalityHigh') }}</span>
        </div>
        <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full bg-gradient-to-r from-blue-400 to-indigo-600 transition-all duration-500"
            :style="{ width: `${analysis.formalityScore * 100}%` }"
          />
        </div>
        <p class="mt-1 text-right text-xs font-medium text-gray-600 dark:text-gray-300">
          {{ Math.round(analysis.formalityScore * 100) }}%
        </p>
      </div>

      <!-- Readability Gauge -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('brandVoice.analysis.readability') }}
        </p>
        <div class="mb-1 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
          <span>{{ $t('brandVoice.analysis.readabilityLow') }}</span>
          <span>{{ $t('brandVoice.analysis.readabilityHigh') }}</span>
        </div>
        <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full transition-all duration-500"
            :class="readabilityBarColor"
            :style="{ width: `${analysis.readabilityScore * 100}%` }"
          />
        </div>
        <p class="mt-1 text-right text-xs font-medium text-gray-600 dark:text-gray-300">
          {{ Math.round(analysis.readabilityScore * 100) }}%
        </p>
      </div>
    </div>

    <!-- Top Words Bar Chart -->
    <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
      <p class="mb-3 text-xs font-medium text-gray-500 dark:text-gray-400">
        {{ $t('brandVoice.analysis.topWords') }}
      </p>
      <div class="space-y-2">
        <div
          v-for="(item, idx) in analysis.topWords"
          :key="item.word"
          class="flex items-center gap-3"
          :style="{ animationDelay: `${idx * 80}ms` }"
          style="animation: bar-slide-in 400ms ease-out backwards"
        >
          <span class="w-16 shrink-0 truncate text-right text-xs font-medium text-gray-700 dark:text-gray-300">
            {{ item.word }}
          </span>
          <div class="flex-1">
            <div class="h-5 overflow-hidden rounded bg-gray-100 dark:bg-gray-800">
              <div
                class="flex h-full items-center rounded px-2 text-xs font-medium text-white transition-all duration-500"
                :class="barColors[idx % barColors.length]"
                :style="{ width: `${(item.count / maxWordCount) * 100}%` }"
              >
                {{ $t('brandVoice.analysis.wordCount', { count: item.count }) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Stats Row -->
    <div class="grid gap-4 tablet:grid-cols-2">
      <!-- Emoji Frequency -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-100 dark:bg-amber-900/30">
            <FaceSmileIcon class="h-5 w-5 text-amber-600 dark:text-amber-400" />
          </div>
          <div>
            <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('brandVoice.analysis.emojiFrequency') }}</p>
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ Math.round(analysis.emojiFrequency * 100) }}%
            </p>
          </div>
        </div>
      </div>

      <!-- Avg Sentence Length -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 dark:bg-emerald-900/30">
            <DocumentTextIcon class="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
          </div>
          <div>
            <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('brandVoice.analysis.avgSentenceLength') }}</p>
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ analysis.avgSentenceLength }} {{ $t('brandVoice.analysis.avgSentenceLengthUnit') }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SpeakerWaveIcon, FaceSmileIcon, DocumentTextIcon } from '@heroicons/vue/24/outline'
import type { VoiceAnalysis } from '@/types/brandVoice'

const props = defineProps<{
  analysis: VoiceAnalysis
}>()

const barColors = [
  'bg-primary-500',
  'bg-blue-500',
  'bg-emerald-500',
  'bg-amber-500',
  'bg-purple-500',
]

const maxWordCount = computed(() =>
  Math.max(...props.analysis.topWords.map((w) => w.count), 1),
)

const readabilityBarColor = computed(() => {
  const score = props.analysis.readabilityScore
  if (score >= 0.7) return 'bg-gradient-to-r from-emerald-400 to-green-600'
  if (score >= 0.4) return 'bg-gradient-to-r from-yellow-400 to-amber-600'
  return 'bg-gradient-to-r from-red-400 to-red-600'
})
</script>

<style scoped>
@keyframes bar-slide-in {
  from {
    opacity: 0;
    transform: translateX(-12px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
