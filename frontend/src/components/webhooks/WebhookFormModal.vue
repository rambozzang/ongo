<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  XMarkIcon,
  ClipboardDocumentIcon,
  ArrowPathIcon,
  PaperAirplaneIcon,
  CheckIcon,
  EyeIcon,
  EyeSlashIcon,
} from '@heroicons/vue/24/outline'
import type { Webhook, WebhookEvent } from '@/types/webhook'
import { WEBHOOK_EVENT_LABELS } from '@/types/webhook'

const props = defineProps<{
  modelValue: boolean
  webhook?: Webhook | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [data: { url: string; events: WebhookEvent[]; secret?: string }]
  test: [webhookId: number]
  regenerateSecret: [webhookId: number]
}>()

const isEditing = computed(() => !!props.webhook)
const modalTitle = computed(() => (isEditing.value ? '웹훅 수정' : '새 웹훅 추가'))

const url = ref('')
const selectedEvents = ref<WebhookEvent[]>([])
const secret = ref('')
const secretVisible = ref(false)
const urlError = ref('')
const eventsError = ref('')
const copied = ref(false)

const allEvents: WebhookEvent[] = [
  'video.uploaded',
  'video.published',
  'video.failed',
  'schedule.executed',
  'analytics.milestone',
  'credit.low',
  'subscription.changed',
]

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      if (props.webhook) {
        url.value = props.webhook.url
        selectedEvents.value = [...props.webhook.events]
        secret.value = props.webhook.secret || ''
      } else {
        url.value = ''
        selectedEvents.value = []
        secret.value = ''
      }
      urlError.value = ''
      eventsError.value = ''
      secretVisible.value = false
      copied.value = false
    }
  },
)

function validateUrl(): boolean {
  if (!url.value.trim()) {
    urlError.value = 'URL을 입력해주세요'
    return false
  }
  try {
    const parsed = new URL(url.value)
    if (!['http:', 'https:'].includes(parsed.protocol)) {
      urlError.value = 'HTTP 또는 HTTPS URL만 지원합니다'
      return false
    }
    urlError.value = ''
    return true
  } catch {
    urlError.value = '올바른 URL 형식이 아닙니다'
    return false
  }
}

function validateEvents(): boolean {
  if (selectedEvents.value.length === 0) {
    eventsError.value = '최소 1개의 이벤트를 선택해주세요'
    return false
  }
  eventsError.value = ''
  return true
}

function toggleEvent(event: WebhookEvent) {
  const index = selectedEvents.value.indexOf(event)
  if (index === -1) {
    selectedEvents.value.push(event)
  } else {
    selectedEvents.value.splice(index, 1)
  }
  if (eventsError.value) validateEvents()
}

function isEventSelected(event: WebhookEvent): boolean {
  return selectedEvents.value.includes(event)
}

function handleSave() {
  const urlValid = validateUrl()
  const eventsValid = validateEvents()
  if (!urlValid || !eventsValid) return

  emit('save', {
    url: url.value.trim(),
    events: [...selectedEvents.value],
    secret: secret.value || undefined,
  })
  close()
}

function close() {
  emit('update:modelValue', false)
}

function handleTest() {
  if (props.webhook) {
    emit('test', props.webhook.id)
  }
}

function handleRegenerateSecret() {
  if (props.webhook) {
    emit('regenerateSecret', props.webhook.id)
  }
}

async function copySecret() {
  if (!secret.value) return
  try {
    await navigator.clipboard.writeText(secret.value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    // Fallback: no clipboard API available
  }
}

function selectAll() {
  selectedEvents.value = [...allEvents]
  if (eventsError.value) validateEvents()
}

function deselectAll() {
  selectedEvents.value = []
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="webhook-form-modal-title"
    >
      <!-- Backdrop -->
      <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="close" />

      <!-- Modal -->
      <div
        class="relative max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800"
        @keydown.escape="close"
      >
        <!-- Header -->
        <div class="mb-5 flex items-center justify-between">
          <h3 id="webhook-form-modal-title" class="text-lg font-semibold text-gray-900 dark:text-white">
            {{ modalTitle }}
          </h3>
          <button
            class="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
            aria-label="모달 닫기"
            @click="close"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- URL Input -->
        <div class="mb-5">
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            엔드포인트 URL
          </label>
          <input
            v-model="url"
            type="url"
            placeholder="https://example.com/webhooks"
            class="w-full rounded-lg border px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:bg-gray-900 dark:text-white dark:placeholder-gray-500"
            :class="
              urlError
                ? 'border-red-300 dark:border-red-600'
                : 'border-gray-300 dark:border-gray-600'
            "
            @blur="validateUrl"
            @input="urlError = ''"
          />
          <p v-if="urlError" class="mt-1 text-xs text-red-500 dark:text-red-400">
            {{ urlError }}
          </p>
        </div>

        <!-- Event Selection -->
        <div class="mb-5">
          <div class="mb-1.5 flex items-center justify-between">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              이벤트 선택
            </label>
            <div class="flex gap-2">
              <button
                class="text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
                @click="selectAll"
              >
                전체 선택
              </button>
              <span class="text-xs text-gray-300 dark:text-gray-600">|</span>
              <button
                class="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
                @click="deselectAll"
              >
                전체 해제
              </button>
            </div>
          </div>

          <div class="space-y-2">
            <label
              v-for="event in allEvents"
              :key="event"
              class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5 transition-colors"
              :class="
                isEventSelected(event)
                  ? 'border-primary-300 bg-primary-50 dark:border-primary-700 dark:bg-primary-900/20'
                  : 'border-gray-200 bg-white hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:hover:bg-gray-750'
              "
            >
              <input
                type="checkbox"
                :checked="isEventSelected(event)"
                class="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-900"
                @change="toggleEvent(event)"
              />
              <div>
                <span class="block text-sm font-medium text-gray-900 dark:text-white">
                  {{ WEBHOOK_EVENT_LABELS[event].label }}
                </span>
                <span class="block text-xs text-gray-500 dark:text-gray-400">
                  {{ WEBHOOK_EVENT_LABELS[event].description }}
                </span>
              </div>
            </label>
          </div>
          <p v-if="eventsError" class="mt-1 text-xs text-red-500 dark:text-red-400">
            {{ eventsError }}
          </p>
        </div>

        <!-- Secret Key -->
        <div class="mb-5">
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            서명 시크릿 키
          </label>
          <p class="mb-2 text-xs text-gray-500 dark:text-gray-400">
            웹훅 페이로드의 서명을 검증하는 데 사용됩니다.
          </p>
          <div v-if="isEditing && secret" class="flex items-center gap-2">
            <div
              class="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-gray-300 bg-gray-50 px-3 py-2 dark:border-gray-600 dark:bg-gray-900"
            >
              <code class="flex-1 truncate text-xs text-gray-700 dark:text-gray-300">
                {{ secretVisible ? secret : '••••••••••••••••••••••••' }}
              </code>
              <button
                class="shrink-0 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                :title="secretVisible ? '숨기기' : '보기'"
                @click="secretVisible = !secretVisible"
              >
                <EyeSlashIcon v-if="secretVisible" class="h-4 w-4" />
                <EyeIcon v-else class="h-4 w-4" />
              </button>
              <button
                class="shrink-0 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                title="복사"
                @click="copySecret"
              >
                <CheckIcon v-if="copied" class="h-4 w-4 text-green-500" />
                <ClipboardDocumentIcon v-else class="h-4 w-4" />
              </button>
            </div>
            <button
              class="inline-flex shrink-0 items-center gap-1 rounded-lg border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              @click="handleRegenerateSecret"
            >
              <ArrowPathIcon class="h-3.5 w-3.5" />
              재생성
            </button>
          </div>
          <p v-else-if="!isEditing" class="text-xs text-gray-500 dark:text-gray-400">
            저장 시 자동으로 생성됩니다.
          </p>
        </div>

        <!-- Test Button (editing only) -->
        <div
          v-if="isEditing"
          class="mb-5 rounded-lg border border-dashed border-gray-300 bg-gray-50 p-3 dark:border-gray-600 dark:bg-gray-900"
        >
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-gray-700 dark:text-gray-300">웹훅 테스트</p>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                테스트 이벤트를 전송하여 연결을 확인합니다.
              </p>
            </div>
            <button
              class="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700"
              @click="handleTest"
            >
              <PaperAirplaneIcon class="h-3.5 w-3.5" />
              테스트 전송
            </button>
          </div>
        </div>

        <!-- Footer Buttons -->
        <div class="flex justify-end gap-3 border-t border-gray-200 pt-4 dark:border-gray-700">
          <button
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="close"
          >
            취소
          </button>
          <button
            class="btn-primary"
            @click="handleSave"
          >
            {{ isEditing ? '저장' : '추가' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
