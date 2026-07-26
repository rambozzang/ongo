<template>
  <!-- 일괄 처리 액션 바 (하단 고정) -->
  <Transition name="slide-up">
    <div
      v-if="count > 0"
      class="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 flex items-center gap-3 rounded-2xl border border-gray-200 bg-white px-5 py-3 shadow-xl dark:border-gray-700 dark:bg-gray-900"
    >
      <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('commentsView.batch.selected', { count }) }}
      </span>
      <div class="h-4 w-px bg-gray-300 dark:bg-gray-600" />
      <button class="btn-primary text-xs" :disabled="loading" @click="showReplyModal = true">
        {{ $t('commentsView.batch.reply') }}
      </button>
      <button class="btn-secondary text-xs" :disabled="loading" @click="emit('hide')">
        {{ $t('commentsView.batch.hide') }}
      </button>
      <button
        class="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
        @click="emit('clear')"
      >
        {{ $t('commentsView.batch.cancel') }}
      </button>
    </div>
  </Transition>

  <!-- 일괄 답변 모달 -->
  <BaseModal v-model="showReplyModal" :title="$t('commentsView.batch.replyModalTitle')">
    <div class="space-y-3">
      <textarea
        v-model="replyText"
        rows="4"
        class="input-field w-full"
        :placeholder="$t('commentsView.batch.replyPlaceholder', { count })"
      />
    </div>
    <template #footer>
      <button class="btn-secondary" @click="showReplyModal = false">
        {{ $t('commentsView.batch.cancel') }}
      </button>
      <button
        class="btn-primary text-sm"
        :disabled="!replyText.trim() || loading"
        @click="handleReply"
      >
        {{ $t('commentsView.batch.confirm') }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BaseModal from '@/components/common/BaseModal.vue'

defineProps<{
  count: number
  loading: boolean
}>()

const emit = defineEmits<{
  reply: [text: string]
  hide: []
  clear: []
}>()

const showReplyModal = ref(false)
const replyText = ref('')

const handleReply = () => {
  const text = replyText.value.trim()
  if (!text) return
  emit('reply', text)
  showReplyModal.value = false
  replyText.value = ''
}
</script>

<style scoped>
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateX(-50%) translateY(16px);
  opacity: 0;
}
</style>
