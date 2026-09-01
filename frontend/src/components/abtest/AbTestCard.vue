<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  TrophyIcon,
  PlayIcon,
  PauseIcon,
  CheckBadgeIcon,
  EyeIcon,
} from '@heroicons/vue/24/outline'
import AbTestResultChart from './AbTestResultChart.vue'
import type { AbTest, AbTestStatus, AbTestType } from '@/types/abtest'

const props = defineProps<{
  test: AbTest
}>()

const emit = defineEmits<{
  select: [id: number]
  start: [id: number]
  pause: [id: number]
  applyWinner: [id: number]
}>()

const { t } = useI18n({ useScope: 'global' })

const typeLabel = computed(() => {
  const labels: Record<AbTestType, string> = {
    THUMBNAIL: t('abTest.typeThumbnail'),
    TITLE: t('abTest.typeTitle'),
    DESCRIPTION: t('abTest.typeDescription'),
    TAGS: t('abTest.typeTags'),
  }
  return labels[props.test.type]
})

const statusLabel = computed(() => {
  const labels: Record<AbTestStatus, string> = {
    DRAFT: t('abTest.statusDraft'),
    RUNNING: t('abTest.statusRunning'),
    PAUSED: t('abTest.statusPaused'),
    COMPLETED: t('abTest.statusCompleted'),
    CANCELLED: t('abTest.statusCancelled'),
  }
  return labels[props.test.status]
})

const statusClasses = computed(() => {
  const styles: Record<AbTestStatus, string> = {
    DRAFT: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    RUNNING: 'bg-info-subtle text-info-strong animate-pulse',
    PAUSED: 'bg-warning-subtle text-warning-strong',
    COMPLETED: 'bg-success-subtle text-success-strong',
    CANCELLED: 'bg-error-subtle text-error-strong',
  }
  return styles[props.test.status]
})

const winner = computed(() => props.test.variants.find(v => v.isWinner))

const confidenceColor = computed(() => {
  const level = props.test.confidenceLevel ?? 0
  if (props.test.confidenceLevel == null) return 'text-gray-500 dark:text-gray-400'
  if (level >= 95) return 'text-success-strong'
  if (level >= 80) return 'text-warning-strong'
  return 'text-error-strong'
})

const confidenceBarWidth = computed(() => `${Math.min(props.test.confidenceLevel ?? 0, 100)}%`)
</script>

<template>
  <div
    class="cursor-pointer rounded-lg border border-gray-200 bg-white p-6 transition-shadow hover:shadow-lg dark:border-gray-700 dark:bg-gray-800"
    @click="emit('select', test.id)"
  >
    <!-- Header -->
    <div class="mb-4 flex items-start justify-between">
      <div class="flex-1">
        <div class="mb-1 flex items-center gap-2">
          <h3 class="text-title font-semibold text-gray-900 dark:text-white">{{ test.videoTitle }}</h3>
          <TrophyIcon v-if="winner" class="h-5 w-5 text-warning-strong" />
        </div>
        <p class="text-body-xs text-gray-500 dark:text-gray-400">
          ID: {{ test.id }}<template v-if="test.durationHours != null"> · {{ test.durationHours }}{{ $t('abTest.durationHours') }}</template>
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="rounded-full bg-info-subtle px-2.5 py-1 text-body-xs font-medium text-info-strong">
          {{ typeLabel }}
        </span>
        <span :class="['rounded-full px-2.5 py-1 text-body-xs font-medium', statusClasses]">
          {{ statusLabel }}
        </span>
      </div>
    </div>

    <!-- Variant Comparison Chart -->
    <AbTestResultChart :variants="test.variants" />

    <!-- Confidence Level -->
    <div class="mt-4 rounded-lg bg-gray-50 p-3 dark:bg-gray-700/50">
      <div class="mb-1 flex items-center justify-between text-body">
        <span class="text-gray-600 dark:text-gray-400">{{ $t('abTest.confidence') }}</span>
        <span :class="['font-semibold', confidenceColor]">{{ test.confidenceLevel != null ? `${test.confidenceLevel}%` : '—' }}</span>
      </div>
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-600">
        <div
          v-if="test.confidenceLevel != null"
          class="h-full rounded-full transition-all duration-500"
          :class="test.confidenceLevel >= 95
            ? 'bg-success'
            : test.confidenceLevel >= 80
              ? 'bg-warning'
              : 'bg-error'"
          :style="{ width: confidenceBarWidth }"
        ></div>
      </div>
    </div>

    <!-- Stats Row -->
    <div class="mt-4 grid grid-cols-2 gap-3">
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-700/50">
        <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.totalImpressions') }}</div>
        <div v-if="test.totalImpressions != null" data-testid="ab-total-impressions" class="text-body font-semibold text-gray-900 dark:text-white">{{ test.totalImpressions.toLocaleString() }}</div>
        <div v-else data-testid="ab-total-impressions-unavailable" class="text-body text-gray-500 dark:text-gray-400">{{ $t('abTest.metricUnavailable') }}</div>
      </div>
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-700/50">
        <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.variants') }}</div>
        <div class="text-body font-semibold text-gray-900 dark:text-white">{{ test.variants.length }}</div>
      </div>
    </div>

    <!-- Actions -->
    <div class="mt-4 flex items-center justify-end gap-2">
      <button
        v-if="test.status === 'DRAFT' || test.status === 'PAUSED'"
        class="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-3 py-1.5 text-body font-medium text-white transition-colors hover:bg-primary-700 dark:bg-primary-600 dark:hover:bg-primary-700"
        @click.stop="emit('start', test.id)"
      >
        <PlayIcon class="h-4 w-4" />
        {{ $t('abTest.start') }}
      </button>
      <button
        v-if="test.status === 'RUNNING'"
        class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-body font-medium text-warning-strong transition-colors hover:bg-warning-subtle"
        @click.stop="emit('pause', test.id)"
      >
        <PauseIcon class="h-4 w-4" />
        {{ $t('abTest.pause') }}
      </button>
      <button
        v-if="test.status === 'COMPLETED'"
        class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-body font-medium text-info-strong transition-colors hover:bg-info-subtle"
        @click.stop="emit('select', test.id)"
      >
        <EyeIcon class="h-4 w-4" />
        {{ $t('abTest.viewResults') }}
      </button>
      <button
        v-if="test.status === 'COMPLETED' && winner"
        class="inline-flex items-center gap-1.5 rounded-lg bg-green-600 px-3 py-1.5 text-body font-medium text-white transition-colors hover:bg-green-700 dark:bg-green-600 dark:hover:bg-green-700"
        @click.stop="emit('applyWinner', test.id)"
      >
        <CheckBadgeIcon class="h-4 w-4" />
        {{ $t('abTest.applyWinner') }}
      </button>
    </div>
  </div>
</template>
