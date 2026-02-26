<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  XMarkIcon,
  CurrencyDollarIcon,
  BriefcaseIcon,
  ClockIcon,
  CheckCircleIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import DealCard from '@/components/sponsorshiptracker/DealCard.vue'
import DeliverableList from '@/components/sponsorshiptracker/DeliverableList.vue'
import RevenueChart from '@/components/sponsorshiptracker/RevenueChart.vue'
import { useSponsorshipTrackerStore } from '@/stores/sponsorshipTracker'
import { storeToRefs } from 'pinia'
import type { Sponsorship, CreateSponsorshipRequest } from '@/types/sponsorshipTracker'

const store = useSponsorshipTrackerStore()
const { sponsorships, summary, isLoading } = storeToRefs(store)

/* ---- State ---- */
const showCreateModal = ref(false)
const showDetailDrawer = ref(false)
const selectedDeal = ref<Sponsorship | null>(null)
const activeFilter = ref<string>('ALL')

/* ---- Filter tabs ---- */
const filterTabs: { key: string; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'INQUIRY', label: '문의' },
  { key: 'NEGOTIATION', label: '협상중' },
  { key: 'CONTRACTED', label: '계약완료' },
  { key: 'IN_PROGRESS', label: '진행중' },
  { key: 'DELIVERED', label: '납품완료' },
  { key: 'PAID', label: '정산완료' },
  { key: 'CANCELLED', label: '취소' },
]

const filteredSponsorships = computed(() => {
  if (activeFilter.value === 'ALL') return sponsorships.value
  return sponsorships.value.filter((s) => s.status === activeFilter.value)
})

/* ---- Create form ---- */
const form = ref<CreateSponsorshipRequest>({
  brandName: '',
  contactName: '',
  contactEmail: '',
  dealValue: 0,
  currency: 'KRW',
  startDate: '',
  endDate: '',
  notes: '',
})

/* ---- Helpers ---- */
function formatCurrency(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

/* ---- Handlers ---- */
function openCreate() {
  form.value = {
    brandName: '',
    contactName: '',
    contactEmail: '',
    dealValue: 0,
    currency: 'KRW',
    startDate: '',
    endDate: '',
    notes: '',
  }
  showCreateModal.value = true
}

async function handleCreate() {
  if (!form.value.brandName.trim()) return
  await store.createSponsorship({ ...form.value })
  showCreateModal.value = false
}

function handleCardClick(id: number) {
  const deal = sponsorships.value.find((s) => s.id === id)
  if (deal) {
    selectedDeal.value = deal
    showDetailDrawer.value = true
  }
}

function handleDelete(id: number) {
  if (confirm('이 스폰서십을 삭제하시겠습니까?')) {
    store.deleteSponsorship(id)
    if (selectedDeal.value?.id === id) {
      showDetailDrawer.value = false
      selectedDeal.value = null
    }
  }
}

function handleFilterChange(key: string) {
  activeFilter.value = key
}

onMounted(async () => {
  await Promise.all([store.fetchSponsorships(), store.fetchSummary()])
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          스폰서십 트래커
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          브랜드 협찬 딜을 추적하고 관리하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="openCreate"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          새 딜 등록
        </button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div v-if="summary" class="grid grid-cols-2 tablet:grid-cols-4 gap-4 mb-6">
      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
            <BriefcaseIcon class="w-5 h-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ summary.totalDeals.toLocaleString('ko-KR') }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">총 딜</p>
          </div>
        </div>
      </div>

      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
            <CheckCircleIcon class="w-5 h-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ summary.activeDeals.toLocaleString('ko-KR') }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">진행중</p>
          </div>
        </div>
      </div>

      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center">
            <CurrencyDollarIcon class="w-5 h-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCurrency(summary.totalRevenue) }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">총 수익</p>
          </div>
        </div>
      </div>

      <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-yellow-100 dark:bg-yellow-900/30 flex items-center justify-center">
            <ClockIcon class="w-5 h-5 text-yellow-600 dark:text-yellow-400" />
          </div>
          <div>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCurrency(summary.pendingPayments) }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">미결제</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Revenue Chart -->
    <div
      v-if="summary && summary.revenueByMonth.length > 0"
      class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6 mb-6"
    >
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
        월별 매출
      </h2>
      <RevenueChart :data="summary.revenueByMonth" />
    </div>

    <!-- Status Filter Tabs -->
    <div class="mb-6">
      <div class="flex items-center gap-1 overflow-x-auto pb-2">
        <FunnelIcon class="w-4 h-4 text-gray-400 dark:text-gray-500 flex-shrink-0 mr-1" />
        <button
          v-for="tab in filterTabs"
          :key="tab.key"
          @click="handleFilterChange(tab.key)"
          class="inline-flex items-center px-3 py-1.5 rounded-full text-sm font-medium whitespace-nowrap transition-all duration-200"
          :class="activeFilter === tab.key
            ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900 shadow-md'
            : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'
          "
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Deal Cards Grid -->
    <div v-else-if="filteredSponsorships.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
      <DealCard
        v-for="deal in filteredSponsorships"
        :key="deal.id"
        :sponsorship="deal"
        @click="handleCardClick"
        @delete="handleDelete"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 dark:border-gray-600 py-16"
    >
      <BriefcaseIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mb-4" />
      <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        등록된 스폰서십이 없습니다
      </h3>
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
        새 브랜드 딜을 등록해서 관리를 시작하세요
      </p>
      <button @click="openCreate" class="btn-primary inline-flex items-center gap-2">
        <PlusIcon class="w-5 h-5" />
        첫 딜 등록하기
      </button>
    </div>

    <!-- ============ Create Deal Modal ============ -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showCreateModal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showCreateModal = false"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="showCreateModal"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Modal Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  새 스폰서십 등록
                </h2>
                <button
                  @click="showCreateModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Modal Body -->
              <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
                <!-- Brand Name -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    브랜드명 *
                  </label>
                  <input
                    v-model="form.brandName"
                    type="text"
                    placeholder="예: 삼성전자, 나이키"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Contact Name -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    담당자명
                  </label>
                  <input
                    v-model="form.contactName"
                    type="text"
                    placeholder="담당자 이름"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Contact Email -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    담당자 이메일
                  </label>
                  <input
                    v-model="form.contactEmail"
                    type="email"
                    placeholder="email@brand.com"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Deal Value -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    딜 금액 (KRW)
                  </label>
                  <input
                    v-model.number="form.dealValue"
                    type="number"
                    min="0"
                    step="100000"
                    placeholder="0"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Dates -->
                <div class="grid grid-cols-2 gap-4">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      시작일
                    </label>
                    <input
                      v-model="form.startDate"
                      type="date"
                      class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                    />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      종료일
                    </label>
                    <input
                      v-model="form.endDate"
                      type="date"
                      class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                    />
                  </div>
                </div>

                <!-- Notes -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    메모
                  </label>
                  <textarea
                    v-model="form.notes"
                    rows="3"
                    placeholder="추가 메모사항..."
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-none"
                  />
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button
                  @click="showCreateModal = false"
                  class="btn-secondary"
                >
                  취소
                </button>
                <button
                  @click="handleCreate"
                  :disabled="!form.brandName.trim()"
                  class="btn-primary"
                >
                  등록
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>

    <!-- ============ Deal Detail Drawer ============ -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showDetailDrawer && selectedDeal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
          @click="showDetailDrawer = false"
        >
          <Transition
            enter-active-class="transition-transform duration-300 ease-out"
            enter-from-class="translate-x-full"
            enter-to-class="translate-x-0"
            leave-active-class="transition-transform duration-200 ease-in"
            leave-from-class="translate-x-0"
            leave-to-class="translate-x-full"
          >
            <div
              v-if="showDetailDrawer && selectedDeal"
              class="absolute right-0 top-0 h-full w-full max-w-xl bg-white dark:bg-gray-800 shadow-2xl flex flex-col"
              @click.stop
            >
              <!-- Drawer Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <div class="min-w-0">
                  <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100 truncate">
                    {{ selectedDeal.brandName }}
                  </h2>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                    {{ selectedDeal.contactName }} &middot; {{ selectedDeal.contactEmail }}
                  </p>
                </div>
                <button
                  @click="showDetailDrawer = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ml-4"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Drawer Body -->
              <div class="flex-1 overflow-y-auto px-6 py-6 space-y-6">
                <!-- Deal info summary -->
                <div class="grid grid-cols-2 gap-4">
                  <div class="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
                    <p class="text-xs text-gray-500 dark:text-gray-400 mb-1">딜 금액</p>
                    <p class="text-lg font-bold text-gray-900 dark:text-gray-100">
                      {{ formatCurrency(selectedDeal.dealValue) }}
                    </p>
                  </div>
                  <div class="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
                    <p class="text-xs text-gray-500 dark:text-gray-400 mb-1">지급 금액</p>
                    <p class="text-lg font-bold text-green-600 dark:text-green-400">
                      {{ formatCurrency(selectedDeal.paidAmount) }}
                    </p>
                  </div>
                </div>

                <!-- Date range -->
                <div class="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
                  <p class="text-xs text-gray-500 dark:text-gray-400 mb-1">계약 기간</p>
                  <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ new Date(selectedDeal.startDate).toLocaleDateString('ko-KR') }}
                    ~
                    {{ new Date(selectedDeal.endDate).toLocaleDateString('ko-KR') }}
                  </p>
                </div>

                <!-- Notes -->
                <div v-if="selectedDeal.notes" class="bg-gray-50 dark:bg-gray-900 rounded-xl p-4">
                  <p class="text-xs text-gray-500 dark:text-gray-400 mb-1">메모</p>
                  <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">
                    {{ selectedDeal.notes }}
                  </p>
                </div>

                <!-- Deliverables -->
                <DeliverableList
                  :deliverables="selectedDeal.deliverables"
                  @toggle-complete="() => {}"
                />
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
