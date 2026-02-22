<template>
  <div
    v-if="showGuide"
    class="card overflow-hidden border-primary-200 bg-gradient-to-r from-primary-50 to-blue-50 dark:border-primary-800 dark:from-primary-950/30 dark:to-blue-950/30"
  >
    <div class="flex items-start justify-between gap-4 p-4">
      <div class="flex-1">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          onGo 시작하기
        </h3>
        <p class="mt-1 text-xs text-gray-600 dark:text-gray-400">
          {{ completedCount }}/{{ steps.length }} 단계 완료
        </p>

        <!-- Progress bar -->
        <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full bg-primary-500 transition-all duration-500"
            :style="{ width: `${progress}%` }"
          />
        </div>

        <!-- Steps checklist -->
        <ul class="mt-3 space-y-2">
          <li
            v-for="step in steps"
            :key="step.id"
            class="flex items-center gap-2"
          >
            <div
              class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full transition-colors"
              :class="
                step.completed
                  ? 'bg-green-500 text-white'
                  : 'border-2 border-gray-300 dark:border-gray-600'
              "
            >
              <CheckIcon v-if="step.completed" class="h-3 w-3" />
            </div>
            <span
              class="text-sm"
              :class="
                step.completed
                  ? 'text-gray-400 line-through dark:text-gray-500'
                  : 'text-gray-700 dark:text-gray-300'
              "
            >
              {{ step.label }}
            </span>
            <router-link
              v-if="!step.completed"
              :to="stepRoutes[step.id]"
              class="ml-auto text-xs font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
            >
              시작하기 &rarr;
            </router-link>
          </li>
        </ul>
      </div>

      <!-- Dismiss button -->
      <button
        class="shrink-0 rounded-lg p-1 text-gray-400 hover:bg-gray-200/50 hover:text-gray-600 dark:text-gray-500 dark:hover:bg-gray-700/50 dark:hover:text-gray-300"
        aria-label="나중에 하기"
        @click="dismiss"
      >
        <XMarkIcon class="h-5 w-5" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CheckIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useOnboardingGuide } from '@/composables/useOnboardingGuide'

const { steps, showGuide, completedCount, progress, dismiss } = useOnboardingGuide()

const stepRoutes: Record<string, string> = {
  upload: '/upload',
  ai: '/ai',
  publish: '/videos',
  analytics: '/analytics',
}
</script>
