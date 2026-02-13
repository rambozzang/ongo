<script setup lang="ts">
import type { NotificationCategory, NotificationSetting } from '@/types/notification'

defineProps<{
  settings: NotificationSetting[]
}>()

const emit = defineEmits<{
  (e: 'update', category: NotificationCategory, field: 'inApp' | 'email' | 'kakao', value: boolean): void
}>()

const categoryLabels: Record<NotificationCategory, string> = {
  upload: '업로드',
  schedule: '예약 게시',
  channel: '채널 관리',
  ai: 'AI 크레딧',
  analytics: '분석 / 리포트',
  subscription: '구독 / 결제',
}

function handleToggle(category: NotificationCategory, field: 'inApp' | 'email' | 'kakao', current: boolean) {
  emit('update', category, field, !current)
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
    <div class="border-b border-gray-200 px-6 py-4 dark:border-gray-700">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white">알림 설정</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        카테고리별로 알림 수신 방법을 설정합니다.
      </p>
    </div>

    <!-- Header row -->
    <div class="grid grid-cols-4 gap-4 border-b border-gray-100 px-6 py-3 dark:border-gray-700">
      <div class="text-sm font-medium text-gray-500 dark:text-gray-400">카테고리</div>
      <div class="text-center text-sm font-medium text-gray-500 dark:text-gray-400">앱 내 알림</div>
      <div class="text-center text-sm font-medium text-gray-500 dark:text-gray-400">이메일</div>
      <div class="text-center text-sm font-medium text-gray-500 dark:text-gray-400">카카오톡</div>
    </div>

    <!-- Setting rows -->
    <div
      v-for="setting in settings"
      :key="setting.category"
      class="grid grid-cols-4 items-center gap-4 border-b border-gray-50 px-6 py-4 last:border-b-0 dark:border-gray-700/50"
    >
      <!-- Category label -->
      <div class="text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ categoryLabels[setting.category] }}
      </div>

      <!-- In-app toggle -->
      <div class="flex justify-center">
        <button
          type="button"
          class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
          :class="setting.inApp ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600'"
          role="switch"
          :aria-checked="setting.inApp"
          @click="handleToggle(setting.category, 'inApp', setting.inApp)"
        >
          <span
            class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
            :class="setting.inApp ? 'translate-x-5' : 'translate-x-0'"
          />
        </button>
      </div>

      <!-- Email toggle -->
      <div class="flex justify-center">
        <button
          type="button"
          class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
          :class="setting.email ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600'"
          role="switch"
          :aria-checked="setting.email"
          @click="handleToggle(setting.category, 'email', setting.email)"
        >
          <span
            class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
            :class="setting.email ? 'translate-x-5' : 'translate-x-0'"
          />
        </button>
      </div>

      <!-- Kakao toggle -->
      <div class="flex justify-center">
        <button
          type="button"
          class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
          :class="setting.kakao ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600'"
          role="switch"
          :aria-checked="setting.kakao"
          @click="handleToggle(setting.category, 'kakao', setting.kakao)"
        >
          <span
            class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
            :class="setting.kakao ? 'translate-x-5' : 'translate-x-0'"
          />
        </button>
      </div>
    </div>
  </div>
</template>
