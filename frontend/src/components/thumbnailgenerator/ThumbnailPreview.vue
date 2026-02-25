<template>
  <div
    class="group relative rounded-xl border shadow-sm transition-all duration-200 hover:shadow-md"
    :class="isSelected
      ? 'border-primary-500 dark:border-primary-400 ring-1 ring-primary-500 dark:ring-primary-400 bg-white dark:bg-gray-900'
      : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900'"
  >
    <!-- Thumbnail Image Placeholder -->
    <div class="relative overflow-hidden rounded-t-xl">
      <div
        class="flex h-40 items-center justify-center tablet:h-44"
        :class="thumbnailGradient"
      >
        <component :is="styleIcon" class="h-12 w-12 text-white/60" />
      </div>

      <!-- Selected Checkmark Overlay -->
      <div
        v-if="isSelected"
        class="absolute inset-0 flex items-center justify-center bg-black/40"
      >
        <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary-500">
          <CheckIcon class="h-6 w-6 text-white" />
        </div>
      </div>

      <!-- CTR Prediction Badge -->
      <div
        class="absolute right-2 top-2 inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold backdrop-blur-sm"
        :class="ctrBadgeClass"
      >
        <ChartBarIcon class="h-3.5 w-3.5" />
        {{ thumbnail.ctrPrediction.toFixed(1) }}%
      </div>
    </div>

    <!-- Info & Actions -->
    <div class="p-3">
      <p class="mb-1 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('thumbnailGenerator.ctrPrediction') }}
      </p>
      <div class="mb-3 flex items-center gap-2">
        <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full transition-all duration-500"
            :class="ctrBarColor"
            :style="{ width: `${Math.min(thumbnail.ctrPrediction * 5, 100)}%` }"
          />
        </div>
        <span class="text-sm font-bold" :class="ctrTextColor">
          {{ thumbnail.ctrPrediction.toFixed(1) }}%
        </span>
      </div>

      <!-- Actions -->
      <div class="flex gap-2">
        <button
          class="flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
          :class="isSelected
            ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
            : 'bg-primary-600 text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600'"
          @click="$emit('select')"
        >
          <CheckCircleIcon class="h-4 w-4" />
          {{ isSelected ? $t('thumbnailGenerator.selected') : $t('thumbnailGenerator.selectButton') }}
        </button>
        <button
          class="flex items-center justify-center gap-1.5 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="$emit('download')"
        >
          <ArrowDownTrayIcon class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckIcon,
  CheckCircleIcon,
  ArrowDownTrayIcon,
  ChartBarIcon,
  LanguageIcon,
  MinusIcon,
  Squares2X2Icon,
  UserIcon,
  FilmIcon,
  CursorArrowRaysIcon,
} from '@heroicons/vue/24/outline'
import type { GeneratedThumbnail } from '@/types/thumbnailGenerator'

const props = defineProps<{
  thumbnail: GeneratedThumbnail
  isSelected: boolean
}>()

defineEmits<{
  select: []
  download: []
}>()

const ctrBadgeClass = computed(() => {
  const ctr = props.thumbnail.ctrPrediction
  if (ctr >= 10) return 'bg-green-500/90 text-white'
  if (ctr >= 7) return 'bg-yellow-500/90 text-white'
  return 'bg-red-500/90 text-white'
})

const ctrBarColor = computed(() => {
  const ctr = props.thumbnail.ctrPrediction
  if (ctr >= 10) return 'bg-green-500'
  if (ctr >= 7) return 'bg-yellow-500'
  return 'bg-red-500'
})

const ctrTextColor = computed(() => {
  const ctr = props.thumbnail.ctrPrediction
  if (ctr >= 10) return 'text-green-600 dark:text-green-400'
  if (ctr >= 7) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const thumbnailGradient = computed(() => {
  switch (props.thumbnail.style) {
    case 'BOLD_TEXT': return 'bg-gradient-to-br from-red-500 to-orange-500'
    case 'MINIMALIST': return 'bg-gradient-to-br from-gray-400 to-gray-600'
    case 'COLLAGE': return 'bg-gradient-to-br from-purple-500 to-pink-500'
    case 'FACE_FOCUS': return 'bg-gradient-to-br from-blue-500 to-cyan-500'
    case 'CINEMATIC': return 'bg-gradient-to-br from-amber-700 to-yellow-900'
    case 'CLICKBAIT': return 'bg-gradient-to-br from-green-500 to-emerald-500'
    default: return 'bg-gradient-to-br from-gray-400 to-gray-600'
  }
})

const styleIcon = computed(() => {
  switch (props.thumbnail.style) {
    case 'BOLD_TEXT': return LanguageIcon
    case 'MINIMALIST': return MinusIcon
    case 'COLLAGE': return Squares2X2Icon
    case 'FACE_FOCUS': return UserIcon
    case 'CINEMATIC': return FilmIcon
    case 'CLICKBAIT': return CursorArrowRaysIcon
    default: return Squares2X2Icon
  }
})
</script>
