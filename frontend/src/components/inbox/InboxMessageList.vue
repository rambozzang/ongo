<script setup lang="ts">
import { computed } from 'vue'
import type { InboxMessage } from '@/types/inbox'
import InboxMessageItem from './InboxMessageItem.vue'
import { CheckCircleIcon, InboxIcon } from '@heroicons/vue/24/outline'

interface Props {
  messages: InboxMessage[]
  selectedMessageId: number | null
  selectedMessageIds: Set<number>
  showCheckboxes?: boolean
}

interface Emits {
  (e: 'select-message', id: number): void
  (e: 'toggle-star', id: number): void
  (e: 'mark-all-read'): void
  (e: 'toggle-check', id: number): void
}

const props = withDefaults(defineProps<Props>(), {
  showCheckboxes: false,
})

const emit = defineEmits<Emits>()

interface GroupedMessages {
  today: InboxMessage[]
  yesterday: InboxMessage[]
  thisWeek: InboxMessage[]
  older: InboxMessage[]
}

const groupedMessages = computed<GroupedMessages>(() => {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterdayStart = new Date(todayStart.getTime() - 24 * 60 * 60 * 1000)
  const weekStart = new Date(todayStart.getTime() - 7 * 24 * 60 * 60 * 1000)

  const groups: GroupedMessages = {
    today: [],
    yesterday: [],
    thisWeek: [],
    older: [],
  }

  props.messages.forEach((message) => {
    const messageDate = new Date(message.createdAt)
    if (messageDate >= todayStart) {
      groups.today.push(message)
    } else if (messageDate >= yesterdayStart) {
      groups.yesterday.push(message)
    } else if (messageDate >= weekStart) {
      groups.thisWeek.push(message)
    } else {
      groups.older.push(message)
    }
  })

  return groups
})

const hasMessages = computed(() => props.messages.length > 0)

const handleSelectMessage = (id: number) => {
  emit('select-message', id)
}

const handleToggleStar = (id: number) => {
  emit('toggle-star', id)
}

const handleToggleCheck = (id: number) => {
  emit('toggle-check', id)
}
</script>

<template>
  <div class="h-full flex flex-col bg-white dark:bg-gray-800">
    <!-- Header -->
    <div class="border-b border-gray-200 dark:border-gray-700 p-4">
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold text-gray-900 dark:text-white">메시지</h3>
        <button
          v-if="hasMessages"
          @click="emit('mark-all-read')"
          class="flex items-center gap-2 px-3 py-1.5 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition-colors"
        >
          <CheckCircleIcon class="w-4 h-4" />
          <span>모두 읽음</span>
        </button>
      </div>
    </div>

    <!-- Message List -->
    <div class="flex-1 overflow-y-auto">
      <div v-if="!hasMessages" class="h-full flex items-center justify-center">
        <div class="text-center text-gray-500 dark:text-gray-400">
          <InboxIcon class="w-16 h-16 mx-auto mb-4 opacity-50" />
          <p class="text-lg">메시지가 없습니다</p>
        </div>
      </div>

      <div v-else>
        <!-- Today -->
        <div v-if="groupedMessages.today.length > 0">
          <div
            class="sticky top-0 bg-gray-50 dark:bg-gray-900 px-4 py-2 text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider z-10"
          >
            오늘
          </div>
          <InboxMessageItem
            v-for="message in groupedMessages.today"
            :key="message.id"
            :message="message"
            :is-selected="selectedMessageIds.has(message.id)"
            :is-active="selectedMessageId === message.id"
            :show-checkbox="showCheckboxes"
            :is-checked="selectedMessageIds.has(message.id)"
            @click="handleSelectMessage(message.id)"
            @toggle-star="handleToggleStar(message.id)"
            @toggle-check="handleToggleCheck(message.id)"
          />
        </div>

        <!-- Yesterday -->
        <div v-if="groupedMessages.yesterday.length > 0">
          <div
            class="sticky top-0 bg-gray-50 dark:bg-gray-900 px-4 py-2 text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider z-10"
          >
            어제
          </div>
          <InboxMessageItem
            v-for="message in groupedMessages.yesterday"
            :key="message.id"
            :message="message"
            :is-selected="selectedMessageIds.has(message.id)"
            :is-active="selectedMessageId === message.id"
            :show-checkbox="showCheckboxes"
            :is-checked="selectedMessageIds.has(message.id)"
            @click="handleSelectMessage(message.id)"
            @toggle-star="handleToggleStar(message.id)"
            @toggle-check="handleToggleCheck(message.id)"
          />
        </div>

        <!-- This Week -->
        <div v-if="groupedMessages.thisWeek.length > 0">
          <div
            class="sticky top-0 bg-gray-50 dark:bg-gray-900 px-4 py-2 text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider z-10"
          >
            이번 주
          </div>
          <InboxMessageItem
            v-for="message in groupedMessages.thisWeek"
            :key="message.id"
            :message="message"
            :is-selected="selectedMessageIds.has(message.id)"
            :is-active="selectedMessageId === message.id"
            :show-checkbox="showCheckboxes"
            :is-checked="selectedMessageIds.has(message.id)"
            @click="handleSelectMessage(message.id)"
            @toggle-star="handleToggleStar(message.id)"
            @toggle-check="handleToggleCheck(message.id)"
          />
        </div>

        <!-- Older -->
        <div v-if="groupedMessages.older.length > 0">
          <div
            class="sticky top-0 bg-gray-50 dark:bg-gray-900 px-4 py-2 text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider z-10"
          >
            이전
          </div>
          <InboxMessageItem
            v-for="message in groupedMessages.older"
            :key="message.id"
            :message="message"
            :is-selected="selectedMessageIds.has(message.id)"
            :is-active="selectedMessageId === message.id"
            :show-checkbox="showCheckboxes"
            :is-checked="selectedMessageIds.has(message.id)"
            @click="handleSelectMessage(message.id)"
            @toggle-star="handleToggleStar(message.id)"
            @toggle-check="handleToggleCheck(message.id)"
          />
        </div>
      </div>
    </div>
  </div>
</template>
