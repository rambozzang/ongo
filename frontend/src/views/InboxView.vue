<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInboxStore } from '@/stores/inbox'
import type { MessagePlatform, MessageType, MessageStatus } from '@/types/inbox'
import InboxMessageList from '@/components/inbox/InboxMessageList.vue'
import InboxMessageDetail from '@/components/inbox/InboxMessageDetail.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import {
  FunnelIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  ArchiveBoxIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })

const inboxStore = useInboxStore()

const showMobileDetail = ref(false)
const showCheckboxes = ref(false)

const platformOptions = computed(() => [
  { value: 'ALL' as const, label: t('inbox.filters.allPlatforms') },
  { value: 'YOUTUBE' as const, label: 'YouTube' },
  { value: 'TIKTOK' as const, label: 'TikTok' },
  { value: 'INSTAGRAM' as const, label: 'Instagram' },
  { value: 'NAVER_CLIP' as const, label: t('inbox.filters.naverClip') },
])

const typeOptions = computed(() => [
  { value: 'ALL' as const, label: t('inbox.filters.allTypes') },
  { value: 'comment' as const, label: t('inbox.filters.comment') },
  { value: 'mention' as const, label: t('inbox.filters.mention') },
  { value: 'dm' as const, label: 'DM' },
  { value: 'reply' as const, label: t('inbox.filters.reply') },
])

const statusOptions = computed(() => [
  { value: 'ALL' as const, label: t('inbox.filters.allStatuses') },
  { value: 'unread' as const, label: t('inbox.filters.unread') },
  { value: 'read' as const, label: t('inbox.filters.read') },
  { value: 'replied' as const, label: t('inbox.filters.replied') },
  { value: 'archived' as const, label: t('inbox.filters.archived') },
])

const selectedCount = computed(() => inboxStore.selectedMessageIds.size)
const hasSelection = computed(() => selectedCount.value > 0)

const handleSelectMessage = (id: number) => {
  inboxStore.selectMessage(id)
  showMobileDetail.value = true
}

const handleToggleStar = (id: number) => {
  inboxStore.toggleStar(id)
}

const handleMarkAllRead = () => {
  inboxStore.markAllAsRead()
}

const handleToggleRead = () => {
  if (!inboxStore.selectedMessage) return
  const message = inboxStore.selectedMessage
  if (message.status === 'unread') {
    inboxStore.markAsRead(message.id)
  } else if (message.status === 'read') {
    inboxStore.markAsUnread(message.id)
  }
}

const handleArchive = () => {
  if (!inboxStore.selectedMessage) return
  inboxStore.archiveMessage(inboxStore.selectedMessage.id)
  inboxStore.selectMessage(null)
  showMobileDetail.value = false
}

const handleDelete = () => {
  if (!inboxStore.selectedMessage) return
  if (confirm(t('inbox.confirmDelete'))) {
    const index = inboxStore.messages.findIndex(
      (m) => m.id === inboxStore.selectedMessage?.id
    )
    if (index !== -1) {
      inboxStore.messages.splice(index, 1)
      inboxStore.selectMessage(null)
      showMobileDetail.value = false
    }
  }
}

const handleReply = (content: string) => {
  if (!inboxStore.selectedMessage) return
  inboxStore.replyToMessage(inboxStore.selectedMessage.id, content)
}

const handleToggleCheck = (id: number) => {
  inboxStore.toggleMessageSelection(id)
}

const handleBulkArchive = () => {
  const ids = Array.from(inboxStore.selectedMessageIds)
  inboxStore.bulkArchive(ids)
  showCheckboxes.value = false
}

const handleBulkMarkRead = () => {
  const ids = Array.from(inboxStore.selectedMessageIds)
  inboxStore.bulkMarkRead(ids)
  showCheckboxes.value = false
}

const handleCloseMobileDetail = () => {
  showMobileDetail.value = false
}

const toggleSelectionMode = () => {
  showCheckboxes.value = !showCheckboxes.value
  if (!showCheckboxes.value) {
    inboxStore.clearSelection()
  }
}

const cancelSelection = () => {
  showCheckboxes.value = false
  inboxStore.clearSelection()
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('inbox.title') }}</h1>
          <span
            v-if="inboxStore.unreadCount > 0"
            class="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-blue-600 text-white"
          >
            {{ inboxStore.unreadCount }}
          </span>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('inbox.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="toggleSelectionMode"
          class="px-3 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
        >
          {{ showCheckboxes ? $t('inbox.cancelSelection') : $t('inbox.select') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('inbox.pageGuideTitle')" :items="($tm('inbox.pageGuide') as string[])" />

    <!-- Filters -->
    <div class="mb-4">
      <div class="flex flex-wrap gap-3 items-center">
        <div class="flex items-center gap-2 flex-1 min-w-[200px]">
          <MagnifyingGlassIcon class="w-5 h-5 text-gray-400 dark:text-gray-500" />
          <input
            type="text"
            :value="inboxStore.filters.searchText"
            @input="
              inboxStore.setFilters({
                searchText: ($event.target as HTMLInputElement).value,
              })
            "
            :placeholder="$t('inbox.searchPlaceholder')"
            class="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500"
          />
        </div>

          <div class="flex items-center gap-2">
            <FunnelIcon class="w-5 h-5 text-gray-400 dark:text-gray-500" />
            <select
              :value="inboxStore.filters.platform"
              @change="
                inboxStore.setFilters({
                  platform: ($event.target as HTMLSelectElement).value as MessagePlatform | 'ALL',
                })
              "
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
            >
              <option v-for="option in platformOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>

            <select
              :value="inboxStore.filters.type"
              @change="
                inboxStore.setFilters({
                  type: ($event.target as HTMLSelectElement).value as MessageType | 'ALL',
                })
              "
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
            >
              <option v-for="option in typeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>

            <select
              :value="inboxStore.filters.status"
              @change="
                inboxStore.setFilters({
                  status: ($event.target as HTMLSelectElement).value as MessageStatus | 'ALL',
                })
              "
              class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
            >
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
        </div>
      </div>

    <!-- Bulk Action Bar -->
    <div
      v-if="hasSelection"
      class="mb-4 rounded-lg bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 px-4 py-3"
    >
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium text-blue-900 dark:text-blue-100">
          {{ $t('inbox.selectedCount', { count: selectedCount }) }}
        </span>
        <div class="flex items-center gap-2">
          <button
            @click="handleBulkMarkRead"
            class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-blue-700 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-900/40 rounded-lg transition-colors"
          >
            <CheckIcon class="w-4 h-4" />
            <span>{{ $t('inbox.markRead') }}</span>
          </button>
          <button
            @click="handleBulkArchive"
            class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-blue-700 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-900/40 rounded-lg transition-colors"
          >
            <ArchiveBoxIcon class="w-4 h-4" />
            <span>{{ $t('inbox.archive') }}</span>
          </button>
          <button
            @click="cancelSelection"
            class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          >
            <XMarkIcon class="w-4 h-4" />
            <span>{{ $t('inbox.cancel') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Main Content: Split Layout -->
    <div class="flex h-[calc(100vh-20rem)] overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
      <!-- Desktop: Split View -->
      <div class="hidden md:flex flex-1">
        <!-- Left: Message List (1/3 width) -->
        <div class="w-1/3 border-r border-gray-200 dark:border-gray-700">
          <InboxMessageList
            :messages="inboxStore.filteredMessages"
            :selected-message-id="inboxStore.selectedMessageId"
            :selected-message-ids="inboxStore.selectedMessageIds"
            :show-checkboxes="showCheckboxes"
            @select-message="handleSelectMessage"
            @toggle-star="handleToggleStar"
            @mark-all-read="handleMarkAllRead"
            @toggle-check="handleToggleCheck"
          />
        </div>

        <!-- Right: Message Detail (2/3 width) -->
        <div class="flex-1">
          <InboxMessageDetail
            :message="inboxStore.selectedMessage"
            @toggle-read="handleToggleRead"
            @toggle-star="handleToggleStar(inboxStore.selectedMessage!.id)"
            @archive="handleArchive"
            @delete="handleDelete"
            @reply="handleReply"
          />
        </div>
      </div>

      <!-- Mobile: List or Detail -->
      <div class="md:hidden flex-1">
        <!-- Message List -->
        <div v-show="!showMobileDetail" class="h-full">
          <InboxMessageList
            :messages="inboxStore.filteredMessages"
            :selected-message-id="inboxStore.selectedMessageId"
            :selected-message-ids="inboxStore.selectedMessageIds"
            :show-checkboxes="showCheckboxes"
            @select-message="handleSelectMessage"
            @toggle-star="handleToggleStar"
            @mark-all-read="handleMarkAllRead"
            @toggle-check="handleToggleCheck"
          />
        </div>

        <!-- Message Detail -->
        <div v-show="showMobileDetail" class="h-full flex flex-col">
          <div class="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 p-4">
            <button
              @click="handleCloseMobileDetail"
              class="flex items-center gap-2 text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300"
            >
              <XMarkIcon class="w-5 h-5" />
              <span>{{ $t('inbox.backToList') }}</span>
            </button>
          </div>
          <div class="flex-1 overflow-hidden">
            <InboxMessageDetail
              :message="inboxStore.selectedMessage"
              @toggle-read="handleToggleRead"
              @toggle-star="handleToggleStar(inboxStore.selectedMessage!.id)"
              @archive="handleArchive"
              @delete="handleDelete"
              @reply="handleReply"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
