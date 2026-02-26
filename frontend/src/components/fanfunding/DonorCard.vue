<template>
  <div class="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Rank badge -->
    <div
      class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full text-sm font-bold"
      :class="rankClass"
    >
      {{ rank }}
    </div>

    <!-- Donor info -->
    <div class="min-w-0 flex-1">
      <p class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ donor.name }}
      </p>
      <p class="text-xs text-gray-500 dark:text-gray-400">
        {{ donor.count }}{{ $t('fanFunding.donationCountUnit') }}
      </p>
    </div>

    <!-- Total amount -->
    <div class="flex-shrink-0 text-right">
      <p class="text-sm font-bold text-primary-600 dark:text-primary-400">
        {{ formattedAmount }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  donor: { name: string; totalAmount: number; count: number }
  rank: number
}>()

const rankClass = computed(() => {
  switch (props.rank) {
    case 1:
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400'
    case 2:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
    case 3:
      return 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-400'
    default:
      return 'bg-gray-50 text-gray-500 dark:bg-gray-800 dark:text-gray-400'
  }
})

const formattedAmount = computed(() => {
  const val = props.donor.totalAmount
  if (val >= 10000) {
    return `${(val / 10000).toFixed(1)}만원`
  }
  return `${val.toLocaleString('ko-KR')}원`
})
</script>
