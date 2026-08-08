<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  LinkIcon,
  BoltIcon,
  MagnifyingGlassIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useWebhookStore } from '@/stores/webhooks'
import { useNotification } from '@/composables/useNotification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import AsyncState from '@/components/common/AsyncState.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import WebhookCard from '@/components/webhooks/WebhookCard.vue'
import WebhookFormModal from '@/components/webhooks/WebhookFormModal.vue'
import WebhookDeliveryLog from '@/components/webhooks/WebhookDeliveryLog.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'
import type { Webhook, WebhookEvent } from '@/types/webhook'
import { WEBHOOK_EVENT_LABELS } from '@/types/webhook'

const { t } = useI18n({ useScope: 'global' })

// --- Store & composables ---
const webhookStore = useWebhookStore()
const notification = useNotification()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

/** 상태 필터 축 — 요약 카드(전체·활성·오류)와 같은 축을 쓴다. */
type WebhookStatusFilter = 'all' | 'active' | 'inactive' | 'failing'

// --- State ---
const showFormModal = ref(false)
const showDeleteModal = ref(false)
const showBulkDeleteModal = ref(false)
const bulkDeleting = ref(false)
const editingWebhook = ref<Webhook | null>(null)
const deletingWebhook = ref<Webhook | null>(null)
const statusFilter = ref<WebhookStatusFilter>('all')

// --- Computed ---
const webhooks = computed(() => webhookStore.webhooks)
const selectedWebhook = computed(() => webhookStore.selectedWebhook)

const summaryStats = computed(() => ({
  total: webhooks.value.length,
  active: webhooks.value.filter((w) => w.isActive).length,
  failingCount: webhooks.value.filter((w) => w.failureCount > 0).length,
}))

const deleteMessage = computed(() => {
  if (!deletingWebhook.value) return ''
  return t('webhooks.confirmDelete', { url: deletingWebhook.value.url })
})

// --- 검색 · 정렬 · 선택 ---
const sortOptions = computed<ListSortOption<Webhook>[]>(() => [
  { key: 'created', label: t('webhooks.sortCreated'), accessor: 'createdAt', kind: 'date', defaultDir: 'desc' },
  {
    key: 'recent',
    label: t('webhooks.sortRecent'),
    accessor: 'lastTriggeredAt',
    kind: 'date',
    defaultDir: 'desc',
  },
  {
    key: 'failures',
    label: t('webhooks.sortFailures'),
    accessor: 'failureCount',
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'url', label: t('webhooks.sortUrl'), accessor: 'url', kind: 'string', defaultDir: 'asc' },
])

/** 이벤트 키(video.uploaded)뿐 아니라 화면에 보이는 한글 라벨로도 검색되게 한다. */
const eventLabels = (webhook: Webhook): string[] =>
  webhook.events.map((event: WebhookEvent) => WEBHOOK_EVENT_LABELS[event]?.label ?? event)

const {
  query,
  sortKey,
  sortDir,
  filtered,
  visibleCount,
  isSourceEmpty,
  isResultEmpty,
  resetFilters,
  selectedIds,
  selectedCount,
  allSelected,
  someSelected,
  isSelected,
  toggle,
  toggleAll,
  clearSelection,
} = useListControls<Webhook>(() => webhookStore.webhooks, {
  searchFields: ['url', 'events', eventLabels],
  sortOptions,
  defaultSortKey: 'created',
  filters: computed(() => {
    switch (statusFilter.value) {
      case 'active':
        return [(webhook: Webhook) => webhook.isActive]
      case 'inactive':
        return [(webhook: Webhook) => !webhook.isActive]
      case 'failing':
        return [(webhook: Webhook) => webhook.failureCount > 0]
      default:
        return []
    }
  }),
})

const statusFilters = computed(() => [
  { label: t('webhooks.filter.all'), value: 'all' as const },
  { label: t('webhooks.filter.active'), value: 'active' as const },
  { label: t('webhooks.filter.inactive'), value: 'inactive' as const },
  { label: t('webhooks.filter.failing'), value: 'failing' as const },
])

const setStatusFilter = (value: WebhookStatusFilter) => {
  statusFilter.value = value
}

const resetSearchAndFilters = () => {
  resetFilters()
  statusFilter.value = 'all'
}

// --- Actions ---
function handleCreate() {
  editingWebhook.value = null
  showFormModal.value = true
}

function handleEdit(webhook: Webhook) {
  editingWebhook.value = webhook
  showFormModal.value = true
}

function handleDeletePrompt(webhook: Webhook) {
  deletingWebhook.value = webhook
  showDeleteModal.value = true
}

async function handleDelete() {
  if (!deletingWebhook.value) return
  try {
    await webhookStore.deleteWebhook(deletingWebhook.value.id)
    notification.success(t('webhooks.notify.deleted'))
    deletingWebhook.value = null
  } catch {
    notification.error(t('webhooks.notify.deleteFailed'))
  }
}

async function handleBulkDelete() {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  bulkDeleting.value = true
  try {
    await Promise.all(ids.map((id) => webhookStore.deleteWebhook(id)))
    notification.success(t('webhooks.bulkDeleteDone', { count: ids.length }))
  } finally {
    bulkDeleting.value = false
    clearSelection()
  }
}

async function handleToggle(webhook: Webhook) {
  try {
    await webhookStore.toggleActive(webhook.id)
    const status = webhook.isActive ? t('webhooks.notify.activated') : t('webhooks.notify.deactivated')
    notification.success(status)
  } catch {
    notification.error(t('webhooks.notify.updateFailed'))
  }
}

async function handleTest(webhook: Webhook) {
  try {
    const delivery = await webhookStore.testWebhook(webhook.id)
    if (delivery.statusCode >= 200 && delivery.statusCode < 300) {
      notification.success(t('webhooks.notify.testSuccess', { code: delivery.statusCode, duration: delivery.duration }))
    } else {
      notification.error(t('webhooks.notify.testFailed', { code: delivery.statusCode }))
    }
  } catch {
    notification.error(t('webhooks.notify.testError'))
  }
}

function handleTestFromModal(webhookId: number) {
  const webhook = webhooks.value.find((w) => w.id === webhookId)
  if (webhook) handleTest(webhook)
}

async function handleSave(data: { url: string; events: WebhookEvent[]; secret?: string }) {
  try {
    if (editingWebhook.value) {
      await webhookStore.updateWebhook(editingWebhook.value.id, {
        url: data.url,
        events: data.events,
      })
      notification.success(t('webhooks.notify.updated'))
    } else {
      await webhookStore.createWebhook(data)
      notification.success(t('webhooks.notify.created'))
    }
  } catch {
    notification.error(t('webhooks.notify.updateFailed'))
  }
}

async function handleRegenerateSecret(webhookId: number) {
  try {
    await webhookStore.regenerateSecret(webhookId)
    notification.success(t('webhooks.notify.secretRegenerated'))
  } catch {
    notification.error(t('webhooks.notify.secretRegenerateFailed'))
  }
}

function handleSelectWebhook(webhook: Webhook) {
  if (webhookStore.selectedWebhookId === webhook.id) {
    webhookStore.selectedWebhookId = null
  } else {
    webhookStore.selectedWebhookId = webhook.id
  }
}

async function handleRetry(webhookId: number, deliveryId: number) {
  try {
    const delivery = await webhookStore.retryDelivery(webhookId, deliveryId)
    if (delivery.statusCode >= 200 && delivery.statusCode < 300) {
      notification.success(t('webhooks.notify.retrySuccess', { code: delivery.statusCode }))
    } else {
      notification.error(t('webhooks.notify.retryFailed', { code: delivery.statusCode }))
    }
  } catch {
    notification.error(t('webhooks.notify.retryError'))
  }
}

function handleCloseDeliveryLog() {
  webhookStore.selectedWebhookId = null
}
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('webhooks.title')" :description="$t('webhooks.description')">
      <template #actions>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="handleCreate"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('webhooks.newWebhook') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('webhooks.pageGuideTitle')" :items="($tm('webhooks.pageGuide') as string[])" />

    <!-- Summary cards -->
    <div v-if="!isSourceEmpty" class="grid gap-2.5 tablet:grid-cols-3">
      <KpiCard :label="$t('webhooks.stats.total')" :value="String(summaryStats.total)" />
      <KpiCard :label="$t('webhooks.stats.active')" :value="String(summaryStats.active)" :delta-variant="summaryStats.active > 0 ? 'success' : 'muted'" />
      <KpiCard :label="$t('webhooks.stats.errors')" :value="String(summaryStats.failingCount)" :delta-variant="summaryStats.failingCount > 0 ? 'error' : 'success'" />
    </div>

    <!-- 검색 · 정렬 · 일괄 작업 -->
    <ListToolbar
      v-if="!isSourceEmpty"
      v-model="query"
      v-model:sort-key="sortKey"
      v-model:sort-dir="sortDir"
      :sort-options="sortOptions"
      :selected-count="selectedCount"
      :total-count="visibleCount"
      :search-placeholder="$t('webhooks.searchPlaceholder')"
      :search-label="$t('webhooks.searchLabel')"
      @clear-selection="clearSelection"
    >
      <template #filters>
        <button
          v-for="option in statusFilters"
          :key="option.value"
          type="button"
          :class="[
            'min-h-9 rounded-lg border px-3 py-2 text-body font-medium transition-colors',
            statusFilter === option.value
              ? 'border-accent bg-accent-dim text-accent'
              : 'border-line-control bg-surface-input text-content-secondary hover:bg-surface-raised hover:text-content'
          ]"
          @click="setStatusFilter(option.value)"
        >
          {{ option.label }}
        </button>
      </template>

      <template #bulk-actions>
        <button
          type="button"
          class="btn-danger inline-flex items-center gap-1.5"
          :disabled="bulkDeleting"
          @click="showBulkDeleteModal = true"
        >
          <TrashIcon class="h-4 w-4" aria-hidden="true" />
          {{ $t('list.bulkDelete') }}
        </button>
      </template>
    </ListToolbar>

    <!-- 전체 선택 -->
    <div v-if="!isSourceEmpty && visibleCount > 0" class="mb-3 flex items-center gap-2">
      <input
        id="webhooks-select-all"
        type="checkbox"
        :class="CHECKBOX_CLASS"
        :checked="allSelected"
        :indeterminate="someSelected"
        @change="toggleAll"
      />
      <label for="webhooks-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
        {{ $t('list.selectAll', { count: visibleCount }) }}
      </label>
    </div>

    <AsyncState
      :loading="webhookStore.loading && isSourceEmpty"
      :empty="isSourceEmpty"
      skeleton="card"
      :skeleton-count="3"
      :empty-icon="LinkIcon"
      :empty-title="$t('webhooks.emptyTitle')"
      :empty-description="$t('webhooks.emptyDescription')"
      :retryable="false"
    >
      <template #empty-action>
        <button class="btn-primary inline-flex items-center gap-2" @click="handleCreate">
          <PlusIcon class="h-5 w-5" />
          {{ $t('webhooks.addFirst') }}
        </button>
      </template>

      <!-- 검색·필터 결과만 비었을 때 -->
      <EmptyState
        v-if="isResultEmpty"
        :icon="MagnifyingGlassIcon"
        :title="$t('list.noResultsTitle')"
        :description="$t('list.noResultsDescription')"
        :action-label="$t('list.resetFilters')"
        @action="resetSearchAndFilters"
      />

      <template v-else>
        <!-- Webhook cards grid -->
        <div class="page-grid page-grid--cards mb-6">
          <div
            v-for="webhook in filtered"
            :key="webhook.id"
            class="flex items-start gap-2"
          >
            <input
              type="checkbox"
              :class="[CHECKBOX_CLASS, 'mt-5']"
              :checked="isSelected(webhook.id)"
              :aria-label="$t('list.selectItem', { name: webhook.url })"
              @change="toggle(webhook.id)"
            />
            <WebhookCard
              :webhook="webhook"
              class="min-w-0 flex-1"
              @edit="handleEdit"
              @delete="handleDeletePrompt"
              @test="handleTest"
              @toggle="handleToggle"
              @select="handleSelectWebhook"
            />
          </div>
        </div>

        <!-- Delivery log panel -->
        <div v-if="selectedWebhook" class="mb-6">
          <WebhookDeliveryLog
            :webhook="selectedWebhook"
            @retry="handleRetry"
            @close="handleCloseDeliveryLog"
          />
        </div>

        <!-- API documentation hint -->
        <div
          class="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-4 dark:border-gray-600 dark:bg-gray-900"
        >
          <div class="flex items-start gap-3">
            <BoltIcon class="mt-0.5 h-5 w-5 shrink-0 text-gray-400 dark:text-gray-500" />
            <div>
              <p class="text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('webhooks.integrationGuide') }}</p>
              <p class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">
                {{ $t('webhooks.integrationDescription') }}
                <a
                  href="/api/docs#webhooks"
                  class="font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
                >
                  {{ $t('webhooks.apiDocs') }}
                </a>
              </p>
            </div>
          </div>
        </div>
      </template>
    </AsyncState>

    <!-- Form Modal -->
    <WebhookFormModal
      v-model="showFormModal"
      :webhook="editingWebhook"
      @save="handleSave"
      @test="handleTestFromModal"
      @regenerate-secret="handleRegenerateSecret"
    />

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('webhooks.deleteTitle')"
      :message="deleteMessage"
      :confirm-text="$t('webhooks.deleteConfirm')"
      :danger="true"
      @confirm="handleDelete"
    />

    <!-- 선택 항목 일괄 삭제 확인 -->
    <ConfirmModal
      v-model="showBulkDeleteModal"
      :title="$t('webhooks.bulkDeleteTitle')"
      :message="$t('webhooks.bulkDeleteMessage', { count: selectedCount })"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="handleBulkDelete"
    />
  </div>
</template>
