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
import { computed } from 'vue'
import { PERSISTED_BLOCK_TYPES, type BlockType } from '@/types/linkbio'

interface BlockTypeOption {
  type: BlockType
  icon: string
  label: string
  description: string
}

defineEmits<{
  select: [type: BlockType]
}>()

/**
 * 블록 종류별 표시 정보. **여기에 있다고 고를 수 있는 것은 아니다.**
 *
 * 실제 노출은 [PERSISTED_BLOCK_TYPES] 로 거른다 — 서버가 저장하지 않는 종류를 고르게
 * 하면 에디터에서는 성공한 것처럼 보이다가 새로고침·공개 페이지에서 사라진다.
 * 목록을 여기서 따로 관리하면 그 규칙과 갈라지므로 단일 출처를 쓴다.
 */
const ALL_BLOCK_TYPES: BlockTypeOption[] = [
  {
    type: 'link',
    icon: '🔗',
    label: '링크',
    description: '저장되는 URL 버튼',
  },
]

const blockTypes = computed(() =>
  ALL_BLOCK_TYPES.filter(option => (PERSISTED_BLOCK_TYPES as readonly string[]).includes(option.type)),
)
</script>
