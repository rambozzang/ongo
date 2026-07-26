<template>
  <div>
    <!-- 필터 -->
    <div class="mb-4 flex flex-wrap gap-3 items-center">
      <div class="flex items-center gap-2 flex-1 min-w-[200px]">
        <MagnifyingGlassIcon class="w-5 h-5 text-gray-400 dark:text-gray-500" />
        <input
          type="text"
          :value="inboxStore.filters.searchText"
          :placeholder="$t('inbox.searchPlaceholder')"
          class="input-field flex-1"
          @input="inboxStore.setFilters({ searchText: ($event.target as HTMLInputElement).value })"
        />
      </div>

      <div class="flex items-center gap-2">
        <FunnelIcon class="w-5 h-5 text-gray-400 dark:text-gray-500" />
        <select
          :value="inboxStore.filters.platform"
          :aria-label="$t('inbox.filters.allPlatforms')"
          class="input-field"
          @change="inboxStore.setFilters({ platform: ($event.target as HTMLSelectElement).value as MessagePlatform | 'ALL' })"
        >
          <option v-for="option in platformOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>

        <select
          :value="inboxStore.filters.type"
          :aria-label="$t('inbox.filters.allTypes')"
          class="input-field"
          @change="inboxStore.setFilters({ type: ($event.target as HTMLSelectElement).value as MessageType | 'ALL' })"
        >
          <option v-for="option in typeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>

        <select
          :value="inboxStore.filters.status"
          :aria-label="$t('inbox.filters.allStatuses')"
          class="input-field"
          @change="inboxStore.setFilters({ status: ($event.target as HTMLSelectElement).value as MessageStatus | 'ALL' })"
        >
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>

        <button
          class="btn-secondary"
          @click="toggleSelectionMode"
        >
          {{ showCheckboxes ? $t('inbox.cancelSelection') : $t('inbox.select') }}
        </button>
      </div>
    </div>

    <!-- 일괄 처리 바 -->
    <div
      v-if="hasSelection"
      class="mb-4 rounded-lg bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-800 px-4 py-3"
    >
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium text-primary-900 dark:text-primary-100">
          {{ $t('inbox.selectedCount', { count: selectedCount }) }}
        </span>
        <div class="flex items-center gap-2">
          <button
            class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-primary-700 dark:text-primary-300 hover:bg-primary-100 dark:hover:bg-primary-900/40 rounded-lg transition-colors"
            @click="handleBulkMarkRead"
          >
            <CheckIcon class="w-4 h-4" />
            <span>{{ $t('inbox.markRead') }}</span>
          </button>
          <button
            class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-primary-700 dark:text-primary-300 hover:bg-primary-100 dark:hover:bg-primary-900/40 rounded-lg transition-colors"
            @click="handleBulkArchive"
          >
            <ArchiveBoxIcon class="w-4 h-4" />
            <span>{{ $t('inbox.archive') }}</span>
          </button>
          <button
            class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
            @click="cancelSelection"
          >
            <XMarkIcon class="w-4 h-4" />
            <span>{{ $t('inbox.cancel') }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 본문: 분할 레이아웃 -->
    <div class="flex h-[calc(100vh-24rem)] min-h-[24rem] overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
      <!-- 데스크톱: 분할 뷰 -->
      <div class="hidden tablet:flex flex-1">
        <div class="w-1/3 border-r border-gray-200 dark:border-gray-700">
          <InboxMessageList
            :messages="inboxStore.filteredMessages"
            :selected-message-id="inboxStore.selectedMessageId"
            :selected-message-ids="inboxStore.selectedMessageIds"
            :show-checkboxes="showCheckboxes"
            @select-message="handleSelectMessage"
            @toggle-star="inboxStore.toggleStar"
            @mark-all-read="inboxStore.markAllAsRead"
            @toggle-check="inboxStore.toggleMessageSelection"
          />
        </div>

        <div class="flex-1">
          <InboxMessageDetail
            :message="inboxStore.selectedMessage"
            @toggle-read="handleToggleRead"
            @toggle-star="handleToggleStarSelected"
            @archive="handleArchive"
            @delete="showDeleteConfirm = true"
            @reply="handleReply"
          />
        </div>
      </div>

      <!-- 모바일: 목록 또는 상세 -->
      <div class="tablet:hidden flex-1">
        <div v-show="!showMobileDetail" class="h-full">
          <InboxMessageList
            :messages="inboxStore.filteredMessages"
            :selected-message-id="inboxStore.selectedMessageId"
            :selected-message-ids="inboxStore.selectedMessageIds"
            :show-checkboxes="showCheckboxes"
            @select-message="handleSelectMessage"
            @toggle-star="inboxStore.toggleStar"
            @mark-all-read="inboxStore.markAllAsRead"
            @toggle-check="inboxStore.toggleMessageSelection"
          />
        </div>

        <div v-show="showMobileDetail" class="h-full flex flex-col">
          <div class="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 p-4">
            <button
              class="flex items-center gap-2 text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300"
              @click="showMobileDetail = false"
            >
              <XMarkIcon class="w-5 h-5" />
              <span>{{ $t('inbox.backToList') }}</span>
            </button>
          </div>
          <div class="flex-1 overflow-hidden">
            <InboxMessageDetail
              :message="inboxStore.selectedMessage"
              @toggle-read="handleToggleRead"
              @toggle-star="handleToggleStarSelected"
              @archive="handleArchive"
              @delete="showDeleteConfirm = true"
              @reply="handleReply"
            />
          </div>
        </div>
      </div>
    </div>

    <ConfirmModal
      v-model="showDeleteConfirm"
      :title="$t('inbox.confirmDeleteTitle')"
      :message="$t('inbox.confirmDelete')"
      danger
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useInboxStore } from '@/stores/inbox'
import type { MessagePlatform, MessageType, MessageStatus } from '@/types/inbox'
import InboxMessageList from './InboxMessageList.vue'
import InboxMessageDetail from './InboxMessageDetail.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import {
  FunnelIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  ArchiveBoxIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'

const props = withDefaults(
  defineProps<{
    /** 필터 상태를 URL 쿼리에 반영할지 여부 */
    syncQuery?: boolean
  }>(),
  { syncQuery: false },
)

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const inboxStore = useInboxStore()

const showMobileDetail = ref(false)
const showCheckboxes = ref(false)
const showDeleteConfirm = ref(false)

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

const handleToggleStarSelected = () => {
  if (!inboxStore.selectedMessage) return
  inboxStore.toggleStar(inboxStore.selectedMessage.id)
}

const handleToggleRead = () => {
  const message = inboxStore.selectedMessage
  if (!message) return
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
  const message = inboxStore.selectedMessage
  if (!message) return
  const index = inboxStore.messages.findIndex((m) => m.id === message.id)
  if (index !== -1) {
    inboxStore.messages.splice(index, 1)
  }
  inboxStore.selectMessage(null)
  showMobileDetail.value = false
}

const handleReply = (content: string) => {
  if (!inboxStore.selectedMessage) return
  inboxStore.replyToMessage(inboxStore.selectedMessage.id, content)
}

const handleBulkArchive = () => {
  inboxStore.bulkArchive(Array.from(inboxStore.selectedMessageIds))
  showCheckboxes.value = false
}

const handleBulkMarkRead = () => {
  inboxStore.bulkMarkRead(Array.from(inboxStore.selectedMessageIds))
  showCheckboxes.value = false
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

// ---- URL 쿼리 동기화 (댓글 탭 파라미터와 충돌하지 않도록 msg* 접두사 사용) ----
let applyingQuery = false

const currentQueryState = () => ({
  msgPlatform: inboxStore.filters.platform === 'ALL' ? undefined : inboxStore.filters.platform,
  msgType: inboxStore.filters.type === 'ALL' ? undefined : inboxStore.filters.type,
  msgStatus: inboxStore.filters.status === 'ALL' ? undefined : inboxStore.filters.status,
  msgQ: inboxStore.filters.searchText || undefined,
})

const readString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)

const applyQuery = async () => {
  applyingQuery = true
  const q = route.query
  inboxStore.setFilters({
    platform: (readString(q.msgPlatform) as MessagePlatform | undefined) ?? 'ALL',
    type: (readString(q.msgType) as MessageType | undefined) ?? 'ALL',
    status: (readString(q.msgStatus) as MessageStatus | undefined) ?? 'ALL',
    searchText: readString(q.msgQ) ?? '',
  })
  await nextTick()
  applyingQuery = false
}

const writeQuery = () => {
  const next: Record<string, unknown> = { ...route.query, ...currentQueryState() }
  Object.keys(next).forEach((key) => {
    if (next[key] === undefined) delete next[key]
  })
  if (JSON.stringify(next) === JSON.stringify(route.query)) return
  router.replace({ query: next as Record<string, string> })
}

watch(
  () => ({ ...inboxStore.filters }),
  () => {
    if (props.syncQuery && !applyingQuery) writeQuery()
  },
)

// 뒤로/앞으로 가기 대응
watch(
  () => route.query,
  () => {
    if (!props.syncQuery) return
    const desired = currentQueryState()
    const same = (Object.keys(desired) as (keyof typeof desired)[]).every(
      (key) => (readString(route.query[key]) ?? undefined) === desired[key],
    )
    if (same) return
    void applyQuery()
  },
)

onMounted(() => {
  if (props.syncQuery) void applyQuery()
})
</script>
