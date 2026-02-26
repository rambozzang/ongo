<script setup lang="ts">
import { computed } from 'vue'
import {
  MusicalNoteIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { MusicTrack } from '@/types/musicRecommender'

interface Props {
  track: MusicTrack
}

const props = defineProps<Props>()

const licenseConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    FREE: {
      label: 'Free',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    STANDARD: {
      label: 'Standard',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    PREMIUM: {
      label: 'Premium',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
    },
  }
  return configs[props.track.licenseType] ?? configs.FREE
})

function formatDuration(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return `${min}:${String(sec).padStart(2, '0')}`
}
</script>

<template>
  <div
    class="flex items-center gap-4 rounded-lg border border-gray-200 bg-white p-3 shadow-sm hover:shadow-md dark:border-gray-700 dark:bg-gray-900 transition-all duration-200"
  >
    <!-- Track icon -->
    <div class="flex-shrink-0 w-10 h-10 rounded-full bg-primary-100 dark:bg-primary-900/30 flex items-center justify-center">
      <MusicalNoteIcon class="w-5 h-5 text-primary-600 dark:text-primary-400" />
    </div>

    <!-- Title & Artist -->
    <div class="flex-1 min-w-0">
      <p class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {{ track.title }}
      </p>
      <p class="text-xs text-gray-500 dark:text-gray-400 truncate">
        {{ track.artist }}
      </p>
    </div>

    <!-- Genre tag -->
    <span
      class="hidden tablet:inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300"
    >
      {{ track.genre }}
    </span>

    <!-- Mood badge -->
    <span
      class="hidden tablet:inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300"
    >
      {{ track.mood }}
    </span>

    <!-- BPM -->
    <div class="hidden desktop:flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
      <span class="font-medium text-gray-700 dark:text-gray-300">{{ track.bpm }}</span>
      <span>BPM</span>
    </div>

    <!-- Duration -->
    <div class="hidden desktop:flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
      <ClockIcon class="w-3.5 h-3.5" />
      {{ formatDuration(track.duration) }}
    </div>

    <!-- License type -->
    <span
      :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium flex-shrink-0', licenseConfig.color]"
    >
      {{ licenseConfig.label }}
    </span>

    <!-- Match score progress -->
    <div class="flex items-center gap-2 flex-shrink-0 w-24">
      <div class="flex-1 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full rounded-full transition-all duration-500"
          :class="
            track.matchScore >= 90
              ? 'bg-green-500 dark:bg-green-400'
              : track.matchScore >= 70
                ? 'bg-blue-500 dark:bg-blue-400'
                : 'bg-yellow-500 dark:bg-yellow-400'
          "
          :style="{ width: `${track.matchScore}%` }"
        ></div>
      </div>
      <span class="text-xs font-semibold text-gray-700 dark:text-gray-300 w-8 text-right">
        {{ track.matchScore }}%
      </span>
    </div>
  </div>
</template>
