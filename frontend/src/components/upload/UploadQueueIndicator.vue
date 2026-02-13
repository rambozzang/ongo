<template>
  <Transition name="fade-slide">
    <button
      v-if="queueStore.queue.length > 0"
      class="fixed bottom-20 left-4 z-40 flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-lg transition-all hover:scale-105 hover:shadow-xl dark:border-gray-700 dark:bg-gray-800 tablet:bottom-6"
      :class="{ 'animate-pulse-subtle': queueStore.uploadingCount > 0 }"
      @click="emit('open')"
    >
      <!-- Progress circle -->
      <div class="relative h-10 w-10 flex-shrink-0">
        <!-- Background circle -->
        <svg class="h-10 w-10 -rotate-90 transform">
          <circle
            cx="20"
            cy="20"
            r="16"
            stroke="currentColor"
            stroke-width="3"
            fill="none"
            class="text-gray-200 dark:text-gray-700"
          />
          <!-- Progress circle -->
          <circle
            cx="20"
            cy="20"
            r="16"
            stroke="currentColor"
            stroke-width="3"
            fill="none"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="progressOffset"
            class="transition-all duration-300"
            :class="progressColor"
            stroke-linecap="round"
          />
        </svg>
        <!-- Center icon/percentage -->
        <div class="absolute inset-0 flex items-center justify-center">
          <span v-if="queueStore.uploadingCount > 0" class="text-xs font-bold text-primary-600 dark:text-primary-400">
            {{ queueStore.overallProgress }}
          </span>
          <QueueListIcon v-else class="h-5 w-5 text-gray-600 dark:text-gray-400" />
        </div>
      </div>

      <!-- Text info -->
      <div class="min-w-0 flex-1 text-left">
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ statusText }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400">
          {{ queueStore.queue.length }}개 항목
        </p>
      </div>

      <!-- Status indicator -->
      <div v-if="queueStore.uploadingCount > 0" class="flex-shrink-0">
        <span class="relative flex h-3 w-3">
          <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary-400 opacity-75"></span>
          <span class="relative inline-flex h-3 w-3 rounded-full bg-primary-500"></span>
        </span>
      </div>
    </button>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { QueueListIcon } from '@heroicons/vue/24/outline'
import { useUploadQueueStore } from '@/stores/uploadQueue'

const emit = defineEmits<{
  open: []
}>()

const queueStore = useUploadQueueStore()

const circumference = 2 * Math.PI * 16 // 2πr where r=16

const progressOffset = computed(() => {
  const progress = queueStore.overallProgress
  return circumference - (progress / 100) * circumference
})

const progressColor = computed(() => {
  if (queueStore.failedCount > 0) {
    return 'text-red-500'
  } else if (queueStore.uploadingCount > 0) {
    return 'text-primary-500'
  } else if (queueStore.completedCount > 0 && queueStore.queuedCount === 0) {
    return 'text-green-500'
  }
  return 'text-gray-400 dark:text-gray-500'
})

const statusText = computed(() => {
  if (queueStore.uploadingCount > 0) {
    return `${queueStore.uploadingCount}개 업로드 중`
  } else if (queueStore.queuedCount > 0) {
    return `${queueStore.queuedCount}개 대기 중`
  } else if (queueStore.failedCount > 0) {
    return `${queueStore.failedCount}개 실패`
  } else if (queueStore.completedCount > 0) {
    return '모두 완료'
  }
  return '업로드 큐'
})
</script>

<style scoped>
/* Fade slide transition */
.fade-slide-enter-active {
  transition:
    opacity 300ms ease-out,
    transform 300ms ease-out;
}
.fade-slide-leave-active {
  transition:
    opacity 200ms ease-in,
    transform 200ms ease-in;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* Subtle pulse animation */
@keyframes pulse-subtle {
  0%,
  100% {
    box-shadow:
      0 10px 15px -3px rgb(0 0 0 / 0.1),
      0 4px 6px -4px rgb(0 0 0 / 0.1);
  }
  50% {
    box-shadow:
      0 20px 25px -5px rgb(0 0 0 / 0.1),
      0 8px 10px -6px rgb(0 0 0 / 0.1);
  }
}

.animate-pulse-subtle {
  animation: pulse-subtle 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

/* Accessibility: Respect user's motion preferences */
@media (prefers-reduced-motion: reduce) {
  .fade-slide-enter-active,
  .fade-slide-leave-active {
    transition: none !important;
  }
  .fade-slide-enter-from,
  .fade-slide-leave-to {
    transform: none !important;
  }
  .animate-pulse-subtle {
    animation: none !important;
  }
}
</style>
