<script setup lang="ts">
import { ref, watch } from 'vue'
import { PencilSquareIcon } from '@heroicons/vue/24/outline'
import type { TranslationLine } from '@/types/subtitleTranslation'

const props = defineProps<{
  line: TranslationLine
}>()

const emit = defineEmits<{
  update: [lineId: number, translatedText: string]
}>()

const editText = ref(props.line.translatedText)

watch(
  () => props.line.translatedText,
  (newVal) => {
    editText.value = newVal
  },
)

const handleBlur = () => {
  if (editText.value !== props.line.translatedText) {
    emit('update', props.line.id, editText.value)
  }
}
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-900">
    <div class="mb-2 flex items-center gap-3">
      <!-- Line Number -->
      <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-gray-100 text-xs font-bold text-gray-600 dark:bg-gray-800 dark:text-gray-400">
        {{ line.lineNumber }}
      </span>

      <!-- Timecode -->
      <span class="text-xs font-mono text-gray-500 dark:text-gray-400">
        {{ line.startTime }} ~ {{ line.endTime }}
      </span>

      <!-- Edited indicator -->
      <div v-if="line.isEdited" class="flex items-center gap-1">
        <PencilSquareIcon class="h-3.5 w-3.5 text-blue-500" />
        <span class="text-[10px] font-medium text-blue-500">편집됨</span>
      </div>
    </div>

    <!-- Source Text -->
    <div class="mb-2">
      <p class="text-xs font-medium text-gray-500 dark:text-gray-400">원문</p>
      <p class="mt-0.5 text-sm text-gray-900 dark:text-gray-100">{{ line.sourceText }}</p>
    </div>

    <!-- Translated Text Input -->
    <div>
      <p class="text-xs font-medium text-gray-500 dark:text-gray-400">번역문</p>
      <input
        v-model="editText"
        type="text"
        class="mt-0.5 w-full rounded-lg border border-gray-200 bg-gray-50 px-3 py-1.5 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:bg-white focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500 dark:focus:bg-gray-900"
        @blur="handleBlur"
        @keyup.enter="handleBlur"
      />
    </div>
  </div>
</template>
