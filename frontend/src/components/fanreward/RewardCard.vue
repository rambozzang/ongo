<script setup lang="ts">
import { computed } from 'vue'
import type { FanReward } from '@/types/fanReward'
import RewardCategoryBadge from './RewardCategoryBadge.vue'

interface Props {
  reward: FanReward
}

interface Emits {
  (e: 'toggle', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const usagePercentage = computed(() => {
  if (!props.reward.maxClaims) return 0
  return Math.min((props.reward.claimedCount / props.reward.maxClaims) * 100, 100)
})

const usageLabel = computed(() => {
  if (!props.reward.maxClaims) return `${props.reward.claimedCount}회 사용`
  return `${props.reward.claimedCount} / ${props.reward.maxClaims}`
})
</script>

<template>
  <div
    class="relative rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
  >
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ reward.name }}
        </h3>
        <p class="mt-1 line-clamp-2 text-sm text-gray-500 dark:text-gray-400">
          {{ reward.description }}
        </p>
      </div>
      <!-- Active Toggle -->
      <label class="relative ml-3 inline-flex cursor-pointer items-center">
        <input
          type="checkbox"
          :checked="reward.isActive"
          class="peer sr-only"
          @change="emit('toggle', reward.id)"
        />
        <div
          class="h-5 w-9 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:bg-gray-700 dark:peer-focus:ring-primary-800"
        />
      </label>
    </div>

    <!-- Category Badge + Points -->
    <div class="mb-4 flex items-center justify-between">
      <RewardCategoryBadge :category="reward.category" />
      <span class="text-sm font-bold text-primary-600 dark:text-primary-400">
        {{ reward.pointsCost.toLocaleString('ko-KR') }} 포인트
      </span>
    </div>

    <!-- Usage Progress -->
    <div>
      <div class="mb-1 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
        <span>사용 현황</span>
        <span>{{ usageLabel }}</span>
      </div>
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
        <div
          class="h-full rounded-full bg-primary-500 transition-all"
          :style="{ width: reward.maxClaims ? `${usagePercentage}%` : '0%' }"
        />
      </div>
    </div>

    <!-- Inactive overlay -->
    <div
      v-if="!reward.isActive"
      class="absolute inset-0 flex items-center justify-center rounded-xl bg-gray-900/10 dark:bg-gray-900/30"
    >
      <span class="rounded-full bg-gray-800/80 px-3 py-1 text-xs font-medium text-white">
        비활성
      </span>
    </div>
  </div>
</template>
