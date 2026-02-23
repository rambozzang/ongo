<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  LinkIcon,
  BoltIcon,
  SignalIcon,
  ExclamationTriangleIcon,
  CodeBracketSquareIcon,
} from '@heroicons/vue/24/outline'
import { useWebhookStore } from '@/stores/webhooks'
import { useNotification } from '@/composables/useNotification'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import WebhookCard from '@/components/webhooks/WebhookCard.vue'
import WebhookFormModal from '@/components/webhooks/WebhookFormModal.vue'
import WebhookDeliveryLog from '@/components/webhooks/WebhookDeliveryLog.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import type { Webhook, WebhookEvent } from '@/types/webhook'

const { t } = useI18n({ useScope: 'global' })

// --- Store & composables ---
const webhookStore = useWebhookStore()
const notification = useNotification()

// --- State ---
const showFormModal = ref(false)
const showDeleteModal = ref(false)
const editingWebhook = ref<Webhook | null>(null)
const deletingWebhook = ref<Webhook | null>(null)

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

function handleDelete() {
  if (!deletingWebhook.value) return
  webhookStore.deleteWebhook(deletingWebhook.value.id)
  notification.success(t('webhooks.notify.deleted'))
  deletingWebhook.value = null
}

function handleToggle(webhook: Webhook) {
  webhookStore.toggleActive(webhook.id)
  const status = !webhook.isActive ? t('webhooks.notify.activated') : t('webhooks.notify.deactivated')
  notification.success(status)
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

function handleSave(data: { url: string; events: WebhookEvent[]; secret?: string }) {
  if (editingWebhook.value) {
    webhookStore.updateWebhook(editingWebhook.value.id, {
      url: data.url,
      events: data.events,
    })
    notification.success(t('webhooks.notify.updated'))
  } else {
    webhookStore.createWebhook(data)
    notification.success(t('webhooks.notify.created'))
  }
}

function handleRegenerateSecret(webhookId: number) {
  try {
    webhookStore.regenerateSecret(webhookId)
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
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('webhooks.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('webhooks.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="handleCreate"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('webhooks.newWebhook') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('webhooks.pageGuideTitle')" :items="($tm('webhooks.pageGuide') as string[])" />

    <!-- Empty State -->
    <EmptyState
      v-if="webhooks.length === 0"
      :title="$t('webhooks.emptyTitle')"
      :description="$t('webhooks.emptyDescription')"
      :icon="LinkIcon"
    >
      <template #action>
        <button class="btn-primary inline-flex items-center gap-2" @click="handleCreate">
          <PlusIcon class="h-5 w-5" />
          {{ $t('webhooks.addFirst') }}
        </button>
      </template>
    </EmptyState>

    <!-- Main content -->
    <div v-else>
      <!-- Summary cards -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3">
        <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('webhooks.stats.total') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
                {{ summaryStats.total }}
              </p>
            </div>
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900/30">
              <CodeBracketSquareIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
          </div>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('webhooks.stats.active') }}</p>
              <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
                {{ summaryStats.active }}
              </p>
            </div>
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
              <SignalIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
          </div>
        </div>
        <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('webhooks.stats.errors') }}</p>
              <p
                class="mt-1 text-2xl font-bold"
                :class="
                  summaryStats.failingCount > 0
                    ? 'text-red-600 dark:text-red-400'
                    : 'text-gray-900 dark:text-gray-100'
                "
              >
                {{ summaryStats.failingCount }}
              </p>
            </div>
            <div
              class="flex h-10 w-10 items-center justify-center rounded-full"
              :class="
                summaryStats.failingCount > 0
                  ? 'bg-red-100 dark:bg-red-900/30'
                  : 'bg-gray-100 dark:bg-gray-700'
              "
            >
              <ExclamationTriangleIcon
                class="h-5 w-5"
                :class="
                  summaryStats.failingCount > 0
                    ? 'text-red-600 dark:text-red-400'
                    : 'text-gray-400 dark:text-gray-500'
                "
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Webhook cards grid -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <WebhookCard
          v-for="webhook in webhooks"
          :key="webhook.id"
          :webhook="webhook"
          @edit="handleEdit"
          @delete="handleDeletePrompt"
          @test="handleTest"
          @toggle="handleToggle"
          @select="handleSelectWebhook"
        />
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
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('webhooks.integrationGuide') }}</p>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
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
    </div>

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
  </div>
</template>
