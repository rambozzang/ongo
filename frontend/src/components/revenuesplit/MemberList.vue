<script setup lang="ts">
import { EnvelopeIcon } from '@heroicons/vue/24/outline'
import type { SplitMember } from '@/types/revenueSplit'

defineProps<{
  members: SplitMember[]
}>()

const paymentConfig: Record<string, { bg: string; text: string; label: string }> = {
  PENDING: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '대기중' },
  PAID: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '지급완료' },
  FAILED: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: '실패' },
}

const memberColors = [
  'bg-primary-500',
  'bg-pink-500',
  'bg-yellow-500',
  'bg-green-500',
  'bg-purple-500',
  'bg-orange-500',
  'bg-teal-500',
  'bg-indigo-500',
]

const getPaymentStyle = (status: string) => paymentConfig[status] ?? paymentConfig.PENDING
const getInitial = (name: string) => name.charAt(0)
const getMemberColor = (index: number) => memberColors[index % memberColors.length]

const formatKRW = (amount: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount)
</script>

<template>
  <div class="space-y-3">
    <div
      v-for="(member, index) in members"
      :key="member.id"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <!-- Header: Avatar + Name + Role + Payment status -->
      <div class="mb-3 flex items-center gap-3">
        <div
          :class="getMemberColor(index)"
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white"
        >
          {{ getInitial(member.name) }}
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ member.name }}
            </span>
            <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
              {{ member.role }}
            </span>
          </div>
          <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
            <EnvelopeIcon class="h-3 w-3" />
            <span>{{ member.email }}</span>
          </div>
        </div>
        <span
          :class="[getPaymentStyle(member.paymentStatus).bg, getPaymentStyle(member.paymentStatus).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap"
        >
          {{ getPaymentStyle(member.paymentStatus).label }}
        </span>
      </div>

      <!-- Percentage + Amount -->
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ member.percentage }}%
        </span>
        <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ formatKRW(member.amount) }}
        </span>
      </div>

      <!-- Horizontal bar chart -->
      <div class="h-3 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
        <div
          :class="getMemberColor(index)"
          class="h-full rounded-full transition-all duration-500"
          :style="{ width: `${member.percentage}%` }"
        />
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-if="members.length === 0"
      class="rounded-xl border border-gray-200 bg-white py-8 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <p class="text-sm text-gray-500 dark:text-gray-400">멤버가 없습니다</p>
    </div>
  </div>
</template>
