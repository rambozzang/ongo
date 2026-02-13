<script setup lang="ts">
import { ref, watch } from 'vue'
import { CheckIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const localValue = ref(props.modelValue)
const isPreview = ref(false)
const isSaving = ref(false)
const lastSaved = ref<Date | null>(null)

const characterCount = ref(props.modelValue.length)

watch(() => props.modelValue, (newValue) => {
  localValue.value = newValue
  characterCount.value = newValue.length
})

watch(localValue, (newValue) => {
  characterCount.value = newValue.length
  emit('update:modelValue', newValue)
  autoSave()
})

let autoSaveTimeout: ReturnType<typeof setTimeout> | null = null

function autoSave() {
  if (autoSaveTimeout) {
    clearTimeout(autoSaveTimeout)
  }

  isSaving.value = true
  autoSaveTimeout = setTimeout(() => {
    lastSaved.value = new Date()
    isSaving.value = false
  }, 1000)
}

function formatLastSaved() {
  if (!lastSaved.value) return ''
  const now = new Date()
  const diff = Math.floor((now.getTime() - lastSaved.value.getTime()) / 1000)

  if (diff < 60) return '방금 저장됨'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전 저장됨`
  return lastSaved.value.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

// Simple markdown-like parser for preview
function parseMarkdown(text: string): string {
  return text
    .replace(/^# (.+)$/gm, '<h1 class="text-2xl font-bold mb-4 text-gray-900 dark:text-gray-100">$1</h1>')
    .replace(/^## (.+)$/gm, '<h2 class="text-xl font-semibold mb-3 text-gray-900 dark:text-gray-100">$1</h2>')
    .replace(/^### (.+)$/gm, '<h3 class="text-lg font-medium mb-2 text-gray-900 dark:text-gray-100">$1</h3>')
    .replace(/\*\*(.+?)\*\*/g, '<strong class="font-semibold">$1</strong>')
    .replace(/\*(.+?)\*/g, '<em class="italic">$1</em>')
    .replace(/^- (.+)$/gm, '<li class="ml-4">$1</li>')
    .replace(/(<li.*<\/li>)/s, '<ul class="list-disc mb-2 text-gray-700 dark:text-gray-300">$1</ul>')
    .replace(/\n\n/g, '<br/><br/>')
    .replace(/^(?!<[h|u|l])(.*?)$/gm, '<p class="mb-2 text-gray-700 dark:text-gray-300">$1</p>')
}
</script>

<template>
  <div class="space-y-4">
    <!-- Toolbar -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button
          @click="isPreview = false"
          :class="[
            'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
            !isPreview
              ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
          ]"
        >
          편집
        </button>
        <button
          @click="isPreview = true"
          :class="[
            'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
            isPreview
              ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
              : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
          ]"
        >
          미리보기
        </button>
      </div>
      <div class="flex items-center gap-2">
        <span
          v-if="isSaving"
          class="text-sm text-gray-500 dark:text-gray-400 flex items-center gap-1"
        >
          <svg class="animate-spin h-4 w-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          <span>저장 중...</span>
        </span>
        <span
          v-else-if="lastSaved"
          class="text-sm text-green-600 dark:text-green-400 flex items-center gap-1"
        >
          <CheckIcon class="w-4 h-4" />
          <span>{{ formatLastSaved() }}</span>
        </span>
      </div>
    </div>

    <!-- Editor / Preview -->
    <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
      <template v-if="!isPreview">
        <textarea
          v-model="localValue"
          class="w-full h-[500px] p-4 bg-transparent text-gray-900 dark:text-gray-100 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 rounded-lg font-mono text-sm"
          placeholder="# 브랜드 가이드라인을 작성하세요

## 색상 사용 원칙
- 메인 브랜드 컬러는 썸네일과 주요 CTA에 사용
- 배경색은 밝은 테마에서만 사용

## 서체 사용 규칙
- 제목: **Bold** 사용
- 본문: *Regular* 사용

**Markdown 문법 지원:**
- # 제목 1
- ## 제목 2
- ### 제목 3
- **굵게**
- *기울임*
- - 목록"
        ></textarea>
      </template>
      <template v-else>
        <div
          class="p-4 h-[500px] overflow-y-auto prose dark:prose-invert max-w-none"
          v-html="parseMarkdown(localValue)"
        ></div>
      </template>
    </div>

    <!-- Footer Info -->
    <div class="flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
      <div class="flex items-center gap-4">
        <span>문자 수: {{ characterCount.toLocaleString() }}</span>
      </div>
      <div class="text-xs">
        마크다운 문법 사용 가능: # 제목, **굵게**, *기울임*, - 목록
      </div>
    </div>
  </div>
</template>
