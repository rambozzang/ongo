<template>
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
    <button
      v-for="blockType in blockTypes"
      :key="blockType.type"
      class="flex flex-col items-center gap-2 rounded-lg border border-gray-200 p-4 transition-all hover:border-blue-500 hover:bg-blue-50 dark:border-gray-700 dark:hover:border-blue-500 dark:hover:bg-blue-950"
      @click="$emit('select', blockType.type)"
    >
      <span class="text-2xl">{{ blockType.icon }}</span>
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ blockType.label }}</span>
      <span class="text-xs text-gray-500 dark:text-gray-400">{{ blockType.description }}</span>
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
    description: 'URL 버튼',
  },
  {
    type: 'header',
    icon: '📝',
    label: '헤더',
    description: '섹션 제목',
  },
  {
    type: 'social',
    icon: '📱',
    label: 'SNS',
    description: '소셜 미디어',
  },
  {
    type: 'video',
    icon: '🎬',
    label: '영상',
    description: '비디오 카드',
  },
  {
    type: 'divider',
    icon: '➖',
    label: '구분선',
    description: '수평선',
  },
  {
    type: 'text',
    icon: '💬',
    label: '텍스트',
    description: '안내 문구',
  },
]
</script>
