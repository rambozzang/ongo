<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  BanknotesIcon,
  PlusIcon,
  XMarkIcon,
  UserGroupIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  CurrencyDollarIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useRevenueSplitStore } from '@/stores/revenueSplit'
import SplitCard from '@/components/revenuesplit/SplitCard.vue'
import SplitPieChart from '@/components/revenuesplit/SplitPieChart.vue'
import MemberList from '@/components/revenuesplit/MemberList.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { RevenueSplit, RevenueSplitStatus } from '@/types/revenueSplit'

const store = useRevenueSplitStore()
const { splits, summary, isLoading } = storeToRefs(store)

// ---- Filter ----
const activeFilter = ref<RevenueSplitStatus | 'ALL'>('ALL')

const statusFilters: { value: RevenueSplitStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'DRAFT', label: '초안' },
  { value: 'PENDING', label: '대기중' },
  { value: 'APPROVED', label: '승인됨' },
  { value: 'DISTRIBUTED', label: '분배완료' },
  { value: 'DISPUTED', label: '분쟁중' },
]

const filteredSplits = computed(() => {
  if (activeFilter.value === 'ALL') return splits.value
  return splits.value.filter((s) => s.status === activeFilter.value)
})

// ---- Detail Drawer ----
const selectedSplit = ref<RevenueSplit | null>(null)
const showDrawer = ref(false)

const openDrawer = (id: number) => {
  const found = splits.value.find((s) => s.id === id)
  if (found) {
    selectedSplit.value = found
    showDrawer.value = true
  }
}

const closeDrawer = () => {
  showDrawer.value = false
  selectedSplit.value = null
}

const handleApprove = async () => {
  if (!selectedSplit.value) return
  await store.approveSplit(selectedSplit.value.id)
  selectedSplit.value = splits.value.find((s) => s.id === selectedSplit.value!.id) ?? null
}

const handleDistribute = async () => {
  if (!selectedSplit.value) return
  if (!confirm('정말 수익을 분배하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return
  await store.distributeSplit(selectedSplit.value.id)
  selectedSplit.value = splits.value.find((s) => s.id === selectedSplit.value!.id) ?? null
}

// ---- Create Modal ----
const showCreateModal = ref(false)
const newTitle = ref('')
const newDescription = ref('')
const newTotalAmount = ref(0)
const newPeriod = ref('')
const newMembers = ref<{ name: string; email: string; role: string; percentage: number }[]>([])

const openCreateModal = () => {
  newTitle.value = ''
  newDescription.value = ''
  newTotalAmount.value = 0
  newPeriod.value = ''
  newMembers.value = [{ name: '', email: '', role: '', percentage: 0 }]
  showCreateModal.value = true
}

const closeCreateModal = () => {
  showCreateModal.value = false
}

const addMember = () => {
  newMembers.value.push({ name: '', email: '', role: '', percentage: 0 })
}

const removeMember = (index: number) => {
  newMembers.value.splice(index, 1)
}

const totalPercentage = computed(() =>
  newMembers.value.reduce((sum, m) => sum + (m.percentage || 0), 0),
)

const handleCreate = async () => {
  if (!newTitle.value.trim() || newTotalAmount.value <= 0) return
  if (totalPercentage.value !== 100) return

  await store.createSplit({
    title: newTitle.value.trim(),
    description: newDescription.value.trim(),
    totalAmount: newTotalAmount.value,
    currency: 'KRW',
    period: newPeriod.value,
    members: newMembers.value.filter((m) => m.name.trim()),
  })
  closeCreateModal()
}

const handleDelete = (id: number) => {
  if (confirm('이 분배를 삭제하시겠습니까?')) {
    store.deleteSplit(id)
  }
}

const formatKRW = (amount: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount)

const getDrawerStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    DRAFT: '초안',
    PENDING: '대기중',
    APPROVED: '승인됨',
    DISTRIBUTED: '분배완료',
    DISPUTED: '분쟁중',
  }
  return labels[status] ?? status
}

onMounted(() => {
  store.fetchSplits()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            수익 분배 관리
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          팀 멤버 간 수익을 공정하게 분배하고 관리하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          새 분배
        </button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <BanknotesIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 분배</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalSplits.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ExclamationTriangleIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">미결 금액</p>
        </div>
        <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ formatKRW(summary.pendingAmount) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CheckCircleIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">분배 완료</p>
        </div>
        <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ formatKRW(summary.distributedAmount) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <UserGroupIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">멤버 수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalMembers.toLocaleString('ko-KR') }}
        </p>
      </div>
    </div>

    <!-- Status Filter Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-6 overflow-x-auto">
        <button
          v-for="filter in statusFilters"
          :key="filter.value"
          @click="activeFilter = filter.value"
          :class="[
            'py-3 px-1 border-b-2 font-medium text-sm transition-colors whitespace-nowrap',
            activeFilter === filter.value
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ filter.label }}
        </button>
      </nav>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Split Cards Grid -->
    <div v-else>
      <div v-if="filteredSplits.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
        <SplitCard
          v-for="s in filteredSplits"
          :key="s.id"
          :split="s"
          @click="openDrawer"
          @delete="handleDelete"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <BanknotesIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          분배 내역이 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          새 분배를 생성하여 수익을 관리하세요
        </p>
        <button
          class="btn-primary mt-4 inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          첫 분배 만들기
        </button>
      </div>
    </div>

    <!-- Detail Drawer -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showDrawer && selectedSplit"
          class="fixed inset-0 z-50 flex justify-end bg-black/50"
          @click.self="closeDrawer"
        >
          <Transition name="slide-right">
            <div
              v-if="showDrawer"
              class="h-full w-full max-w-lg overflow-y-auto bg-white p-6 shadow-xl dark:bg-gray-900"
            >
              <!-- Drawer Header -->
              <div class="mb-6 flex items-center justify-between">
                <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                  {{ selectedSplit.title }}
                </h2>
                <button
                  class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                  @click="closeDrawer"
                >
                  <XMarkIcon class="h-5 w-5" />
                </button>
              </div>

              <!-- Status + Period -->
              <div class="mb-6 flex items-center gap-3">
                <span class="text-sm text-gray-500 dark:text-gray-400">
                  {{ selectedSplit.period }}
                </span>
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ getDrawerStatusLabel(selectedSplit.status) }}
                </span>
              </div>

              <!-- Description -->
              <div v-if="selectedSplit.description" class="mb-6">
                <p class="text-sm text-gray-600 dark:text-gray-400">
                  {{ selectedSplit.description }}
                </p>
              </div>

              <!-- Pie Chart -->
              <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
                <SplitPieChart
                  :members="selectedSplit.members"
                  :total-amount="selectedSplit.totalAmount"
                />
              </div>

              <!-- Member List -->
              <div class="mb-6">
                <h3 class="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-300">
                  멤버 목록
                </h3>
                <MemberList :members="selectedSplit.members" />
              </div>

              <!-- Action Buttons -->
              <div class="flex items-center gap-3">
                <button
                  v-if="selectedSplit.status === 'PENDING' || selectedSplit.status === 'DRAFT'"
                  class="btn-primary flex-1 inline-flex items-center justify-center gap-2"
                  @click="handleApprove"
                >
                  <CheckCircleIcon class="h-5 w-5" />
                  승인
                </button>
                <button
                  v-if="selectedSplit.status === 'APPROVED'"
                  class="flex-1 inline-flex items-center justify-center gap-2 rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700"
                  @click="handleDistribute"
                >
                  <CurrencyDollarIcon class="h-5 w-5" />
                  분배 실행
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>

    <!-- Create Modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showCreateModal"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="closeCreateModal"
        >
          <div class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                새 수익 분배
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeCreateModal"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <div class="space-y-4">
              <!-- Title -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  제목
                </label>
                <input
                  v-model="newTitle"
                  type="text"
                  placeholder="예: 2025년 4월 유튜브 수익"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Description -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  설명
                </label>
                <textarea
                  v-model="newDescription"
                  rows="2"
                  placeholder="분배에 대한 설명 (선택)"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Total Amount -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  총 금액 (KRW)
                </label>
                <input
                  v-model.number="newTotalAmount"
                  type="number"
                  min="0"
                  placeholder="0"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Period -->
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  기간
                </label>
                <input
                  v-model="newPeriod"
                  type="text"
                  placeholder="예: 2025-04"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <!-- Members -->
              <div>
                <div class="mb-2 flex items-center justify-between">
                  <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
                    멤버
                  </label>
                  <span
                    class="text-xs font-medium"
                    :class="totalPercentage === 100 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                  >
                    합계: {{ totalPercentage }}%
                  </span>
                </div>

                <div class="space-y-3">
                  <div
                    v-for="(member, index) in newMembers"
                    :key="index"
                    class="rounded-lg border border-gray-200 p-3 dark:border-gray-700"
                  >
                    <div class="mb-2 flex items-center justify-between">
                      <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
                        멤버 {{ index + 1 }}
                      </span>
                      <button
                        v-if="newMembers.length > 1"
                        class="rounded p-1 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                        @click="removeMember(index)"
                      >
                        <TrashIcon class="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                      <input
                        v-model="member.name"
                        type="text"
                        placeholder="이름"
                        class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                      />
                      <input
                        v-model="member.email"
                        type="email"
                        placeholder="이메일"
                        class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                      />
                      <input
                        v-model="member.role"
                        type="text"
                        placeholder="역할"
                        class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                      />
                      <input
                        v-model.number="member.percentage"
                        type="number"
                        min="0"
                        max="100"
                        placeholder="비율 (%)"
                        class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                      />
                    </div>
                  </div>
                </div>

                <button
                  class="mt-2 inline-flex w-full items-center justify-center gap-1 rounded-lg border border-dashed border-gray-300 py-2 text-xs font-medium text-gray-500 transition-colors hover:border-gray-400 hover:text-gray-600 dark:border-gray-600 dark:text-gray-400 dark:hover:border-gray-500 dark:hover:text-gray-300"
                  @click="addMember"
                >
                  <PlusIcon class="h-3.5 w-3.5" />
                  멤버 추가
                </button>
              </div>
            </div>

            <div class="mt-6 flex items-center justify-end gap-3">
              <button
                class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="closeCreateModal"
              >
                취소
              </button>
              <button
                class="btn-primary text-sm"
                :disabled="!newTitle.trim() || newTotalAmount <= 0 || totalPercentage !== 100"
                @click="handleCreate"
              >
                분배 생성
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
}
</style>
