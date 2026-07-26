<script setup lang="ts">
import { SparklesIcon } from '@heroicons/vue/24/outline'
import { useCreditStore } from '@/stores/credit'
import { storeToRefs } from 'pinia'

withDefaults(
  defineProps<{
    showLabel?: boolean
  }>(),
  {
    showLabel: true,
  },
)

const creditStore = useCreditStore()
const { totalBalance, isLow } = storeToRefs(creditStore)
</script>

<template>
  <div
    class="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-body font-medium"
    :class="isLow
      ? 'bg-error-subtle text-error-strong'
      : 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'"
  >
    <SparklesIcon class="h-4 w-4" />
    <span>{{ totalBalance.toLocaleString() }}</span>
    <span v-if="showLabel">{{ isLow ? '크레딧 부족' : '크레딧' }}</span>
  </div>
</template>
