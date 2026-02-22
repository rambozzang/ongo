<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4 dark:bg-gray-950">
    <div class="flex flex-col items-center text-center">
      <div class="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-800">
        <WifiIcon class="h-10 w-10 text-gray-400 dark:text-gray-500" />
      </div>

      <h1 class="mb-2 text-xl font-semibold text-gray-900 dark:text-gray-100">
        인터넷 연결이 끊어졌습니다
      </h1>
      <p class="mb-8 max-w-sm text-sm leading-relaxed text-gray-600 dark:text-gray-400">
        연결 확인 후 다시 시도해주세요.
        <br />
        Wi-Fi 또는 모바일 데이터 연결 상태를 확인해주세요.
      </p>

      <button
        class="rounded-lg bg-primary-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600"
        @click="refresh"
      >
        <ArrowPathIcon class="mr-1.5 inline h-4 w-4 align-text-bottom" />
        새로고침
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { WifiIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'

function refresh() {
  window.location.reload()
}
</script>
