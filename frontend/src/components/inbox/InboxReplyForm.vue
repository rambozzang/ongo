<script setup lang="ts">
import { ref, computed } from 'vue'
import type { MessagePlatform } from '@/types/inbox'
import { PaperAirplaneIcon } from '@heroicons/vue/24/outline'

interface Props {
  platform: MessagePlatform
}

interface Emits {
  (e: 'send', replyContent: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const replyText = ref('')

const platformLimits: Record<MessagePlatform, number> = {
  YOUTUBE: 10000,
  TIKTOK: 150,
  INSTAGRAM: 2200,
  NAVER_CLIP: 1000,
}

const characterLimit = computed(() => platformLimits[props.platform])
const characterCount = computed(() => replyText.value.length)
const isOverLimit = computed(() => characterCount.value > characterLimit.value)

const quickReplies = ['감사합니다!', '확인했습니다', '곧 답변드리겠습니다']

const useQuickReply = (template: string) => {
  replyText.value = template
}

const handleSend = () => {
  if (!replyText.value.trim() || isOverLimit.value) return
  emit('send', replyText.value)
  replyText.value = ''
}
</script>

<template>
  <div class="border-t border-gray-200 dark:border-gray-700 p-4 bg-white dark:bg-gray-800">
    <!-- Quick Reply Templates -->
    <div class="flex gap-2 mb-3">
      <button
        v-for="template in quickReplies"
        :key="template"
        @click="useQuickReply(template)"
        class="px-3 py-1 text-sm rounded-full bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors"
      >
        {{ template }}
      </button>
    </div>

    <!-- Reply Textarea -->
    <div class="space-y-2">
      <textarea
        v-model="replyText"
        placeholder="답장을 입력하세요..."
        rows="3"
        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 resize-none"
        :class="{ 'border-red-500 dark:border-red-500': isOverLimit }"
      ></textarea>

      <!-- Character Count & Send Button -->
      <div class="flex items-center justify-between">
        <span
          class="text-sm"
          :class="isOverLimit ? 'text-red-500 dark:text-red-400' : 'text-gray-500 dark:text-gray-400'"
        >
          {{ characterCount }} / {{ characterLimit }}
        </span>

        <button
          @click="handleSend"
          :disabled="!replyText.trim() || isOverLimit"
          class="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-gray-500 dark:disabled:text-gray-500 disabled:cursor-not-allowed transition-colors"
        >
          <PaperAirplaneIcon class="w-5 h-5" />
          <span>전송</span>
        </button>
      </div>
    </div>
  </div>
</template>
