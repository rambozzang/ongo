<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm">
    <!-- Header (always visible) -->
    <button
      class="flex w-full items-center gap-3 p-4 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-800/50"
      @click="toggleExpand"
    >
      <!-- Thumbnail mini preview -->
      <div class="flex h-10 w-14 flex-shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary-400 to-primary-600">
        <PhotoIcon class="h-5 w-5 text-white/80" />
      </div>

      <!-- Info -->
      <div class="min-w-0 flex-1">
        <h4 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ item.videoTitle }}
        </h4>
        <div class="mt-0.5 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span>{{ formatDate(item.createdAt) }}</span>
          <span class="inline-flex items-center gap-1">
            <Squares2X2Icon class="h-3.5 w-3.5" />
            {{ $t('thumbnailGenerator.thumbnailCount', { count: item.thumbnails.length }) }}
          </span>
          <span
            v-if="item.selectedThumbnailId"
            class="inline-flex items-center gap-1 text-primary-600 dark:text-primary-400"
          >
            <CheckCircleIcon class="h-3.5 w-3.5" />
            {{ $t('thumbnailGenerator.selected') }}
          </span>
        </div>
      </div>

      <!-- Expand/Collapse toggle -->
      <div class="flex-shrink-0">
        <ChevronUpIcon v-if="expanded" class="h-5 w-5 text-gray-400 dark:text-gray-500" />
        <ChevronDownIcon v-else class="h-5 w-5 text-gray-400 dark:text-gray-500" />
      </div>
    </button>

    <!-- Expandable Thumbnails -->
    <div
      class="grid transition-all duration-300 ease-in-out"
      :class="expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'"
    >
      <div class="overflow-hidden">
        <div class="border-t border-gray-200 dark:border-gray-700 p-4">
          <div class="grid grid-cols-2 gap-3 tablet:grid-cols-4">
            <div
              v-for="thumb in item.thumbnails"
              :key="thumb.id"
              class="relative overflow-hidden rounded-lg"
            >
              <!-- Thumbnail placeholder -->
              <div
                class="flex h-24 items-center justify-center tablet:h-28"
                :class="thumbnailGradient(thumb.style)"
              >
                <component :is="styleIcon(thumb.style)" class="h-6 w-6 text-white/60" />
              </div>

              <!-- Selected indicator -->
              <div
                v-if="item.selectedThumbnailId === thumb.id"
                class="absolute inset-0 flex items-center justify-center bg-black/40"
              >
                <CheckIcon class="h-6 w-6 text-white" />
              </div>

              <!-- CTR badge -->
              <div
                class="absolute bottom-1 right-1 rounded-full px-1.5 py-0.5 text-[10px] font-semibold"
                :class="ctrBadgeClass(thumb.ctrPrediction)"
              >
                {{ thumb.ctrPrediction.toFixed(1) }}%
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  PhotoIcon,
  Squares2X2Icon,
  CheckCircleIcon,
  CheckIcon,
  ChevronUpIcon,
  ChevronDownIcon,
  LanguageIcon,
  MinusIcon,
  UserIcon,
  FilmIcon,
  CursorArrowRaysIcon,
} from '@heroicons/vue/24/outline'
import type { ThumbnailHistory, ThumbnailStyle } from '@/types/thumbnailGenerator'

defineProps<{
  item: ThumbnailHistory
}>()

const expanded = ref(false)

function toggleExpand() {
  expanded.value = !expanded.value
}

function thumbnailGradient(style: ThumbnailStyle): string {
  switch (style) {
    case 'BOLD_TEXT': return 'bg-gradient-to-br from-red-500 to-orange-500'
    case 'MINIMALIST': return 'bg-gradient-to-br from-gray-400 to-gray-600'
    case 'COLLAGE': return 'bg-gradient-to-br from-purple-500 to-pink-500'
    case 'FACE_FOCUS': return 'bg-gradient-to-br from-blue-500 to-cyan-500'
    case 'CINEMATIC': return 'bg-gradient-to-br from-amber-700 to-yellow-900'
    case 'CLICKBAIT': return 'bg-gradient-to-br from-green-500 to-emerald-500'
    default: return 'bg-gradient-to-br from-gray-400 to-gray-600'
  }
}

function styleIcon(style: ThumbnailStyle) {
  switch (style) {
    case 'BOLD_TEXT': return LanguageIcon
    case 'MINIMALIST': return MinusIcon
    case 'COLLAGE': return Squares2X2Icon
    case 'FACE_FOCUS': return UserIcon
    case 'CINEMATIC': return FilmIcon
    case 'CLICKBAIT': return CursorArrowRaysIcon
    default: return Squares2X2Icon
  }
}

function ctrBadgeClass(ctr: number): string {
  if (ctr >= 10) return 'bg-green-500/90 text-white'
  if (ctr >= 7) return 'bg-yellow-500/90 text-white'
  return 'bg-red-500/90 text-white'
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}
</script>
