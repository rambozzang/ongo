<script setup lang="ts">
import { ref, computed } from 'vue'
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
  return `"${deletingWebhook.value.url}" 웹훅을 삭제하시겠습니까?\n\n삭제 후에는 이벤트가 더 이상 전달되지 않습니다.`
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
  notification.success('웹훅이 삭제되었습니다.')
  deletingWebhook.value = null
}

function handleToggle(webhook: Webhook) {
  webhookStore.toggleActive(webhook.id)
  const status = !webhook.isActive ? '활성화' : '비활성화'
  notification.success(`웹훅이 ${status}되었습니다.`)
}

async function handleTest(webhook: Webhook) {
  try {
    const delivery = await webhookStore.testWebhook(webhook.id)
    if (delivery.statusCode >= 200 && delivery.statusCode < 300) {
      notification.success(`테스트 전송 성공 (${delivery.statusCode}, ${delivery.duration}ms)`)
    } else {
      notification.error(`테스트 전송 실패 (${delivery.statusCode})`)
    }
  } catch {
    notification.error('테스트 전송 중 오류가 발생했습니다.')
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
    notification.success('웹훅이 수정되었습니다.')
  } else {
    webhookStore.createWebhook(data)
    notification.success('새 웹훅이 추가되었습니다.')
  }
}

function handleRegenerateSecret(webhookId: number) {
  try {
    webhookStore.regenerateSecret(webhookId)
    notification.success('시크릿 키가 재생성되었습니다.')
  } catch {
    notification.error('시크릿 키 재생성에 실패했습니다.')
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
      notification.success(`재시도 성공 (${delivery.statusCode})`)
    } else {
      notification.error(`재시도 실패 (${delivery.statusCode})`)
    }
  } catch {
    notification.error('재시도 중 오류가 발생했습니다.')
  }
}

function handleCloseDeliveryLog() {
  webhookStore.selectedWebhookId = null
}
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex items-start justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">웹훅 관리</h1>
        <p class="mt-1 text-sm text-gray-600 dark:text-gray-400">
          외부 서비스에 실시간 이벤트를 전달합니다
        </p>
      </div>
      <button
        class="btn-primary inline-flex items-center gap-2"
        @click="handleCreate"
      >
        <PlusIcon class="h-5 w-5" />
        새 웹훅
      </button>
    </div>

    <PageGuide title="웹훅" :items="[
      '새 웹훅 버튼으로 외부 서비스에 연결할 엔드포인트 URL을 등록하세요',
      '웹훅 생성/수정 시 수신할 이벤트(업로드·게시·예약 실행·댓글 등)를 선택하고, 재시도 정책을 설정하세요',
      '상단 요약에서 전체 웹훅 수·활성 수·실패 수를 확인하세요',
      '각 웹훅 카드에서 활성/비활성 토글로 전송을 제어하고, 테스트 버튼으로 연결 상태를 검증하세요',
      '전송 로그에서 요청/응답 상세(상태 코드·소요 시간)를 확인하여 문제를 디버깅하세요',
    ]" />

    <!-- Empty State -->
    <EmptyState
      v-if="webhooks.length === 0"
      title="등록된 웹훅이 없어요"
      description="웹훅을 추가하면 영상 업로드, 게시, 예약 실행 등의 이벤트를 외부 서비스에 실시간으로 전달할 수 있어요."
      :icon="LinkIcon"
    >
      <template #action>
        <button class="btn-primary inline-flex items-center gap-2" @click="handleCreate">
          <PlusIcon class="h-5 w-5" />
          첫 번째 웹훅 추가하기
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
              <p class="text-sm text-gray-600 dark:text-gray-400">전체 웹훅</p>
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
              <p class="text-sm text-gray-600 dark:text-gray-400">활성 상태</p>
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
              <p class="text-sm text-gray-600 dark:text-gray-400">오류 발생</p>
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
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">웹훅 연동 가이드</p>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              웹훅 페이로드는 JSON 형식으로 전달되며, <code class="rounded bg-gray-200 px-1 py-0.5 dark:bg-gray-700">X-Webhook-Signature</code> 헤더를 통해
              서명을 검증할 수 있습니다. 자세한 내용은
              <a
                href="/api/docs#webhooks"
                class="font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
              >
                API 문서
              </a>
              를 참고하세요.
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
      title="웹훅 삭제"
      :message="deleteMessage"
      confirm-text="삭제"
      :danger="true"
      @confirm="handleDelete"
    />
  </div>
</template>
