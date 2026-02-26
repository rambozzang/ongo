<script setup lang="ts">
import { TrashIcon, UserGroupIcon } from '@heroicons/vue/24/outline'
import type { RevenueSplit } from '@/types/revenueSplit'

defineProps<{
  split: RevenueSplit
}>()

const emit = defineEmits<{
  click: [id: number]
  delete: [id: number]
}>()

const statusConfig: Record<string, { bg: string; text: string; label: string }> = {
  DRAFT: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300', label: '초안' },
  PENDING: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '대기중' },
  APPROVED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '승인됨' },
  DISTRIBUTED: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '분배완료' },
  DISPUTED: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: '분쟁중' },
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

const getStatusStyle = (status: string) => statusConfig[status] ?? statusConfig.DRAFT
const getInitial = (name: string) => name.charAt(0)
const getMemberColor = (index: number) => memberColors[index % memberColors.length]

const formatKRW = (amount: number) =>
  new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount)
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('click', split.id)"
  >
    <!-- Header: Title + Status + Delete -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <h3 class="truncate text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ split.title }}
        </h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ split.period }}
        </p>
      </div>
      <div class="ml-2 flex items-center gap-2">
        <span
          :class="[getStatusStyle(split.status).bg, getStatusStyle(split.status).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap"
        >
          {{ getStatusStyle(split.status).label }}
        </span>
        <button
          class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
          title="삭제"
          @click.stop="emit('delete', split.id)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Total Amount -->
    <div class="mb-3">
      <p class="text-xs text-gray-500 dark:text-gray-400">총 금액</p>
      <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
        {{ formatKRW(split.totalAmount) }}
      </p>
    </div>

    <!-- Members preview -->
    <div class="mb-3 flex items-center gap-2">
      <UserGroupIcon class="h-4 w-4 text-gray-400" />
      <div class="flex -space-x-2">
        <div
          v-for="(member, index) in split.members.slice(0, 5)"
          :key="member.id"
          :class="getMemberColor(index)"
          class="flex h-7 w-7 items-center justify-center rounded-full border-2 border-white text-xs font-semibold text-white dark:border-gray-900"
          :title="member.name"
        >
          {{ getInitial(member.name) }}
        </div>
        <div
          v-if="split.members.length > 5"
          class="flex h-7 w-7 items-center justify-center rounded-full border-2 border-white bg-gray-300 text-xs font-semibold text-gray-700 dark:border-gray-900 dark:bg-gray-600 dark:text-gray-200"
        >
          +{{ split.members.length - 5 }}
        </div>
      </div>
      <span class="ml-auto text-xs text-gray-500 dark:text-gray-400">
        {{ split.members.length }}명
      </span>
    </div>

    <!-- Member ratio bars preview -->
    <div class="space-y-1.5">
      <div
        v-for="(member, index) in split.members.slice(0, 3)"
        :key="member.id"
        class="flex items-center gap-2"
      >
        <span class="w-14 truncate text-xs text-gray-600 dark:text-gray-400">
          {{ member.name }}
        </span>
        <div class="flex-1">
          <div class="h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
            <div
              :class="getMemberColor(index)"
              class="h-full rounded-full transition-all duration-500"
              :style="{ width: `${member.percentage}%` }"
            />
          </div>
        </div>
        <span class="w-8 text-right text-xs font-medium text-gray-600 dark:text-gray-400">
          {{ member.percentage }}%
        </span>
      </div>
      <p
        v-if="split.members.length > 3"
        class="text-xs text-gray-400 dark:text-gray-500"
      >
        외 {{ split.members.length - 3 }}명
      </p>
    </div>
  </div>
</template>
