<template>
  <div v-if="guide.showGuide.value" class="card mb-6 border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50">
    <div class="mb-3 flex items-center justify-between">
      <div>
        <h3 class="text-h3 text-gray-900 dark:text-gray-100">onGo 시작하기</h3>
        <p class="text-body-sm text-gray-500 dark:text-gray-400">{{ guide.completedCount.value }}/4 완료</p>
      </div>
      <button
        class="text-caption text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
        @click="guide.dismiss()"
      >
        숨기기
      </button>
    </div>

    <div class="mb-4 h-1.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        class="h-full rounded-full bg-primary-500 transition-all duration-500"
        :style="{ width: `${guide.progress.value}%` }"
      />
    </div>

    <div class="grid gap-2 tablet:grid-cols-4">
      <div
        v-for="step in guide.steps.value"
        :key="step.id"
        class="flex items-center gap-2 rounded-lg p-2 text-sm"
        :class="step.completed
          ? 'text-primary-600 dark:text-primary-400'
          : 'text-gray-500 dark:text-gray-400'"
      >
        <div
          class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full"
          :class="step.completed
            ? 'bg-primary-500 text-white'
            : 'border border-gray-300 dark:border-gray-600'"
        >
          <CheckIcon v-if="step.completed" class="h-3 w-3" />
        </div>
        <span :class="{ 'line-through': step.completed }">{{ step.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CheckIcon } from '@heroicons/vue/24/solid'
import { useOnboardingGuide } from '@/composables/useOnboardingGuide'

const guide = useOnboardingGuide()
</script>
