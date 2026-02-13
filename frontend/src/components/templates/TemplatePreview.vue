<script setup lang="ts">
import { computed } from 'vue'
import type { TemplateCategory } from '@/types/template'

interface Props {
  content: string
  category: TemplateCategory
  platform?: string
}

const props = defineProps<Props>()

const platformHints: Record<string, string> = {
  YOUTUBE: '최대 100자 (제목), 5000자 (설명)',
  TIKTOK: '최대 150자',
  INSTAGRAM: '최대 2200자',
  NAVER_CLIP: '최대 1000자',
  ALL: '플랫폼별 제한사항을 확인하세요',
}

const categoryLabels: Record<TemplateCategory, string> = {
  title: '제목 미리보기',
  description: '설명 미리보기',
  tags: '태그 미리보기',
  thumbnail: '썸네일 스타일',
  full: '풀 패키지 미리보기',
}

// Highlight variables in content
const highlightedContent = computed(() => {
  if (!props.content) return ''

  // Replace {{variable}} with highlighted version
  return props.content.replace(/\{\{([^}]+)\}\}/g, (match) => {
    return `<span class="variable-highlight">${match}</span>`
  })
})

const hint = computed(() => {
  if (props.platform) {
    return platformHints[props.platform] || ''
  }
  return ''
})
</script>

<template>
  <div class="bg-gray-50 dark:bg-gray-900 rounded-lg p-4 border border-gray-200 dark:border-gray-700">
    <div class="flex items-center justify-between mb-3">
      <h4 class="text-sm font-semibold text-gray-900 dark:text-white">
        {{ categoryLabels[category] }}
      </h4>
      <span v-if="hint" class="text-xs text-gray-500 dark:text-gray-400">
        {{ hint }}
      </span>
    </div>

    <div
      v-if="content"
      class="prose prose-sm dark:prose-invert max-w-none"
    >
      <div
        v-if="category === 'tags'"
        class="flex flex-wrap gap-2"
      >
        <span
          v-for="(tag, index) in content.split(',')"
          :key="index"
          class="px-2 py-1 text-sm bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 rounded"
          v-html="tag.trim().replace(/\{\{([^}]+)\}\}/g, '<span class=\'variable-highlight\'>{{$1}}</span>')"
        />
      </div>
      <div
        v-else
        class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap"
        v-html="highlightedContent"
      />
    </div>

    <div
      v-else
      class="text-sm text-gray-400 dark:text-gray-500 italic"
    >
      내용을 입력하면 여기에 미리보기가 표시됩니다
    </div>
  </div>
</template>

<style scoped>
:deep(.variable-highlight) {
  @apply bg-blue-200 dark:bg-blue-800 text-blue-700 dark:text-blue-300 px-1 rounded font-semibold;
}
</style>
