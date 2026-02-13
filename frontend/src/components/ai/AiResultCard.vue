<template>
  <div class="relative overflow-hidden rounded-lg border border-l-4 border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm ai-result-gradient">
    <!-- Loading state -->
    <AiLoadingOverlay v-if="loading" :visible="loading" />

    <!-- Results -->
    <div v-else class="p-6">
      <TransitionGroup name="ai-item" tag="div" class="space-y-4">
        <div
          v-for="(item, index) in items"
          :key="index"
          :style="{ '--stagger-delay': `${index * 150}ms` }"
          class="ai-result-item"
        >
          <label class="mb-1.5 block text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ item.label }}
          </label>

          <!-- Text type (with typing effect for first item) -->
          <div v-if="item.type === 'text' || !item.type" class="text-sm text-gray-800 dark:text-gray-200">
            <AiTypingEffect v-if="index === 0" :text="item.value" :speed="20" />
            <p v-else>{{ item.value }}</p>
          </div>

          <!-- Tag type -->
          <div v-else-if="item.type === 'tag'" class="flex flex-wrap gap-1.5">
            <span
              v-for="tag in item.value.split(',')"
              :key="tag.trim()"
              class="badge-blue"
            >
              #{{ tag.trim() }}
            </span>
          </div>

          <!-- List type -->
          <ol v-else-if="item.type === 'list'" class="list-decimal list-inside space-y-1 text-sm text-gray-800 dark:text-gray-200">
            <li v-for="(line, li) in item.value.split('\n').filter(l => l.trim())" :key="li">
              {{ line.trim() }}
            </li>
          </ol>
        </div>
      </TransitionGroup>

      <!-- Action bar -->
      <div class="mt-6 flex items-center justify-between border-t border-gray-200 dark:border-gray-700 pt-4">
        <div class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
          <SparklesIcon class="h-4 w-4 text-primary-500" />
          <span v-if="creditCost">{{ creditCost }} 크레딧 사용</span>
        </div>

        <div class="flex items-center gap-3">
          <button
            class="btn-secondary inline-flex items-center gap-1.5 text-sm"
            @click="onRegenerate"
          >
            <SparklesIcon class="h-4 w-4" />
            재생성
          </button>
          <button
            class="btn-primary inline-flex items-center gap-1.5 text-sm"
            @click="onApply"
          >
            적용하기
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SparklesIcon } from '@heroicons/vue/24/outline'
import AiLoadingOverlay from './AiLoadingOverlay.vue'
import AiTypingEffect from './AiTypingEffect.vue'

interface ResultItem {
  label: string
  value: string
  type?: 'text' | 'tag' | 'list'
}

defineProps<{
  items: ResultItem[]
  loading?: boolean
  creditCost?: number
}>()

const emit = defineEmits<{
  apply: []
  regenerate: []
}>()

function onApply() {
  emit('apply')
}

function onRegenerate() {
  emit('regenerate')
}
</script>

<style scoped>
/* Gradient border accent */
.ai-result-gradient {
  border-left-color: transparent;
  background-image: linear-gradient(to right, white, white),
    linear-gradient(to bottom, #7c3aed, #8b5cf6, #a78bfa);
  background-origin: border-box;
  background-clip: padding-box, border-box;
}

.dark .ai-result-gradient {
  background-image: linear-gradient(to right, rgb(31 41 55), rgb(31 41 55)),
    linear-gradient(to bottom, #7c3aed, #8b5cf6, #a78bfa);
}

/* Item stagger animations */
.ai-item-enter-active {
  transition: all 500ms ease-out;
  transition-delay: var(--stagger-delay, 0ms);
}

.ai-item-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.ai-result-item {
  animation: ai-item-fade-in 500ms ease-out backwards;
  animation-delay: var(--stagger-delay, 0ms);
}

@keyframes ai-item-fade-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
