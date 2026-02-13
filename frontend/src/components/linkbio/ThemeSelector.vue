<template>
  <div class="flex gap-3 overflow-x-auto pb-2">
    <button
      v-for="theme in themes"
      :key="theme.name"
      @click="$emit('select', theme.name)"
      :class="[
        'flex flex-col items-center gap-2 rounded-lg border-2 p-3 transition-all',
        selectedTheme === theme.name
          ? 'border-blue-500 bg-blue-50 dark:bg-blue-950'
          : 'border-gray-200 hover:border-gray-300 dark:border-gray-700 dark:hover:border-gray-600',
      ]"
    >
      <div
        class="h-16 w-24 rounded-md border border-gray-300 dark:border-gray-600"
        :style="{ background: theme.preview }"
      />
      <span class="text-xs font-medium text-gray-900 dark:text-gray-100">{{ theme.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { ThemeStyle } from '@/types/linkbio'

interface ThemeOption {
  name: ThemeStyle
  label: string
  preview: string
}

defineProps<{
  selectedTheme: ThemeStyle
}>()

defineEmits<{
  select: [theme: ThemeStyle]
}>()

const themes: ThemeOption[] = [
  {
    name: 'minimal',
    label: '미니멀',
    preview: 'linear-gradient(180deg, #ffffff 0%, #f9fafb 100%)',
  },
  {
    name: 'rounded',
    label: '둥근',
    preview: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  },
  {
    name: 'gradient',
    label: '그라데이션',
    preview: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  },
  {
    name: 'dark',
    label: '다크',
    preview: 'linear-gradient(180deg, #1a1a1a 0%, #2d2d2d 100%)',
  },
  {
    name: 'colorful',
    label: '컬러풀',
    preview: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
  },
]
</script>
