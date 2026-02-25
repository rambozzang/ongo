<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
    <h4 class="mb-3 text-xs font-semibold text-gray-700 dark:text-gray-300">
      {{ $t('subtitleEditor.timeline') }}
    </h4>

    <!-- Timeline Bar -->
    <div
      ref="timelineRef"
      class="relative h-10 cursor-pointer rounded-lg bg-gray-100 dark:bg-gray-700"
      @click="handleTimelineClick"
    >
      <!-- Cue Blocks -->
      <div
        v-for="cue in cues"
        :key="cue.id"
        class="absolute top-1 h-8 rounded transition-all"
        :class="[
          confidenceColor(cue.confidence),
          selectedCueId === cue.id ? 'ring-2 ring-primary-500 ring-offset-1 dark:ring-offset-gray-800' : 'hover:opacity-80',
        ]"
        :style="{
          left: `${cueLeft(cue)}%`,
          width: `${cueWidth(cue)}%`,
          minWidth: '4px',
        }"
        :title="`${formatTime(cue.startTime)} - ${formatTime(cue.endTime)}: ${cue.text.substring(0, 40)}`"
        @click.stop="emit('selectCue', cue.id)"
      />
    </div>

    <!-- Time Ruler -->
    <div class="mt-1 flex justify-between">
      <span class="text-xs text-gray-400 dark:text-gray-500">0:00</span>
      <span class="text-xs text-gray-400 dark:text-gray-500">{{ formatTime(totalDuration * 0.25) }}</span>
      <span class="text-xs text-gray-400 dark:text-gray-500">{{ formatTime(totalDuration * 0.5) }}</span>
      <span class="text-xs text-gray-400 dark:text-gray-500">{{ formatTime(totalDuration * 0.75) }}</span>
      <span class="text-xs text-gray-400 dark:text-gray-500">{{ formatTime(totalDuration) }}</span>
    </div>

    <!-- Legend -->
    <div class="mt-2 flex items-center gap-4">
      <div class="flex items-center gap-1.5">
        <span class="h-2.5 w-2.5 rounded-sm bg-green-500" />
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('subtitleEditor.confidenceHigh') }}</span>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="h-2.5 w-2.5 rounded-sm bg-yellow-500" />
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('subtitleEditor.confidenceMedium') }}</span>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="h-2.5 w-2.5 rounded-sm bg-red-500" />
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('subtitleEditor.confidenceLow') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { SubtitleCue } from '@/types/subtitleEditor'

const props = defineProps<{
  cues: SubtitleCue[]
  totalDuration: number
  selectedCueId: string | null
}>()

const emit = defineEmits<{
  selectCue: [id: string]
}>()

useI18n({ useScope: 'global' })

const timelineRef = ref<HTMLDivElement>()

function cueLeft(cue: SubtitleCue): number {
  if (props.totalDuration <= 0) return 0
  return (cue.startTime / props.totalDuration) * 100
}

function cueWidth(cue: SubtitleCue): number {
  if (props.totalDuration <= 0) return 0
  return ((cue.endTime - cue.startTime) / props.totalDuration) * 100
}

function confidenceColor(confidence: number): string {
  if (confidence >= 0.8) return 'bg-green-500/70 dark:bg-green-500/50'
  if (confidence >= 0.5) return 'bg-yellow-500/70 dark:bg-yellow-500/50'
  return 'bg-red-500/70 dark:bg-red-500/50'
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

function handleTimelineClick(event: MouseEvent) {
  if (!timelineRef.value || props.cues.length === 0 || props.totalDuration <= 0) return

  const rect = timelineRef.value.getBoundingClientRect()
  const clickX = event.clientX - rect.left
  const clickPercent = clickX / rect.width
  const clickTime = clickPercent * props.totalDuration

  // Find the closest cue to the clicked time
  let closestCue: SubtitleCue | null = null
  let closestDistance = Infinity

  for (const cue of props.cues) {
    const mid = (cue.startTime + cue.endTime) / 2
    const dist = Math.abs(mid - clickTime)
    if (dist < closestDistance) {
      closestDistance = dist
      closestCue = cue
    }
  }

  if (closestCue) {
    emit('selectCue', closestCue.id)
  }
}
</script>
