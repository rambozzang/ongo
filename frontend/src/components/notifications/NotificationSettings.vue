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

function update(field: keyof NotificationSettings, value: boolean | string | number) {
  emit('update', {
    ...props.settings,
    [field]: value,
  } as NotificationSettings)
}

function updateBoolean(field: 'uploadEmail', event: Event) {
  update(field, (event.target as HTMLInputElement).checked)
}

function updateString(field: 'commentFrequency', event: Event) {
  update(field, (event.target as HTMLSelectElement).value)
}

function updateReminder(event: Event) {
  update(fieldName, Number((event.target as HTMLSelectElement).value))
}

const fieldName = 'scheduleReminderMinutes' as const
</script>

<template>
  <form class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800" @submit.prevent="emit('save')">
    <div class="border-b border-gray-200 px-6 py-4 dark:border-gray-700">
      <h3 class="text-title font-semibold text-gray-900 dark:text-white">알림 설정</h3>
      <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
        실제 계정에 저장되는 알림 수신 기준을 설정합니다.
      </p>
    </div>

    <div class="space-y-5 px-6 py-5">
      <label class="flex items-start justify-between gap-4">
        <span>
          <span class="block text-body font-medium text-gray-700 dark:text-gray-300">업로드 결과 이메일 알림</span>
          <span class="mt-1 block text-body-sm text-gray-500 dark:text-gray-400">업로드 완료 및 실패 결과를 이메일로 받습니다.</span>
        </span>
        <input
          type="checkbox"
          class="mt-0.5 h-5 w-5 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
          :checked="settings.uploadEmail"
          @change="updateBoolean('uploadEmail', $event)"
        >
      </label>

      <label class="block">
        <span class="text-body font-medium text-gray-700 dark:text-gray-300">댓글 알림 빈도</span>
        <select
          class="input-field mt-2 w-full"
          :value="settings.commentFrequency"
          @change="updateString('commentFrequency', $event)"
        >
          <option value="realtime">실시간</option>
          <option value="daily">하루 한 번</option>
          <option value="none">받지 않음</option>
        </select>
      </label>

      <label class="block">
        <span class="text-body font-medium text-gray-700 dark:text-gray-300">AI 크레딧 알림 기준</span>
        <span class="mt-1 block text-body-sm text-gray-500 dark:text-gray-400">크레딧이 이 값 이하가 되면 알림을 보냅니다.</span>
        <input
          type="number"
          min="0"
          max="1000000"
          class="input-field mt-2 w-full"
          :value="settings.creditThreshold"
          @input="update('creditThreshold', Math.max(0, Number(($event.target as HTMLInputElement).value) || 0))"
        >
      </label>

      <label class="block">
        <span class="text-body font-medium text-gray-700 dark:text-gray-300">예약 게시 알림</span>
        <select
          class="input-field mt-2 w-full"
          :value="settings.scheduleReminderMinutes"
          @change="updateReminder($event)"
        >
          <option :value="0">받지 않음</option>
          <option :value="30">30분 전</option>
          <option :value="60">1시간 전</option>
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
