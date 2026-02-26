<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  moods: string[]
}

interface Emits {
  (e: 'select', mood: string): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const selectedMood = ref<string | null>(null)

function handleSelect(mood: string) {
  if (selectedMood.value === mood) {
    selectedMood.value = null
  } else {
    selectedMood.value = mood
  }
  emit('select', selectedMood.value ?? '')
}

const moodColorMap: Record<string, string> = {
  '밝은': 'bg-yellow-100 text-yellow-800 border-yellow-300 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-700',
  '편안한': 'bg-green-100 text-green-800 border-green-300 dark:bg-green-900/30 dark:text-green-300 dark:border-green-700',
  '집중': 'bg-blue-100 text-blue-800 border-blue-300 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-700',
  '에너지': 'bg-red-100 text-red-800 border-red-300 dark:bg-red-900/30 dark:text-red-300 dark:border-red-700',
  '차분한': 'bg-indigo-100 text-indigo-800 border-indigo-300 dark:bg-indigo-900/30 dark:text-indigo-300 dark:border-indigo-700',
  '드라마틱': 'bg-purple-100 text-purple-800 border-purple-300 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-700',
  '활기찬': 'bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-900/30 dark:text-orange-300 dark:border-orange-700',
  '감성적': 'bg-pink-100 text-pink-800 border-pink-300 dark:bg-pink-900/30 dark:text-pink-300 dark:border-pink-700',
  '로맨틱': 'bg-rose-100 text-rose-800 border-rose-300 dark:bg-rose-900/30 dark:text-rose-300 dark:border-rose-700',
  '웅장한': 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-900/30 dark:text-amber-300 dark:border-amber-700',
}

function getMoodColor(mood: string): string {
  return moodColorMap[mood] ?? 'bg-gray-100 text-gray-800 border-gray-300 dark:bg-gray-700 dark:text-gray-300 dark:border-gray-600'
}
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <button
      v-for="mood in moods"
      :key="mood"
      type="button"
      @click="handleSelect(mood)"
      :class="[
        'inline-flex items-center px-3 py-1.5 rounded-full text-sm font-medium border transition-all duration-200',
        selectedMood === mood
          ? getMoodColor(mood) + ' ring-2 ring-offset-1 ring-primary-500 dark:ring-offset-gray-900'
          : getMoodColor(mood) + ' opacity-70 hover:opacity-100',
      ]"
    >
      {{ mood }}
    </button>
  </div>
</template>
