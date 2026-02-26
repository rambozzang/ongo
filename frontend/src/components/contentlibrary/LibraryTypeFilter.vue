<script setup lang="ts">
import {
  Squares2X2Icon,
  FilmIcon,
  PhotoIcon,
  MusicalNoteIcon,
  DocumentIcon,
} from '@heroicons/vue/24/outline'

type FilterType = 'ALL' | 'VIDEO' | 'IMAGE' | 'AUDIO' | 'DOCUMENT'

defineProps<{
  modelValue: FilterType
}>()

defineEmits<{
  'update:modelValue': [value: FilterType]
}>()

const filterTabs: { key: FilterType; label: string; icon: typeof FilmIcon }[] = [
  { key: 'ALL', label: '전체', icon: Squares2X2Icon },
  { key: 'VIDEO', label: '영상', icon: FilmIcon },
  { key: 'IMAGE', label: '이미지', icon: PhotoIcon },
  { key: 'AUDIO', label: '오디오', icon: MusicalNoteIcon },
  { key: 'DOCUMENT', label: '문서', icon: DocumentIcon },
]
</script>

<template>
  <div class="flex gap-2 overflow-x-auto">
    <button
      v-for="tab in filterTabs"
      :key="tab.key"
      class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition-colors"
      :class="
        modelValue === tab.key
          ? 'bg-primary-600 text-white'
          : 'bg-white text-gray-700 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
      "
      @click="$emit('update:modelValue', tab.key)"
    >
      <component :is="tab.icon" class="h-4 w-4" />
      {{ tab.label }}
    </button>
  </div>
</template>
