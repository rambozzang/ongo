<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { ApprovalRequest, ApprovalComment } from '@/types/approval'
import { useApprovalStore } from '@/stores/approval'
import { useTeamStore } from '@/stores/team'
import ApprovalTimeline from './ApprovalTimeline.vue'
import ApprovalChainProgress from './ApprovalChainProgress.vue'
import {
  XMarkIcon,
  CheckCircleIcon,
  XCircleIcon,
  PencilSquareIcon,
  CalendarDaysIcon,
  VideoCameraIcon,
  AtSymbolIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  visible: boolean
  requestId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  close: []
}>()

const approvalStore = useApprovalStore()
const teamStore = useTeamStore()

// @mention support
const mentionQuery = ref('')
const showMentionDropdown = ref(false)
const mentionDropdownIndex = ref(0)
const commentTextareaRef = ref<HTMLTextAreaElement | null>(null)
const mentionCursorPos = ref(0)

const mentionCandidates = computed(() => {
  if (!mentionQuery.value) return teamStore.members.slice(0, 5)
  const q = mentionQuery.value.toLowerCase()
  return teamStore.members
    .filter((m) => m.name.toLowerCase().includes(q) || m.email.toLowerCase().includes(q))
    .slice(0, 5)
})

function handleCommentInput(e: Event) {
  const textarea = e.target as HTMLTextAreaElement
  const value = textarea.value
  const cursor = textarea.selectionStart

  // Detect @ trigger
  const beforeCursor = value.substring(0, cursor)
  const atMatch = beforeCursor.match(/@(\w*)$/)

  if (atMatch) {
    mentionQuery.value = atMatch[1]
    mentionCursorPos.value = cursor - atMatch[0].length
    showMentionDropdown.value = true
    mentionDropdownIndex.value = 0
  } else {
    showMentionDropdown.value = false
  }
}

function handleCommentKeydown(e: KeyboardEvent) {
  if (!showMentionDropdown.value) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    mentionDropdownIndex.value = Math.min(
      mentionDropdownIndex.value + 1,
      mentionCandidates.value.length - 1,
    )
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    mentionDropdownIndex.value = Math.max(mentionDropdownIndex.value - 1, 0)
  } else if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    const candidate = mentionCandidates.value[mentionDropdownIndex.value]
    if (candidate) insertMention(candidate.name)
  } else if (e.key === 'Escape') {
    showMentionDropdown.value = false
  }
}

function insertMention(name: string) {
  const before = newComment.value.substring(0, mentionCursorPos.value)
  const after = newComment.value.substring(commentTextareaRef.value?.selectionStart ?? mentionCursorPos.value)
  newComment.value = `${before}@${name} ${after}`
  showMentionDropdown.value = false

  nextTick(() => {
    if (commentTextareaRef.value) {
      const pos = before.length + name.length + 2 // @name + space
      commentTextareaRef.value.selectionStart = pos
      commentTextareaRef.value.selectionEnd = pos
      commentTextareaRef.value.focus()
    }
  })
}

const request = computed<ApprovalRequest | undefined>(() => {
  if (!props.requestId) return undefined
  return approvalStore.getRequestById(props.requestId)
})

const requestComments = computed<ApprovalComment[]>(() => {
  if (!props.requestId) return []
  return approvalStore.getCommentsByApprovalId(props.requestId)
})

const newComment = ref('')
const actionMode = ref<'none' | 'reject' | 'revision'>('none')
const actionNote = ref('')

watch(
  () => props.visible,
  (val) => {
    if (val) {
      // Fetch team members for @mention
      if (teamStore.members.length === 0) {
        teamStore.fetchMembers()
      }
      // Fetch comments for this approval
      if (props.requestId) {
        approvalStore.fetchComments(props.requestId)
      }
    } else {
      newComment.value = ''
      actionMode.value = 'none'
      actionNote.value = ''
      showMentionDropdown.value = false
    }
  },
)

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

const statusConfig = computed(() => {
  if (!request.value) return { label: '', bgClass: '' }
  switch (request.value.status) {
    case 'pending':
      return {
        label: '검토 대기',
        bgClass: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
      }
    case 'approved':
      return {
        label: '승인됨',
        bgClass: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
      }
    case 'rejected':
      return {
        label: '반려됨',
        bgClass: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
      }
    case 'revision_requested':
      return {
        label: '수정 요청',
        bgClass: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
      }
    default:
      return { label: '', bgClass: '' }
  }
})

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

function handleApprove() {
  if (!request.value) return
  approvalStore.approveRequest(request.value.id, newComment.value || undefined)
  handleClose()
}

function handleReject() {
  if (!request.value || !actionNote.value.trim()) return
  approvalStore.rejectRequest(request.value.id, actionNote.value.trim())
  handleClose()
}

function handleRequestRevision() {
  if (!request.value || !actionNote.value.trim()) return
  approvalStore.requestRevision(request.value.id, actionNote.value.trim())
  handleClose()
}

function handleSubmitComment() {
  if (!request.value || !newComment.value.trim()) return
  approvalStore.addComment(request.value.id, newComment.value.trim())
  newComment.value = ''
}

function startReject() {
  actionMode.value = 'reject'
  actionNote.value = ''
}

function startRevision() {
  actionMode.value = 'revision'
  actionNote.value = ''
}

function cancelAction() {
  actionMode.value = 'none'
  actionNote.value = ''
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
        v-if="visible && request"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="approval-detail-modal-title"
      >
        <!-- Backdrop -->
        <div
          class="absolute inset-0 bg-black/50"
          aria-hidden="true"
          @click="handleClose"
        />

        <!-- Modal -->
        <div
          class="relative max-h-[90vh] w-full max-w-2xl overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-gray-800"
          @keydown.escape="handleClose"
        >
          <!-- Header -->
          <div class="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-700">
            <div class="flex items-center gap-3">
              <h2 id="approval-detail-modal-title" class="text-lg font-semibold text-gray-900 dark:text-white">
                승인 요청 상세
              </h2>
              <span
                class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                :class="statusConfig.bgClass"
              >
                {{ statusConfig.label }}
              </span>
            </div>
            <button
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              aria-label="모달 닫기"
              @click="handleClose"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Scrollable Content -->
          <div class="max-h-[calc(90vh-160px)] overflow-y-auto px-6 py-4">
            <!-- Video Preview Section -->
            <div class="mb-6">
              <div class="flex items-start gap-4">
                <div
                  class="h-24 w-40 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700"
                >
                  <img
                    v-if="request.videoThumbnail"
                    :src="request.videoThumbnail"
                    :alt="request.videoTitle"
                    class="h-full w-full object-cover"
                  />
                  <div
                    v-else
                    class="flex h-full w-full items-center justify-center"
                  >
                    <VideoCameraIcon class="h-10 w-10 text-gray-400 dark:text-gray-500" />
                  </div>
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="text-base font-semibold text-gray-900 dark:text-white">
                    {{ request.videoTitle }}
                  </h3>
                  <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                    요청자: {{ request.requesterName }}
                  </p>
                  <p
                    v-if="request.reviewerName"
                    class="text-sm text-gray-500 dark:text-gray-400"
                  >
                    검토자: {{ request.reviewerName }}
                  </p>
                </div>
              </div>
            </div>

            <!-- Metadata Section -->
            <div class="mb-6 rounded-lg border border-gray-200 p-4 dark:border-gray-700">
              <h4 class="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
                게시 정보
              </h4>
              <div class="space-y-3">
                <!-- Platforms -->
                <div class="flex items-center gap-2">
                  <span class="w-20 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
                    플랫폼
                  </span>
                  <div class="flex flex-wrap gap-1.5">
                    <span
                      v-for="platform in request.platforms"
                      :key="platform"
                      class="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium"
                      :class="platformColors[platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'"
                    >
                      {{ platformLabels[platform] || platform }}
                    </span>
                  </div>
                </div>

                <!-- Scheduled date -->
                <div
                  v-if="request.scheduledAt"
                  class="flex items-center gap-2"
                >
                  <span class="w-20 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
                    예약 게시
                  </span>
                  <div class="flex items-center gap-1 text-sm text-gray-700 dark:text-gray-300">
                    <CalendarDaysIcon class="h-4 w-4 text-gray-400" />
                    {{ formatDate(request.scheduledAt) }}
                  </div>
                </div>

                <!-- Request date -->
                <div class="flex items-center gap-2">
                  <span class="w-20 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
                    요청일시
                  </span>
                  <span class="text-sm text-gray-700 dark:text-gray-300">
                    {{ formatDate(request.requestedAt) }}
                  </span>
                </div>

                <!-- Decision date -->
                <div
                  v-if="request.decidedAt"
                  class="flex items-center gap-2"
                >
                  <span class="w-20 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
                    결정일시
                  </span>
                  <span class="text-sm text-gray-700 dark:text-gray-300">
                    {{ formatDate(request.decidedAt) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Changes Diff Section -->
            <div
              v-if="request.changes && request.changes.length > 0"
              class="mb-6"
            >
              <h4 class="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
                변경 사항
              </h4>
              <div class="space-y-2">
                <div
                  v-for="change in request.changes"
                  :key="change.field"
                  class="rounded-lg border border-gray-200 p-3 dark:border-gray-700"
                >
                  <span class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
                    {{ change.label }}
                  </span>
                  <div class="flex items-center gap-2 text-sm">
                    <span class="rounded bg-red-50 px-2 py-0.5 text-red-700 line-through dark:bg-red-900/20 dark:text-red-300">
                      {{ change.oldValue }}
                    </span>
                    <svg
                      class="h-4 w-4 flex-shrink-0 text-gray-400"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M13 7l5 5m0 0l-5 5m5-5H6"
                      />
                    </svg>
                    <span class="rounded bg-green-50 px-2 py-0.5 text-green-700 dark:bg-green-900/20 dark:text-green-300">
                      {{ change.newValue }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Multi-step Approval Chain Progress -->
            <div class="mb-6">
              <h4 class="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
                승인 단계
              </h4>
              <ApprovalChainProgress :approval-id="Number(request.id)" />
            </div>

            <!-- Timeline Section -->
            <div class="mb-6">
              <h4 class="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
                타임라인
              </h4>
              <ApprovalTimeline
                :request="request"
                :comments="requestComments"
              />
            </div>

            <!-- Action Note (for reject / revision request) -->
            <div v-if="actionMode !== 'none'" class="mb-4">
              <div class="rounded-lg border-2 p-4" :class="actionMode === 'reject' ? 'border-red-300 dark:border-red-700' : 'border-orange-300 dark:border-orange-700'">
                <h4 class="mb-2 text-sm font-semibold" :class="actionMode === 'reject' ? 'text-red-700 dark:text-red-300' : 'text-orange-700 dark:text-orange-300'">
                  {{ actionMode === 'reject' ? '반려 사유' : '수정 요청 사항' }}
                </h4>
                <textarea
                  v-model="actionNote"
                  :placeholder="actionMode === 'reject' ? '반려 사유를 입력하세요...' : '수정이 필요한 내용을 입력하세요...'"
                  class="input"
                  rows="3"
                />
                <div class="mt-2 flex items-center gap-2">
                  <button
                    :disabled="!actionNote.trim()"
                    class="rounded-lg px-3 py-1.5 text-xs font-medium text-white transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                    :class="actionMode === 'reject' ? 'bg-red-600 hover:bg-red-700 dark:bg-red-500 dark:hover:bg-red-400' : 'bg-orange-600 hover:bg-orange-700 dark:bg-orange-500 dark:hover:bg-orange-400'"
                    @click="actionMode === 'reject' ? handleReject() : handleRequestRevision()"
                  >
                    {{ actionMode === 'reject' ? '반려 확인' : '수정 요청 확인' }}
                  </button>
                  <button
                    class="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                    @click="cancelAction"
                  >
                    취소
                  </button>
                </div>
              </div>
            </div>

            <!-- Comment Input with @mention -->
            <div class="mb-4">
              <div class="relative">
                <div class="flex items-start gap-2">
                  <div class="relative flex-1">
                    <textarea
                      ref="commentTextareaRef"
                      v-model="newComment"
                      placeholder="코멘트를 입력하세요... (@로 멤버 멘션)"
                      class="input"
                      rows="2"
                      @input="handleCommentInput"
                      @keydown="handleCommentKeydown"
                      @keydown.meta.enter="handleSubmitComment"
                      @keydown.ctrl.enter="handleSubmitComment"
                    />
                    <!-- @mention dropdown -->
                    <Transition
                      enter-active-class="transition duration-100 ease-out"
                      enter-from-class="scale-95 opacity-0"
                      enter-to-class="scale-100 opacity-100"
                      leave-active-class="transition duration-75 ease-in"
                      leave-from-class="scale-100 opacity-100"
                      leave-to-class="scale-95 opacity-0"
                    >
                      <div
                        v-if="showMentionDropdown && mentionCandidates.length > 0"
                        class="absolute bottom-full left-0 z-10 mb-1 w-60 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-lg dark:border-gray-600 dark:bg-gray-700"
                      >
                        <div class="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                          멤버 멘션
                        </div>
                        <button
                          v-for="(member, idx) in mentionCandidates"
                          :key="member.id"
                          class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition-colors"
                          :class="idx === mentionDropdownIndex
                            ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                            : 'text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-600'"
                          @click="insertMention(member.name)"
                          @mouseenter="mentionDropdownIndex = idx"
                        >
                          <div
                            class="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full text-[10px] font-bold text-white"
                            :style="{ backgroundColor: member.avatar || '#6B7280' }"
                          >
                            {{ member.name.charAt(0) }}
                          </div>
                          <div class="min-w-0 flex-1">
                            <p class="truncate text-sm font-medium">{{ member.name }}</p>
                            <p class="truncate text-[11px] text-gray-400 dark:text-gray-500">
                              {{ member.email }}
                            </p>
                          </div>
                        </button>
                      </div>
                    </Transition>
                  </div>
                  <div class="flex flex-shrink-0 flex-col gap-1">
                    <button
                      :disabled="!newComment.trim()"
                      class="btn-primary px-3"
                      @click="handleSubmitComment"
                    >
                      전송
                    </button>
                    <button
                      class="rounded-lg border border-gray-200 p-2 text-gray-400 transition-colors hover:text-primary-500 dark:border-gray-600 dark:hover:text-primary-400"
                      title="멤버 멘션 (@)"
                      @click="() => {
                        newComment += '@'
                        showMentionDropdown = true
                        mentionQuery = ''
                        mentionCursorPos = newComment.length - 1
                        nextTick(() => commentTextareaRef?.focus())
                      }"
                    >
                      <AtSymbolIcon class="h-4 w-4" />
                    </button>
                  </div>
                </div>
                <p class="mt-1 text-[11px] text-gray-400 dark:text-gray-500">
                  Cmd+Enter로 전송 / @로 멤버 멘션
                </p>
              </div>
            </div>
          </div>

          <!-- Footer Actions -->
          <div
            v-if="request.status === 'pending'"
            class="flex items-center justify-end gap-2 border-t border-gray-200 px-6 py-4 dark:border-gray-700"
          >
            <template v-if="actionMode === 'none'">
              <button
                class="inline-flex items-center gap-1.5 rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-400"
                @click="handleApprove"
              >
                <CheckCircleIcon class="h-4 w-4" />
                승인
              </button>
              <button
                class="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-red-700 dark:bg-red-500 dark:hover:bg-red-400"
                @click="startReject"
              >
                <XCircleIcon class="h-4 w-4" />
                반려
              </button>
              <button
                class="inline-flex items-center gap-1.5 rounded-lg border border-orange-300 px-4 py-2 text-sm font-medium text-orange-700 transition-colors hover:bg-orange-50 dark:border-orange-600 dark:text-orange-400 dark:hover:bg-orange-900/20"
                @click="startRevision"
              >
                <PencilSquareIcon class="h-4 w-4" />
                수정 요청
              </button>
            </template>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
