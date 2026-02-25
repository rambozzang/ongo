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
    RUNNING: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 animate-pulse',
    PAUSED: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    COMPLETED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    CANCELLED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  }
  return styles[props.test.status]
})

const winner = computed(() => props.test.variants.find(v => v.isWinner))

const confidenceColor = computed(() => {
  const level = props.test.confidenceLevel
  if (level >= 95) return 'text-green-600 dark:text-green-400'
  if (level >= 80) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const confidenceBarWidth = computed(() => `${Math.min(props.test.confidenceLevel, 100)}%`)
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
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white">{{ test.videoTitle }}</h3>
          <TrophyIcon v-if="winner" class="h-5 w-5 text-yellow-500" />
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          ID: {{ test.id }} | {{ test.durationHours }}{{ $t('abTest.durationHours') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="rounded-full bg-purple-100 px-2.5 py-1 text-xs font-medium text-purple-700 dark:bg-purple-900/30 dark:text-purple-400">
          {{ typeLabel }}
        </span>
        <span :class="['rounded-full px-2.5 py-1 text-xs font-medium', statusClasses]">
          {{ statusLabel }}
        </span>
      </div>
    </div>

    <!-- Variant Comparison Chart -->
    <AbTestResultChart :variants="test.variants" />

    <!-- Confidence Level -->
    <div class="mt-4 rounded-lg bg-gray-50 p-3 dark:bg-gray-700/50">
      <div class="mb-1 flex items-center justify-between text-sm">
        <span class="text-gray-600 dark:text-gray-400">{{ $t('abTest.confidence') }}</span>
        <span :class="['font-semibold', confidenceColor]">{{ test.confidenceLevel }}%</span>
      </div>
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-600">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="test.confidenceLevel >= 95
            ? 'bg-green-500'
            : test.confidenceLevel >= 80
              ? 'bg-yellow-500'
              : 'bg-red-500'"
          :style="{ width: confidenceBarWidth }"
        ></div>
      </div>
    </div>

    <!-- Stats Row -->
    <div class="mt-4 grid grid-cols-2 gap-3">
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-700/50">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.totalImpressions') }}</div>
        <div class="text-sm font-semibold text-gray-900 dark:text-white">{{ test.totalImpressions.toLocaleString() }}</div>
      </div>
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-700/50">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.variants') }}</div>
        <div class="text-sm font-semibold text-gray-900 dark:text-white">{{ test.variants.length }}</div>
      </div>
    </div>

    <!-- Actions -->
    <div class="mt-4 flex items-center justify-end gap-2">
      <button
        v-if="test.status === 'DRAFT' || test.status === 'PAUSED'"
        class="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700"
        @click.stop="emit('start', test.id)"
      >
        <PlayIcon class="h-4 w-4" />
        {{ $t('abTest.start') }}
      </button>
      <button
        v-if="test.status === 'RUNNING'"
        class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-yellow-700 transition-colors hover:bg-yellow-50 dark:text-yellow-400 dark:hover:bg-yellow-900/20"
        @click.stop="emit('pause', test.id)"
      >
        <PauseIcon class="h-4 w-4" />
        {{ $t('abTest.pause') }}
      </button>
      <button
        v-if="test.status === 'COMPLETED'"
        class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-blue-600 transition-colors hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20"
        @click.stop="emit('select', test.id)"
      >
        <EyeIcon class="h-4 w-4" />
        {{ $t('abTest.viewResults') }}
      </button>
      <button
        v-if="test.status === 'COMPLETED' && winner"
        class="inline-flex items-center gap-1.5 rounded-lg bg-green-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-green-700 dark:bg-green-600 dark:hover:bg-green-700"
        @click.stop="emit('applyWinner', test.id)"
      >
        <CheckBadgeIcon class="h-4 w-4" />
        {{ $t('abTest.applyWinner') }}
      </button>
    </div>
  </div>
</template>
