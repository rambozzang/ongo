<script setup lang="ts">
import { computed } from 'vue'
import {
  TrashIcon,
  CalendarIcon,
  CurrencyDollarIcon,
  BuildingOfficeIcon,
} from '@heroicons/vue/24/outline'
import type { Sponsorship, SponsorshipStatus, PaymentStatus } from '@/types/sponsorshipTracker'

interface Props {
  sponsorship: Sponsorship
}

interface Emits {
  (e: 'click', id: number): void
  (e: 'delete', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const statusConfig = computed(() => {
  const configs: Record<SponsorshipStatus, { label: string; color: string }> = {
    INQUIRY: {
      label: '문의',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    NEGOTIATION: {
      label: '협상중',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    CONTRACTED: {
      label: '계약완료',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    IN_PROGRESS: {
      label: '진행중',
      color: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300',
    },
    DELIVERED: {
      label: '납품완료',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
    },
    PAID: {
      label: '정산완료',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    CANCELLED: {
      label: '취소',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.sponsorship.status]
})

const paymentStatusConfig = computed(() => {
  const configs: Record<PaymentStatus, { label: string; color: string }> = {
    PENDING: {
      label: '대기',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    INVOICED: {
      label: '청구됨',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    PAID: {
      label: '지급완료',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    OVERDUE: {
      label: '연체',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.sponsorship.paymentStatus]
})

const deliverablesProgress = computed(() => {
  const total = props.sponsorship.deliverables.length
  if (total === 0) return { completed: 0, total: 0, percent: 0 }
  const completed = props.sponsorship.deliverables.filter((d) => d.isCompleted).length
  return { completed, total, percent: Math.round((completed / total) * 100) }
})

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

/* Status pipeline: highlight current and completed stages */
const pipelineStages: { key: SponsorshipStatus; label: string }[] = [
  { key: 'INQUIRY', label: '문의' },
  { key: 'NEGOTIATION', label: '협상' },
  { key: 'CONTRACTED', label: '계약' },
  { key: 'IN_PROGRESS', label: '진행' },
  { key: 'DELIVERED', label: '납품' },
  { key: 'PAID', label: '정산' },
]

const currentStageIndex = computed(() => {
  if (props.sponsorship.status === 'CANCELLED') return -1
  return pipelineStages.findIndex((s) => s.key === props.sponsorship.status)
})
</script>

<template>
  <div
    class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6 hover:shadow-xl transition-all duration-200 cursor-pointer group relative"
    @click="emit('click', sponsorship.id)"
  >
    <!-- Top: Brand info & status -->
    <div class="flex items-start justify-between mb-4">
      <div class="flex items-center gap-3 min-w-0">
        <div
          v-if="sponsorship.brandLogo"
          class="w-10 h-10 rounded-full overflow-hidden flex-shrink-0 border border-gray-200 dark:border-gray-600"
        >
          <img :src="sponsorship.brandLogo" :alt="sponsorship.brandName" class="w-full h-full object-cover" />
        </div>
        <div
          v-else
          class="w-10 h-10 rounded-full flex-shrink-0 bg-gradient-to-br from-blue-500 to-purple-500 flex items-center justify-center"
        >
          <BuildingOfficeIcon class="w-5 h-5 text-white" />
        </div>
        <div class="min-w-0">
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ sponsorship.brandName }}
          </h3>
          <p class="text-xs text-gray-500 dark:text-gray-400 truncate">
            {{ sponsorship.contactName }}
          </p>
        </div>
      </div>
      <span
        :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium flex-shrink-0', statusConfig.color]"
      >
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Status Pipeline -->
    <div v-if="sponsorship.status !== 'CANCELLED'" class="mb-4">
      <div class="flex items-center gap-0.5">
        <div
          v-for="(stage, i) in pipelineStages"
          :key="stage.key"
          class="flex-1 h-1.5 rounded-full transition-colors"
          :class="i <= currentStageIndex
            ? 'bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400'
            : 'bg-gray-200 dark:bg-gray-700'
          "
        />
      </div>
      <div class="flex justify-between mt-1">
        <span class="text-[10px] text-gray-400 dark:text-gray-500">문의</span>
        <span class="text-[10px] text-gray-400 dark:text-gray-500">정산</span>
      </div>
    </div>

    <!-- Cancelled badge -->
    <div
      v-if="sponsorship.status === 'CANCELLED'"
      class="mb-4 px-3 py-1.5 rounded-lg bg-red-50 dark:bg-red-900/20 text-center"
    >
      <span class="text-xs text-red-600 dark:text-red-400 font-medium">취소된 딜</span>
    </div>

    <!-- Deal Value -->
    <div class="flex items-center gap-2 mb-3">
      <CurrencyDollarIcon class="w-4 h-4 text-gray-400 dark:text-gray-500 flex-shrink-0" />
      <span class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ formatCurrency(sponsorship.dealValue) }}
      </span>
    </div>

    <!-- Date range -->
    <div class="flex items-center gap-2 mb-4 text-sm text-gray-500 dark:text-gray-400">
      <CalendarIcon class="w-4 h-4 flex-shrink-0" />
      <span>{{ formatDate(sponsorship.startDate) }} ~ {{ formatDate(sponsorship.endDate) }}</span>
    </div>

    <!-- Deliverables progress -->
    <div class="mb-4">
      <div class="flex items-center justify-between text-sm mb-1">
        <span class="text-gray-600 dark:text-gray-400">산출물 진행</span>
        <span class="text-gray-700 dark:text-gray-300 font-medium">
          {{ deliverablesProgress.completed }}/{{ deliverablesProgress.total }}
        </span>
      </div>
      <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400 rounded-full transition-all duration-500"
          :style="{ width: `${deliverablesProgress.percent}%` }"
        />
      </div>
    </div>

    <!-- Payment status -->
    <div class="flex items-center justify-between border-t border-gray-200 dark:border-gray-700 pt-3">
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-500 dark:text-gray-400">결제</span>
        <span
          :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', paymentStatusConfig.color]"
        >
          {{ paymentStatusConfig.label }}
        </span>
      </div>
      <button
        @click.stop="emit('delete', sponsorship.id)"
        class="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 dark:hover:text-red-400 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
      >
        <TrashIcon class="w-4 h-4" />
      </button>
    </div>
  </div>
</template>
