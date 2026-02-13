<template>
  <div class="card">
    <p class="mb-3 text-sm font-medium text-gray-500 dark:text-gray-400">빠른 작업</p>

    <div class="grid grid-cols-2 gap-2">
      <button
        class="flex flex-col items-center justify-center gap-2 rounded-lg border border-gray-200 p-4 transition-all hover:border-primary-500 hover:bg-primary-50 dark:border-gray-700 dark:hover:border-primary-500 dark:hover:bg-primary-900/20"
        @click="$router.push('/upload')"
      >
        <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary-100 dark:bg-primary-900/30">
          <PlusIcon class="h-5 w-5 text-primary-600 dark:text-primary-400" />
        </div>
        <span class="text-xs font-medium text-gray-900 dark:text-gray-100">새 영상</span>
      </button>

      <button
        class="flex flex-col items-center justify-center gap-2 rounded-lg border border-gray-200 p-4 transition-all hover:border-purple-500 hover:bg-purple-50 dark:border-gray-700 dark:hover:border-purple-500 dark:hover:bg-purple-900/20"
        @click="$router.push('/ai')"
      >
        <div class="flex h-10 w-10 items-center justify-center rounded-full bg-purple-100 dark:bg-purple-900/30">
          <SparklesIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
        </div>
        <span class="text-xs font-medium text-gray-900 dark:text-gray-100">AI 도구</span>
      </button>

      <button
        class="flex flex-col items-center justify-center gap-2 rounded-lg border border-gray-200 p-4 transition-all hover:border-blue-500 hover:bg-blue-50 dark:border-gray-700 dark:hover:border-blue-500 dark:hover:bg-blue-900/20"
        @click="$router.push('/schedule')"
      >
        <div class="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900/30">
          <CalendarDaysIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
        </div>
        <span class="text-xs font-medium text-gray-900 dark:text-gray-100">일정</span>
      </button>

      <button
        class="flex flex-col items-center justify-center gap-2 rounded-lg border border-gray-200 p-4 transition-all hover:border-green-500 hover:bg-green-50 dark:border-gray-700 dark:hover:border-green-500 dark:hover:bg-green-900/20"
        @click="$router.push('/analytics')"
      >
        <div class="flex h-10 w-10 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
          <ChartBarIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
        </div>
        <span class="text-xs font-medium text-gray-900 dark:text-gray-100">분석</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  PlusIcon,
  SparklesIcon,
  CalendarDaysIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
</script>
