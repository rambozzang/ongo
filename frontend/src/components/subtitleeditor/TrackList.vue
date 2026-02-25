<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { TrashIcon } from '@heroicons/vue/24/outline'
import type { SubtitleTrack } from '@/types/subtitleEditor'

defineProps<{
  tracks: SubtitleTrack[]
  selectedTrack: SubtitleTrack | null
}>()

const emit = defineEmits<{
  select: [track: SubtitleTrack]
  delete: [trackId: number]
}>()

const { t } = useI18n({ useScope: 'global' })

function statusClass(status: string): string {
  const map: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
    GENERATING: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    READY: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    EXPORTED: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  }
  return map[status] || map.DRAFT
}

function handleDelete(trackId: number) {
  if (confirm(t('subtitleEditor.deleteTrackConfirm'))) {
    emit('delete', trackId)
  }
}
</script>

<template>
  <div class="space-y-2">
    <div
      v-if="tracks.length === 0"
      class="rounded-lg border border-dashed border-gray-300 bg-gray-50 px-4 py-6 text-center dark:border-gray-600 dark:bg-gray-900"
    >
      <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('subtitleEditor.noTracks') }}</p>
    </div>
    <div
      v-for="track in tracks"
      :key="track.id"
      class="group cursor-pointer rounded-lg border p-3 transition-all"
      :class="selectedTrack?.id === track.id
        ? 'border-primary-300 bg-primary-50 dark:border-primary-600 dark:bg-primary-900/20'
        : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-gray-600'"
      @click="emit('select', track)"
    >
      <div class="flex items-center justify-between">
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ $t('subtitleEditor.generate.languageOptions.' + track.language) }}
            </span>
            <span
              class="rounded-full px-2 py-0.5 text-xs font-medium"
              :class="statusClass(track.status)"
            >
              {{ $t('subtitleEditor.status.' + track.status) }}
            </span>
          </div>
          <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            {{ track.cues.length }} cues | {{ track.wordCount }} words
          </p>
        </div>
        <button
          class="rounded p-1 text-gray-400 opacity-0 transition-all hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:text-gray-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
          :title="$t('subtitleEditor.deleteTrack')"
          @click.stop="handleDelete(track.id)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>
</template>
