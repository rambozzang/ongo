<script setup lang="ts">
import { ref, watch } from 'vue'
import { useApprovalStore } from '@/stores/approval'
import {
  XMarkIcon,
  PaperAirplaneIcon,
  VideoCameraIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  visible: boolean
  videoId?: number
  videoTitle?: string
  platforms?: string[]
  scheduledAt?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  close: []
  submitted: []
}>()

const approvalStore = useApprovalStore()

const comment = ref('')
const selectedReviewerId = ref('')
const submitting = ref(false)

// Mock team members who can review (admins and owners)
const reviewers = [
  { id: 'user-1', name: '김민수', role: '소유자', avatar: '#6366F1' },
  { id: 'user-2', name: '박지영', role: '관리자', avatar: '#EC4899' },
  { id: 'user-8', name: '조민준', role: '관리자', avatar: '#14B8A6' },
]

const platformLabels: Record<string, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
  naverclip: 'Naver Clip',
}

const platformColors: Record<string, string> = {
  youtube: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  tiktok: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  instagram: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  naverclip: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
}

watch(
  () => props.visible,
  (val) => {
    if (!val) {
      comment.value = ''
      selectedReviewerId.value = ''
      submitting.value = false
    }
  },
)

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${year}.${month}.${day} ${hours}:${minutes}`
}

function handleClose() {
  emit('update:visible', false)
  emit('close')
}

async function handleSubmit() {
  if (!props.videoId || !props.videoTitle) return

  submitting.value = true

  const reviewer = reviewers.find((r) => r.id === selectedReviewerId.value)

  try {
    approvalStore.submitForApproval(
      props.videoId,
      props.videoTitle,
      props.platforms || [],
      props.scheduledAt,
      comment.value || undefined,
      reviewer?.id,
      reviewer?.name,
    )
    emit('submitted')
    handleClose()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="submit-approval-modal-title"
      >
        <!-- Backdrop -->
        <div
          class="absolute inset-0 bg-black/50"
          aria-hidden="true"
          @click="handleClose"
        />

        <!-- Modal -->
        <div
          class="relative w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-gray-800"
          @keydown.escape="handleClose"
        >
          <!-- Header -->
          <div class="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-700">
            <h2 id="submit-approval-modal-title" class="text-lg font-semibold text-gray-900 dark:text-white">
              승인 요청 제출
            </h2>
            <button
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              aria-label="모달 닫기"
              @click="handleClose"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Content -->
          <div class="px-6 py-4">
            <!-- Video Info Summary -->
            <div class="mb-5 rounded-lg border border-gray-200 p-4 dark:border-gray-700">
              <div class="flex items-start gap-3">
                <div class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-700">
                  <VideoCameraIcon class="h-6 w-6 text-gray-400 dark:text-gray-500" />
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-white">
                    {{ videoTitle || '제목 없음' }}
                  </h3>
                  <div
                    v-if="platforms && platforms.length > 0"
                    class="mt-1.5 flex flex-wrap gap-1"
                  >
                    <span
                      v-for="platform in platforms"
                      :key="platform"
                      class="inline-flex items-center rounded-md px-1.5 py-0.5 text-[10px] font-medium"
                      :class="platformColors[platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'"
                    >
                      {{ platformLabels[platform] || platform }}
                    </span>
                  </div>
                  <p
                    v-if="scheduledAt"
                    class="mt-1 text-xs text-gray-500 dark:text-gray-400"
                  >
                    예약: {{ formatDate(scheduledAt) }}
                  </p>
                </div>
              </div>
            </div>

            <!-- Select Reviewer -->
            <div class="mb-4">
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                검토자 선택
              </label>
              <select
                v-model="selectedReviewerId"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
              >
                <option value="">
                  검토자를 선택하세요
                </option>
                <option
                  v-for="reviewer in reviewers"
                  :key="reviewer.id"
                  :value="reviewer.id"
                >
                  {{ reviewer.name }} ({{ reviewer.role }})
                </option>
              </select>
            </div>

            <!-- Comment -->
            <div class="mb-4">
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                검토자에게 전달할 메시지 (선택)
              </label>
              <textarea
                v-model="comment"
                placeholder="요청 사항이나 참고 사항을 입력하세요..."
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500"
                rows="3"
              />
            </div>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-end gap-2 border-t border-gray-200 px-6 py-4 dark:border-gray-700">
            <button
              class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              @click="handleClose"
            >
              취소
            </button>
            <button
              :disabled="submitting"
              class="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-primary-500 dark:hover:bg-primary-400"
              @click="handleSubmit"
            >
              <PaperAirplaneIcon class="h-4 w-4" />
              {{ submitting ? '제출 중...' : '승인 요청 제출' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
