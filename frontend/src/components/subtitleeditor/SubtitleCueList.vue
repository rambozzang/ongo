<template>
  <div class="space-y-2">
    <div
      v-if="cues.length === 0"
      class="rounded-lg border border-dashed border-gray-300 bg-gray-50 px-6 py-12 text-center dark:border-gray-600 dark:bg-gray-900"
    >
      <LanguageIcon class="mx-auto mb-2 h-8 w-8 text-gray-400 dark:text-gray-500" />
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ $t('subtitleEditor.noCues') }}
      </p>
    </div>

    <div
      v-for="cue in cues"
      :key="cue.id"
      class="group cursor-pointer rounded-lg border p-3 transition-all"
      :class="selectedCueId === cue.id
        ? 'border-primary-300 bg-primary-50 dark:border-primary-600 dark:bg-primary-900/20'
        : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-gray-600'"
      @click="emit('selectCue', cue.id)"
    >
      <!-- Timestamps Row -->
      <div class="mb-2 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
            {{ formatTime(cue.startTime) }}
          </span>
          <ArrowRightIcon class="h-3 w-3 text-gray-400 dark:text-gray-500" />
          <span class="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
            {{ formatTime(cue.endTime) }}
          </span>
        </div>

        <div class="flex items-center gap-2">
          <!-- Confidence Badge -->
          <span
            class="rounded-full px-2 py-0.5 text-xs font-medium"
            :class="confidenceClass(cue.confidence)"
          >
            {{ confidenceLabel(cue.confidence) }}
          </span>

          <!-- Speaker Badge -->
          <span
            v-if="cue.speaker"
            class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-400"
          >
            {{ $t('subtitleEditor.speaker') }}: {{ cue.speaker }}
          </span>

          <!-- Delete Button -->
          <button
            class="rounded p-1 text-gray-400 opacity-0 transition-all hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:text-gray-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
            :title="$t('subtitleEditor.deleteCue')"
            @click.stop="handleDelete(cue.id)"
          >
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>
      </div>

      <!-- Text Content -->
      <div v-if="editingCueId === cue.id" @click.stop>
        <textarea
          ref="editTextareaRef"
          v-model="editText"
          class="input-field min-h-[60px] resize-y text-sm"
          :placeholder="$t('subtitleEditor.editCueText')"
          @blur="saveEdit(cue.id)"
          @keydown.enter.ctrl="saveEdit(cue.id)"
        />
      </div>
      <p
        v-else
        class="text-sm leading-relaxed text-gray-800 dark:text-gray-200"
        @dblclick.stop="startEdit(cue)"
      >
        {{ cue.text }}
      </p>

      <!-- Translated Text -->
      <p
        v-if="cue.translatedText"
        class="mt-1.5 border-t border-gray-100 pt-1.5 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400"
      >
        <span class="mr-1 font-medium text-primary-600 dark:text-primary-400">{{ $t('subtitleEditor.translated') }}:</span>
        {{ cue.translatedText }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRightIcon, TrashIcon, LanguageIcon } from '@heroicons/vue/24/outline'
import type { SubtitleCue } from '@/types/subtitleEditor'

defineProps<{
  cues: SubtitleCue[]
  selectedCueId: string | null
}>()

const emit = defineEmits<{
  selectCue: [id: string]
  updateCue: [id: string, text: string]
  deleteCue: [id: string]
}>()

const { t } = useI18n({ useScope: 'global' })

const editingCueId = ref<string | null>(null)
const editText = ref('')
const editTextareaRef = ref<HTMLTextAreaElement[]>()

function formatTime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const ms = Math.floor((seconds % 1) * 100)
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(ms).padStart(2, '0')}`
  }
  return `${m}:${String(s).padStart(2, '0')}.${String(ms).padStart(2, '0')}`
}

function confidenceClass(confidence: number): string {
  if (confidence >= 0.8) return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
  if (confidence >= 0.5) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
  return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
}

function confidenceLabel(confidence: number): string {
  if (confidence >= 0.8) return t('subtitleEditor.confidenceHigh')
  if (confidence >= 0.5) return t('subtitleEditor.confidenceMedium')
  return t('subtitleEditor.confidenceLow')
}

function startEdit(cue: SubtitleCue) {
  editingCueId.value = cue.id
  editText.value = cue.text
  nextTick(() => {
    if (editTextareaRef.value?.[0]) {
      editTextareaRef.value[0].focus()
    }
  })
}

function saveEdit(cueId: string) {
  if (editText.value.trim()) {
    emit('updateCue', cueId, editText.value.trim())
  }
  editingCueId.value = null
  editText.value = ''
}

function handleDelete(cueId: string) {
  if (confirm(t('subtitleEditor.deleteCueConfirm'))) {
    emit('deleteCue', cueId)
  }
}
</script>
