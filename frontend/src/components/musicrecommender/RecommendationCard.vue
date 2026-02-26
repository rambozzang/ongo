<script setup lang="ts">
import { computed } from 'vue'
import {
  MusicalNoteIcon,
  CheckCircleIcon,
  FilmIcon,
} from '@heroicons/vue/24/outline'
import type { MusicRecommendation } from '@/types/musicRecommender'

interface Props {
  recommendation: MusicRecommendation
}

interface Emits {
  (e: 'select', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    PENDING: {
      label: '대기중',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    RECOMMENDED: {
      label: '추천됨',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    APPLIED: {
      label: '적용됨',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
  }
  return configs[props.recommendation.status] ?? configs.PENDING
})

const selectedTrack = computed(() => {
  if (!props.recommendation.selectedTrackId) return null
  return props.recommendation.tracks.find(
    (t) => t.id === props.recommendation.selectedTrackId,
  )
})
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer group"
    @click="emit('select', recommendation.id)"
  >
    <!-- Video icon & title -->
    <div class="flex items-start gap-3 mb-3">
      <div class="flex-shrink-0 w-10 h-10 rounded-lg bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
        <FilmIcon class="w-5 h-5 text-gray-500 dark:text-gray-400" />
      </div>
      <div class="flex-1 min-w-0">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ recommendation.videoTitle }}
        </h3>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          {{ new Date(recommendation.createdAt).toLocaleDateString('ko-KR') }}
        </p>
      </div>
      <span
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium flex-shrink-0', statusConfig.color]"
      >
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Track count -->
    <div class="flex items-center gap-2 mb-3 text-sm text-gray-600 dark:text-gray-400">
      <MusicalNoteIcon class="w-4 h-4" />
      <span>
        추천 트랙 <span class="font-semibold text-gray-900 dark:text-gray-100">{{ recommendation.tracks.length }}</span>곡
      </span>
    </div>

    <!-- Selected track info -->
    <div
      v-if="selectedTrack"
      class="flex items-center gap-2 rounded-lg bg-green-50 dark:bg-green-900/20 px-3 py-2 border border-green-200 dark:border-green-800"
    >
      <CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400 flex-shrink-0" />
      <div class="flex-1 min-w-0">
        <p class="text-xs font-medium text-green-800 dark:text-green-300 truncate">
          {{ selectedTrack.title }} - {{ selectedTrack.artist }}
        </p>
      </div>
    </div>

    <div
      v-else
      class="flex items-center gap-2 rounded-lg bg-gray-50 dark:bg-gray-800 px-3 py-2 text-xs text-gray-500 dark:text-gray-400"
    >
      <MusicalNoteIcon class="w-4 h-4" />
      <span>트랙 미선택</span>
    </div>
  </div>
</template>
