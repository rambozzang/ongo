<template>
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
    <button
      v-for="blockType in blockTypes"
      :key="blockType.type"
      class="flex flex-col items-center gap-2 rounded-lg border border-gray-200 p-4 transition-all hover:border-primary-500 hover:bg-primary-50 dark:border-gray-700 dark:hover:bg-primary-900/20"
      @click="$emit('select', blockType.type)"
    >
      <span class="text-h1">{{ blockType.icon }}</span>
      <span class="text-body font-medium text-gray-900 dark:text-gray-100">{{ blockType.label }}</span>
      <span class="text-body-xs text-gray-500 dark:text-gray-400">{{ blockType.description }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { BlockType } from '@/types/linkbio'

interface BlockTypeOption {
  type: BlockType
  icon: string
  label: string
  description: string
}

defineEmits<{
  select: [type: BlockType]
}>()

const blockTypes: BlockTypeOption[] = [
  {
    type: 'link',
    icon: '🔗',
    label: '링크',
    description: '저장되는 URL 버튼',
  },
]
</script>
