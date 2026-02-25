<template>
  <button
    class="rounded-xl border p-4 shadow-sm text-left transition-all duration-200 hover:shadow-md"
    :class="selected
      ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20 ring-1 ring-primary-500 dark:ring-primary-400'
      : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 hover:border-gray-300 dark:hover:border-gray-600'"
    @click="$emit('select')"
  >
    <!-- Style Preview Placeholder -->
    <div
      class="mb-3 flex h-24 items-center justify-center rounded-lg"
      :class="gradientClass"
    >
      <component :is="styleIcon" class="h-8 w-8 text-white/80" />
    </div>

    <!-- Style Name -->
    <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ template.name }}
    </h4>

    <!-- Popularity Bar -->
    <div class="mt-2">
      <div class="flex items-center justify-between mb-1">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('thumbnailGenerator.popularity') }}</span>
        <span class="text-xs font-medium text-gray-700 dark:text-gray-300">{{ template.popularity }}%</span>
      </div>
      <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="selected ? 'bg-primary-500' : 'bg-gray-400 dark:bg-gray-500'"
          :style="{ width: `${template.popularity}%` }"
        />
      </div>
    </div>

    <!-- Selected Indicator -->
    <div v-if="selected" class="mt-2 flex items-center gap-1 text-xs font-medium text-primary-600 dark:text-primary-400">
      <CheckCircleIcon class="h-4 w-4" />
      {{ $t('thumbnailGenerator.selected') }}
    </div>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleIcon,
  LanguageIcon,
  MinusIcon,
  Squares2X2Icon,
  UserIcon,
  FilmIcon,
  CursorArrowRaysIcon,
} from '@heroicons/vue/24/outline'
import type { ThumbnailTemplate } from '@/types/thumbnailGenerator'

const props = defineProps<{
  template: ThumbnailTemplate
  selected: boolean
}>()

defineEmits<{
  select: []
}>()

const gradientClass = computed(() => {
  switch (props.template.style) {
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
  switch (props.template.style) {
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
