<template>
  <div class="mt-3 space-y-2 border-l-2 border-primary-200 pl-4 dark:border-primary-800">
    <textarea
      v-model="replyText"
      placeholder="답글을 입력하세요..."
      rows="3"
      class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
      :disabled="submitting"
    />
    <div class="flex items-center justify-between">
      <span class="text-xs text-gray-500 dark:text-gray-400">{{ replyText.length }}/500</span>
      <div class="flex gap-2">
        <button
          type="button"
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
          :disabled="submitting"
          @click="emit('cancel')"
        >
          취소
        </button>
        <button
          type="button"
          class="rounded-lg bg-primary-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
          :disabled="!replyText.trim() || submitting || replyText.length > 500"
          @click="handleSubmit"
        >
          {{ submitting ? '답글 작성 중...' : '답글 달기' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  submit: [text: string]
  cancel: []
}>()

const replyText = ref('')
const submitting = ref(false)

const handleSubmit = async () => {
  if (!replyText.value.trim()) return

  submitting.value = true
  try {
    emit('submit', replyText.value)
    replyText.value = ''
  } finally {
    submitting.value = false
  }
}
</script>
