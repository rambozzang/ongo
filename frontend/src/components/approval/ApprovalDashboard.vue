<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useApprovalStore } from '@/stores/approval'
import type { ApprovalStatus, ApprovalRequest as ApprovalRequestType, ApprovalChainSlaStatus } from '@/types/approval'
import { approvalApi } from '@/api/approval'
import ApprovalRequestCard from './ApprovalRequestCard.vue'
import ApprovalDetailModal from './ApprovalDetailModal.vue'
import SubmitApprovalModal from './SubmitApprovalModal.vue'
import {
  ClockIcon,
  CheckCircleIcon,
  XCircleIcon,
  PencilSquareIcon,
  FunnelIcon,
  PlusIcon,
  InboxIcon,
  ViewColumnsIcon,
  ListBulletIcon,
} from '@heroicons/vue/24/outline'

const approvalStore = useApprovalStore()

const activeTab = ref<'pending_review' | 'my_requests' | 'all'>('pending_review')
const statusFilter = ref<ApprovalStatus | ''>('')
const detailModalVisible = ref(false)
const selectedRequestId = ref<string | null>(null)
const submitModalVisible = ref(false)
const viewMode = ref<'list' | 'kanban'>('kanban')

// SLA countdown timer
const now = ref(Date.now())
let slaInterval: ReturnType<typeof setInterval> | null = null

// SLA data cache
const slaDataMap = ref<Record<string, ApprovalChainSlaStatus[]>>({})

onMounted(() => {
  approvalStore.fetchRequests()
  slaInterval = setInterval(() => {
    now.value = Date.now()
  }, 60_000) // update every minute
})

onUnmounted(() => {
  if (slaInterval) clearInterval(slaInterval)
})

const tabs = computed(() => [
  {
    key: 'pending_review' as const,
    label: '검토 대기',
    count: approvalStore.pendingRequests.length,
  },
  {
    key: 'my_requests' as const,
    label: '내가 요청한',
    count: approvalStore.myRequests.length,
  },
  {
    key: 'all' as const,
    label: '전체',
    count: approvalStore.requests.length,
  },
])

const filteredRequests = computed(() => {
  return approvalStore.getFilteredRequests(
    activeTab.value,
    statusFilter.value || undefined,
  )
})

// Kanban columns
const kanbanColumns = computed(() => {
  const allRequests = approvalStore.requests
  return [
    {
      key: 'pending',
      title: '대기중',
      colorClass: 'border-yellow-400',
      headerBg: 'bg-yellow-50 dark:bg-yellow-900/20',
      headerText: 'text-yellow-700 dark:text-yellow-300',
      icon: ClockIcon,
      requests: allRequests.filter((r) => r.status === 'pending' || r.status === 'revision_requested'),
    },
    {
      key: 'in_review',
      title: '검토중',
      colorClass: 'border-blue-400',
      headerBg: 'bg-blue-50 dark:bg-blue-900/20',
      headerText: 'text-blue-700 dark:text-blue-300',
      icon: PencilSquareIcon,
      requests: allRequests.filter((r) => {
        // Requests that have chain steps in progress
        const sla = slaDataMap.value[r.id]
        return sla && sla.some(s => s.status === 'PENDING')
      }),
    },
    {
      key: 'completed',
      title: '완료',
      colorClass: 'border-green-400',
      headerBg: 'bg-green-50 dark:bg-green-900/20',
      headerText: 'text-green-700 dark:text-green-300',
      icon: CheckCircleIcon,
      requests: allRequests.filter((r) => r.status === 'approved' || r.status === 'rejected'),
    },
  ]
})

const stats = computed(() => approvalStore.stats)

const statCards = computed(() => [
  {
    label: '대기중',
    value: stats.value.pending,
    icon: ClockIcon,
    colorClass: 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400',
    valueClass: 'text-yellow-600 dark:text-yellow-400',
  },
  {
    label: '승인됨',
    value: stats.value.approved,
    icon: CheckCircleIcon,
    colorClass: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
    valueClass: 'text-green-600 dark:text-green-400',
  },
  {
    label: '반려됨',
    value: stats.value.rejected,
    icon: XCircleIcon,
    colorClass: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
    valueClass: 'text-red-600 dark:text-red-400',
  },
  {
    label: '수정요청',
    value: stats.value.revisionRequested,
    icon: PencilSquareIcon,
    colorClass: 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400',
    valueClass: 'text-orange-600 dark:text-orange-400',
  },
])

const statusFilterOptions: { value: ApprovalStatus | ''; label: string }[] = [
  { value: '', label: '전체 상태' },
  { value: 'pending', label: '검토 대기' },
  { value: 'approved', label: '승인됨' },
  { value: 'rejected', label: '반려됨' },
  { value: 'revision_requested', label: '수정 요청' },
]

function getCardRole(request: { requesterId: string }): 'requester' | 'reviewer' {
  let userId = ''
  try {
    const token = localStorage.getItem('accessToken')
    if (token) {
      const payload = JSON.parse(atob(token.split('.')[1]))
      userId = String(payload.sub || payload.userId || '')
    }
  } catch { /* ignore */ }
  return request.requesterId === userId
    ? 'requester'
    : 'reviewer'
}

function openDetail(requestId: string) {
  selectedRequestId.value = requestId
  detailModalVisible.value = true
}

function handleApprove(id: string) {
  approvalStore.approveRequest(id)
}

function handleReject(id: string) {
  selectedRequestId.value = id
  detailModalVisible.value = true
}

function handleRequestRevision(id: string) {
  selectedRequestId.value = id
  detailModalVisible.value = true
}

function handleResubmit(id: string) {
  approvalStore.resubmitRequest(id, '수정을 완료하여 다시 제출합니다.')
}

function getSlaCountdown(request: ApprovalRequestType): string | null {
  const sla = slaDataMap.value[request.id]
  if (!sla || sla.length === 0) return null

  const pendingStep = sla.find(s => s.status === 'PENDING')
  if (!pendingStep || !pendingStep.remainingMinutes) return null

  const remaining = pendingStep.remainingMinutes
  if (remaining <= 0) return '마감 초과'
  if (remaining < 60) return `${remaining}분 남음`
  const hours = Math.floor(remaining / 60)
  const minutes = remaining % 60
  return `${hours}시간 ${minutes}분 남음`
}

function getSlaUrgency(request: ApprovalRequestType): string {
  const sla = slaDataMap.value[request.id]
  if (!sla || sla.length === 0) return ''
  const pendingStep = sla.find(s => s.status === 'PENDING')
  if (!pendingStep) return ''
  if (pendingStep.isOverdue) return 'text-red-600 dark:text-red-400'
  if (pendingStep.remainingMinutes != null && pendingStep.remainingMinutes < 60) return 'text-orange-600 dark:text-orange-400'
  return 'text-gray-500 dark:text-gray-400'
}

function getChainProgress(request: ApprovalRequestType): { completed: number; total: number } | null {
  const sla = slaDataMap.value[request.id]
  if (!sla || sla.length <= 1) return null
  const completed = sla.filter(s => s.status === 'APPROVED').length
  return { completed, total: sla.length }
}

async function loadSlaData(approvalId: number) {
  try {
    const data = await approvalApi.getSlaStatus(approvalId)
    slaDataMap.value[String(approvalId)] = data
  } catch {
    // SLA data not available
  }
}

// Load SLA data for pending requests
onMounted(async () => {
  // Wait for requests to load, then fetch SLA for each
  await approvalStore.fetchRequests()
  for (const r of approvalStore.requests) {
    if (r.status === 'pending' || r.status === 'revision_requested') {
      loadSlaData(Number(r.id))
    }
  }
})

const emptyMessages: Record<string, { title: string; description: string }> = {
  pending_review: {
    title: '검토 대기 중인 요청이 없습니다',
    description: '새로운 승인 요청이 들어오면 여기에 표시됩니다.',
  },
  my_requests: {
    title: '요청한 항목이 없습니다',
    description: '비디오를 게시하기 전에 승인 요청을 제출해보세요.',
  },
  all: {
    title: '승인 요청이 없습니다',
    description: '아직 생성된 승인 요청이 없습니다.',
  },
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white">
          게시 승인
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠 게시 전 승인 워크플로를 관리합니다.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <!-- View Mode Toggle -->
        <div class="flex rounded-lg border border-gray-200 dark:border-gray-700">
          <button
            class="rounded-l-lg px-2.5 py-1.5 transition-colors"
            :class="viewMode === 'list'
              ? 'bg-primary-500 text-white'
              : 'text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
            @click="viewMode = 'list'"
          >
            <ListBulletIcon class="h-4 w-4" />
          </button>
          <button
            class="rounded-r-lg px-2.5 py-1.5 transition-colors"
            :class="viewMode === 'kanban'
              ? 'bg-primary-500 text-white'
              : 'text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'"
            @click="viewMode = 'kanban'"
          >
            <ViewColumnsIcon class="h-4 w-4" />
          </button>
        </div>
        <button
          class="inline-flex items-center gap-1.5 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-400"
          @click="submitModalVisible = true"
        >
          <PlusIcon class="h-4 w-4" />
          승인 요청
        </button>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div
        v-for="stat in statCards"
        :key="stat.label"
        class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
      >
        <div class="flex items-center gap-3">
          <div
            class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg"
            :class="stat.colorClass"
          >
            <component :is="stat.icon" class="h-5 w-5" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              {{ stat.label }}
            </p>
            <p class="text-xl font-bold" :class="stat.valueClass">
              {{ stat.value }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== KANBAN VIEW ===== -->
    <template v-if="viewMode === 'kanban'">
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div
          v-for="column in kanbanColumns"
          :key="column.key"
          class="flex flex-col rounded-xl border-t-4 bg-gray-50 dark:bg-gray-900/50"
          :class="column.colorClass"
        >
          <!-- Column Header -->
          <div
            class="flex items-center justify-between rounded-t-lg px-4 py-3"
            :class="column.headerBg"
          >
            <div class="flex items-center gap-2">
              <component :is="column.icon" class="h-4 w-4" :class="column.headerText" />
              <span class="text-sm font-semibold" :class="column.headerText">
                {{ column.title }}
              </span>
            </div>
            <span
              class="inline-flex h-5 min-w-[20px] items-center justify-center rounded-full px-1.5 text-[11px] font-bold"
              :class="column.headerBg + ' ' + column.headerText"
            >
              {{ column.requests.length }}
            </span>
          </div>

          <!-- Column Body -->
          <div class="flex-1 space-y-2 overflow-y-auto p-3" style="max-height: 600px;">
            <div
              v-for="request in column.requests"
              :key="request.id"
              class="cursor-pointer rounded-lg border border-gray-200 bg-white p-3 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-800"
              @click="openDetail(request.id)"
            >
              <!-- Title -->
              <p class="text-sm font-medium text-gray-900 dark:text-white line-clamp-1">
                {{ request.videoTitle }}
              </p>

              <!-- Requester -->
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ request.requesterName }}
              </p>

              <!-- Chain Progress Indicator -->
              <div v-if="getChainProgress(request)" class="mt-2">
                <div class="flex items-center gap-1.5">
                  <div class="flex-1">
                    <div class="flex gap-0.5">
                      <div
                        v-for="i in getChainProgress(request)!.total"
                        :key="i"
                        class="h-1.5 flex-1 rounded-full transition-colors"
                        :class="i <= getChainProgress(request)!.completed
                          ? 'bg-green-500'
                          : i === getChainProgress(request)!.completed + 1
                            ? 'bg-yellow-400 animate-pulse'
                            : 'bg-gray-200 dark:bg-gray-700'"
                      />
                    </div>
                  </div>
                  <span class="text-[10px] font-medium text-gray-500 dark:text-gray-400">
                    {{ getChainProgress(request)!.completed }}/{{ getChainProgress(request)!.total }}
                  </span>
                </div>
              </div>

              <!-- SLA Countdown -->
              <div
                v-if="getSlaCountdown(request)"
                class="mt-2 flex items-center gap-1"
              >
                <ClockIcon class="h-3 w-3" :class="getSlaUrgency(request)" />
                <span class="text-[11px] font-medium" :class="getSlaUrgency(request)">
                  {{ getSlaCountdown(request) }}
                </span>
              </div>

              <!-- Platforms -->
              <div class="mt-2 flex flex-wrap gap-1">
                <span
                  v-for="platform in request.platforms"
                  :key="platform"
                  class="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-300"
                >
                  {{ platform }}
                </span>
              </div>

              <!-- Status badge -->
              <div class="mt-2 flex items-center justify-between">
                <span
                  v-if="request.status === 'approved'"
                  class="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-[10px] font-medium text-green-700 dark:bg-green-900/30 dark:text-green-300"
                >
                  <CheckCircleIcon class="h-3 w-3" />
                  승인됨
                </span>
                <span
                  v-else-if="request.status === 'rejected'"
                  class="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-medium text-red-700 dark:bg-red-900/30 dark:text-red-300"
                >
                  <XCircleIcon class="h-3 w-3" />
                  반려됨
                </span>
                <span
                  v-else-if="request.status === 'revision_requested'"
                  class="inline-flex items-center gap-1 rounded-full bg-orange-100 px-2 py-0.5 text-[10px] font-medium text-orange-700 dark:bg-orange-900/30 dark:text-orange-300"
                >
                  <PencilSquareIcon class="h-3 w-3" />
                  수정 요청
                </span>
              </div>
            </div>

            <!-- Empty column -->
            <div
              v-if="column.requests.length === 0"
              class="flex flex-col items-center justify-center py-8 text-center"
            >
              <component :is="column.icon" class="h-6 w-6 text-gray-300 dark:text-gray-600" />
              <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">
                항목 없음
              </p>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ===== LIST VIEW ===== -->
    <template v-else>
      <!-- Tabs and Filter -->
      <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <!-- Tabs -->
        <div class="flex gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              activeTab === tab.key
                ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-white'
                : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white'
            "
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
            <span
              v-if="tab.count > 0"
              class="inline-flex items-center justify-center rounded-full px-1.5 py-0.5 text-[10px] font-semibold"
              :class="
                activeTab === tab.key
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                  : 'bg-gray-200 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
              "
            >
              {{ tab.count }}
            </span>
          </button>
        </div>

        <!-- Status Filter -->
        <div class="flex items-center gap-2">
          <FunnelIcon class="h-4 w-4 text-gray-400" />
          <select
            v-model="statusFilter"
            class="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
          >
            <option
              v-for="option in statusFilterOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </div>
      </div>

      <!-- Request List with SLA -->
      <div v-if="filteredRequests.length > 0" class="space-y-3">
        <div v-for="request in filteredRequests" :key="request.id">
          <!-- SLA Banner -->
          <div
            v-if="getSlaCountdown(request) && request.status === 'pending'"
            class="mb-1 flex items-center gap-1.5 rounded-t-lg px-3 py-1.5"
            :class="getSlaUrgency(request).includes('red')
              ? 'bg-red-50 dark:bg-red-900/20'
              : getSlaUrgency(request).includes('orange')
                ? 'bg-orange-50 dark:bg-orange-900/20'
                : 'bg-gray-50 dark:bg-gray-900'"
          >
            <ClockIcon class="h-3.5 w-3.5" :class="getSlaUrgency(request)" />
            <span class="text-xs font-medium" :class="getSlaUrgency(request)">
              SLA: {{ getSlaCountdown(request) }}
            </span>
            <!-- Chain progress inline -->
            <template v-if="getChainProgress(request)">
              <span class="mx-1.5 text-gray-300 dark:text-gray-600">|</span>
              <span class="text-xs text-gray-500 dark:text-gray-400">
                승인 단계 {{ getChainProgress(request)!.completed }}/{{ getChainProgress(request)!.total }}
              </span>
              <div class="ml-1 flex gap-0.5">
                <div
                  v-for="i in getChainProgress(request)!.total"
                  :key="i"
                  class="h-1.5 w-3 rounded-full"
                  :class="i <= getChainProgress(request)!.completed
                    ? 'bg-green-500'
                    : i === getChainProgress(request)!.completed + 1
                      ? 'bg-yellow-400'
                      : 'bg-gray-200 dark:bg-gray-700'"
                />
              </div>
            </template>
          </div>
          <ApprovalRequestCard
            :request="request"
            :role="getCardRole(request)"
            @click="openDetail(request.id)"
            @approve="handleApprove"
            @reject="handleReject"
            @request-revision="handleRequestRevision"
            @resubmit="handleResubmit"
          />
        </div>
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 bg-white py-16 dark:border-gray-600 dark:bg-gray-800"
      >
        <div class="flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <InboxIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mt-4 text-sm font-semibold text-gray-900 dark:text-white">
          {{ emptyMessages[activeTab]?.title }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ emptyMessages[activeTab]?.description }}
        </p>
      </div>
    </template>

    <!-- Detail Modal -->
    <ApprovalDetailModal
      v-model:visible="detailModalVisible"
      :request-id="selectedRequestId"
      @close="selectedRequestId = null"
    />

    <!-- Submit Modal -->
    <SubmitApprovalModal
      v-model:visible="submitModalVisible"
      :video-id="106"
      video-title="새로운 영상 콘텐츠"
      :platforms="['youtube', 'instagram']"
    />
  </div>
</template>
