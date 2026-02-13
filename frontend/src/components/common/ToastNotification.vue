<template>
  <Teleport to="body">
    <!-- Desktop: bottom-right, Mobile: bottom-center -->
    <div
      class="pointer-events-none fixed bottom-4 right-4 z-[100] flex flex-col-reverse gap-3 md:bottom-6 md:right-6"
    >
      <TransitionGroup
        name="toast"
        tag="div"
        class="flex flex-col-reverse gap-3"
      >
        <div
          v-for="(toast, index) in toasts"
          :key="toast.id"
          role="alert"
          :aria-label="getToastAriaLabel(toast)"
          class="toast-item pointer-events-auto relative w-[calc(100vw-2rem)] overflow-hidden rounded-lg bg-white shadow-lg transition-all duration-300 hover:shadow-xl dark:bg-gray-800 md:w-[380px]"
          :class="getStackClass(index)"
          :style="{ zIndex: 100 - index }"
          @mouseenter="pauseToast(toast.id)"
          @mouseleave="resumeToast(toast.id)"
        >
          <!-- Left accent border -->
          <div
            class="absolute left-0 top-0 h-full w-1"
            :class="accentColor(toast.type)"
          />

          <!-- Main content -->
          <div class="flex items-start gap-3 p-4 pl-5">
            <!-- Icon with animation -->
            <component
              :is="toastIcon(toast.type)"
              class="toast-icon h-5 w-5 shrink-0"
              :class="[iconColor(toast.type), iconAnimation(toast.type)]"
            />

            <!-- Text content -->
            <div class="flex-1 min-w-0">
              <p
                v-if="toast.title"
                class="font-semibold text-gray-900 dark:text-gray-100"
              >
                {{ toast.title }}
              </p>
              <p
                class="text-sm text-gray-700 dark:text-gray-300"
                :class="{ 'mt-0.5': toast.title }"
              >
                {{ toast.message }}
              </p>
            </div>

            <!-- Close button -->
            <button
              class="shrink-0 text-gray-400 opacity-60 transition-opacity hover:opacity-100 dark:text-gray-500"
              @click="removeToast(toast.id)"
              aria-label="알림 닫기"
            >
              <XMarkIcon class="h-4 w-4" />
            </button>
          </div>

          <!-- Timer bar -->
          <div
            class="timer-bar absolute bottom-0 left-0 h-0.5 origin-left"
            :class="timerBarColor(toast.type)"
            :data-toast-id="toast.id"
            :style="{
              animation: `timer-shrink ${toast.duration}ms linear`,
              animationPlayState: 'running',
            }"
          />
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  ExclamationTriangleIcon,
  InformationCircleIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import { useNotificationStore, type ToastType } from '@/stores/notification'
import { storeToRefs } from 'pinia'
import { onUnmounted } from 'vue'

const store = useNotificationStore()
const { toasts } = storeToRefs(store)
const { removeToast } = store

// Pause/resume toast timers
const pausedToasts = new Set<string>()

function pauseToast(id: string) {
  pausedToasts.add(id)
  const timerBar = document.querySelector(`[data-toast-id="${id}"]`) as HTMLElement
  if (timerBar) {
    timerBar.style.animationPlayState = 'paused'
  }

  // Find and pause the timeout
  const toast = toasts.value.find(t => t.id === id)
  if (toast?.timeoutId) {
    clearTimeout(toast.timeoutId)
  }
}

function resumeToast(id: string) {
  pausedToasts.delete(id)
  const timerBar = document.querySelector(`[data-toast-id="${id}"]`) as HTMLElement
  if (timerBar) {
    timerBar.style.animationPlayState = 'running'

    // Calculate remaining time
    const toast = toasts.value.find(t => t.id === id)
    if (toast) {
      const elapsed = Date.now() - toast.createdAt
      const remaining = Math.max(0, toast.duration - elapsed)

      if (remaining > 0) {
        toast.timeoutId = setTimeout(() => removeToast(id), remaining)
      }
    }
  }
}

function getStackClass(index: number) {
  const totalToasts = toasts.value.length
  const position = totalToasts - index - 1 // 0 is newest (bottom), increasing goes up

  if (position === 0) {
    // Newest toast (bottom) - full size
    return 'scale-100 opacity-100'
  } else if (position <= 2) {
    // Show next 2 toasts with slight scale down
    return 'scale-95 opacity-90'
  } else {
    // Older toasts - more scaled down
    return 'scale-90 opacity-70'
  }
}

function accentColor(type: ToastType) {
  return {
    success: 'bg-emerald-500',
    error: 'bg-red-500',
    warning: 'bg-amber-500',
    info: 'bg-blue-500',
  }[type]
}

function iconColor(type: ToastType) {
  return {
    success: 'text-emerald-500 dark:text-emerald-400',
    error: 'text-red-500 dark:text-red-400',
    warning: 'text-amber-500 dark:text-amber-400',
    info: 'text-blue-500 dark:text-blue-400',
  }[type]
}

function iconAnimation(type: ToastType) {
  return {
    success: 'animate-scale-in',
    error: 'animate-shake',
    warning: '',
    info: '',
  }[type]
}

function timerBarColor(type: ToastType) {
  return {
    success: 'bg-emerald-500',
    error: 'bg-red-500',
    warning: 'bg-amber-500',
    info: 'bg-blue-500',
  }[type]
}

function getToastAriaLabel(toast: { type: ToastType; title?: string; message: string }) {
  const typeLabels: Record<ToastType, string> = {
    success: '성공',
    error: '오류',
    warning: '경고',
    info: '정보',
  }
  const prefix = typeLabels[toast.type]
  return toast.title ? `${prefix}: ${toast.title} - ${toast.message}` : `${prefix}: ${toast.message}`
}

function toastIcon(type: ToastType) {
  return {
    success: CheckCircleIcon,
    error: ExclamationCircleIcon,
    warning: ExclamationTriangleIcon,
    info: InformationCircleIcon,
  }[type]
}

// Cleanup on unmount
onUnmounted(() => {
  pausedToasts.clear()
})
</script>

<style scoped>
/* Toast enter/leave transitions */
.toast-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 1, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%) scale(0.95);
}

@media (max-width: 768px) {
  .toast-enter-from {
    transform: translateY(100%) scale(0.95);
  }
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100%) scale(0.9);
}

@media (max-width: 768px) {
  .toast-leave-to {
    transform: translateY(20px) scale(0.95);
  }
}

/* Move animation for stacking */
.toast-move {
  transition: transform 0.3s ease-out;
}

/* Timer bar animation */
@keyframes timer-shrink {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}

/* Icon animations */
@keyframes scale-in {
  0% {
    transform: scale(0);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  10%, 30%, 50%, 70%, 90% {
    transform: translateX(-2px);
  }
  20%, 40%, 60%, 80% {
    transform: translateX(2px);
  }
}

.animate-scale-in {
  animation: scale-in 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.animate-shake {
  animation: shake 0.5s ease-in-out;
}

/* Ensure timer bar stays on top of gradient */
.timer-bar {
  will-change: transform;
}
</style>
