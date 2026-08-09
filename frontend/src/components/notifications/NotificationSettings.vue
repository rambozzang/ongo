<script setup lang="ts">
import type { NotificationSettings } from '@/api/settings'

const props = withDefaults(defineProps<{
  settings: NotificationSettings
  saving?: boolean
  error?: string | null
  saved?: boolean
}>(), {
  saving: false,
  error: null,
  saved: false,
})

const emit = defineEmits<{
  (e: 'update', settings: NotificationSettings): void
  (e: 'save'): void
}>()

function updateFrequency(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('update', { ...props.settings, commentFrequency: value === 'realtime' ? 'realtime' : 'none' })
}
</script>

<template>
  <form class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800" @submit.prevent="emit('save')">
    <div class="border-b border-gray-200 px-6 py-4 dark:border-gray-700">
      <h3 class="text-title font-semibold text-gray-900 dark:text-white">알림 설정</h3>
      <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
        현재 계정에서 실제로 제공되는 댓글 알림 방식을 설정합니다.
      </p>
    </div>

    <div class="space-y-5 px-6 py-5">
      <label class="block">
        <span class="text-body font-medium text-gray-700 dark:text-gray-300">새 댓글 알림</span>
        <span class="mt-1 block text-body-sm text-gray-500 dark:text-gray-400">댓글 동기화 중 새 댓글을 발견하면 앱 내 알림을 만듭니다.</span>
        <select
          class="input-field mt-2 w-full"
          :value="settings.commentFrequency"
          @change="updateFrequency($event)"
        >
          <option value="realtime">실시간</option>
          <option value="none">받지 않음</option>
        </select>
      </label>

      <p v-if="error" class="rounded-lg border border-error-subtle bg-error-subtle px-3 py-2 text-body-sm text-error-strong" role="alert">
        {{ error }}
      </p>
      <p v-if="saved" class="text-body-sm text-success-strong" role="status">알림 설정을 저장했습니다.</p>

      <div class="flex justify-end">
        <button type="submit" class="btn-primary" :disabled="saving">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
      </div>
    </div>
  </form>
</template>
