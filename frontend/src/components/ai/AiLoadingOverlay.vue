<template>
  <Transition name="ai-overlay">
    <div v-if="visible" class="absolute inset-0 z-10 flex items-center justify-center bg-white/90 dark:bg-gray-800/90 backdrop-blur-sm">
      <div class="flex flex-col items-center gap-4">
        <!-- AI Icon with glow animation -->
        <div class="relative">
          <SparklesIcon
            class="h-12 w-12 text-primary-600 dark:text-primary-400"
            :class="stage === 'complete' ? 'ai-icon-complete' : 'ai-icon-glow'"
          />

          <!-- Completion particles -->
          <div v-if="stage === 'complete'" class="absolute inset-0">
            <span class="sparkle sparkle-1" />
            <span class="sparkle sparkle-2" />
            <span class="sparkle sparkle-3" />
            <span class="sparkle sparkle-4" />
          </div>
        </div>

        <!-- Stage message -->
        <div class="text-center">
          <p class="text-lg font-medium text-gray-900 dark:text-gray-100">
            {{ stageMessage }}
          </p>

          <!-- Loading dots (hide when complete) -->
          <div v-if="stage !== 'complete'" class="mt-3 flex items-center justify-center gap-1.5">
            <span class="dot dot-1" />
            <span class="dot dot-2" />
            <span class="dot dot-3" />
          </div>

          <!-- Checkmark (show when complete) -->
          <div v-else class="mt-3 flex items-center justify-center">
            <svg class="h-8 w-8 text-green-500" viewBox="0 0 24 24" fill="none">
              <path
                class="check-path"
                d="M5 13l4 4L19 7"
                stroke="currentColor"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SparklesIcon } from '@heroicons/vue/24/outline'

type Stage = 'analyzing' | 'generating' | 'optimizing' | 'complete'
type Type = 'title' | 'description' | 'tags' | 'hashtags' | 'script' | 'stt' | 'time' | 'insight'

const props = withDefaults(
  defineProps<{
    visible: boolean
    stage?: Stage
    type?: Type
  }>(),
  {
    stage: 'analyzing',
    type: 'title',
  }
)

const stageMessage = computed(() => {
  if (props.stage === 'analyzing') {
    return 'AI가 분석 중입니다...'
  }

  if (props.stage === 'generating') {
    const messages: Record<Type, string> = {
      title: '제목을 생성하고 있습니다...',
      description: '설명을 작성하고 있습니다...',
      tags: '태그를 최적화하고 있습니다...',
      hashtags: '해시태그를 생성하고 있습니다...',
      script: '스크립트를 분석하고 있습니다...',
      stt: '음성을 인식하고 있습니다...',
      time: '최적 시간을 계산하고 있습니다...',
      insight: '인사이트를 도출하고 있습니다...',
    }
    return messages[props.type]
  }

  if (props.stage === 'optimizing') {
    return '결과를 최적화하고 있습니다...'
  }

  return '완료!'
})
</script>

<style scoped>
/* Overlay transitions */
.ai-overlay-enter-active,
.ai-overlay-leave-active {
  transition: opacity 300ms ease;
}

.ai-overlay-enter-from,
.ai-overlay-leave-to {
  opacity: 0;
}

/* AI icon animations */
.ai-icon-glow {
  animation: ai-glow 2s ease-in-out infinite, ai-pulse 2s ease-in-out infinite;
}

.ai-icon-complete {
  animation: ai-complete-bounce 600ms cubic-bezier(0.68, -0.55, 0.265, 1.55);
}

@keyframes ai-glow {
  0%, 100% {
    filter: drop-shadow(0 0 8px rgba(124, 58, 237, 0.5));
  }
  33% {
    filter: drop-shadow(0 0 12px rgba(139, 92, 246, 0.6));
  }
  66% {
    filter: drop-shadow(0 0 10px rgba(217, 70, 239, 0.5));
  }
}

@keyframes ai-pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

@keyframes ai-complete-bounce {
  0% {
    transform: scale(0.3);
    opacity: 0;
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

/* Loading dots */
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: currentColor;
  @apply text-primary-600 dark:text-primary-400;
  animation: dot-bounce 1.4s ease-in-out infinite;
}

.dot-1 {
  animation-delay: 0ms;
}

.dot-2 {
  animation-delay: 200ms;
}

.dot-3 {
  animation-delay: 400ms;
}

@keyframes dot-bounce {
  0%, 80%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  40% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* Checkmark animation */
.check-path {
  stroke-dasharray: 20;
  stroke-dashoffset: 20;
  animation: check-draw 600ms ease-out forwards;
}

@keyframes check-draw {
  to {
    stroke-dashoffset: 0;
  }
}

/* Completion sparkles */
.sparkle {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  @apply bg-primary-500;
  animation: sparkle 800ms ease-out forwards;
}

.sparkle-1 {
  top: -4px;
  right: 8px;
  animation-delay: 0ms;
}

.sparkle-2 {
  top: 8px;
  right: -4px;
  animation-delay: 100ms;
}

.sparkle-3 {
  bottom: -4px;
  right: 8px;
  animation-delay: 200ms;
}

.sparkle-4 {
  top: 8px;
  left: -4px;
  animation-delay: 150ms;
}

@keyframes sparkle {
  0% {
    transform: scale(0) translateY(0);
    opacity: 1;
  }
  50% {
    opacity: 1;
  }
  100% {
    transform: scale(1.5) translateY(-20px);
    opacity: 0;
  }
}
</style>
