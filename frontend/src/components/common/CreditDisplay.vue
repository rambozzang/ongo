<template>
  <router-link
    to="/subscription"
    class="flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
    :class="isLow ? 'text-red-600 dark:text-red-400' : 'text-gray-700 dark:text-gray-300'"
  >
    <SparklesIcon class="h-4 w-4" />
    <span class="hidden font-medium tablet:inline">
      {{ totalBalance }} 크레딧
    </span>
    <span class="font-medium tablet:hidden">
      {{ totalBalance }}
    </span>
  </router-link>
</template>

<script setup lang="ts">
import { SparklesIcon } from '@heroicons/vue/24/outline'
import { useCreditStore } from '@/stores/credit'
import { storeToRefs } from 'pinia'

const creditStore = useCreditStore()
const { totalBalance, isLow } = storeToRefs(creditStore)
</script>
