<script setup lang="ts">
import type { VideoForSubtitle } from '@/types/subtitleEditor'

defineProps<{
  videos: VideoForSubtitle[]
  selectedVideo: VideoForSubtitle | null
}>()

const emit = defineEmits<{
  select: [id: number]
  change: []
}>()

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return m + ':' + String(s).padStart(2, '0')
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
    <h3 class="mb-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('subtitleEditor.selectVideo') }}
    </h3>
    <template v-if="!selectedVideo">
      <select
        class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
        @change="emit('select', Number(($event.target as HTMLSelectElement).value))"
      >
        <option value="" selected disabled>{{ $t('subtitleEditor.selectVideoPlaceholder') }}</option>
        <option
          v-for="v in videos"
          :key="v.id"
          :value="v.id"
        >
          {{ v.title }} ({{ formatDuration(v.duration) }})
        </option>
      </select>
      <p v-if="videos.length === 0" class="mt-2 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('subtitleEditor.noVideos') }}
      </p>
    </template>
    <template v-else>
      <div class="flex items-center gap-3">
        <img
          v-if="selectedVideo.thumbnailUrl"
          :src="selectedVideo.thumbnailUrl"
          :alt="selectedVideo.title"
          class="h-12 w-20 flex-shrink-0 rounded object-cover"
        />
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ selectedVideo.title }}
          </p>
          <p class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('subtitleEditor.duration') }}: {{ formatDuration(selectedVideo.duration) }}
            <span v-if="selectedVideo.subtitleCount > 0" class="ml-2">
              | {{ selectedVideo.subtitleCount }} tracks
            </span>
          </p>
        </div>
        <button
          class="btn-secondary flex-shrink-0 text-xs"
          @click="emit('change')"
        >
          {{ $t('subtitleEditor.changeVideo') }}
        </button>
      </div>
    </template>
  </div>
</template>
