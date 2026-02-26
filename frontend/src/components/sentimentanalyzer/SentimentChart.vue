<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  positiveCount: number
  neutralCount: number
  negativeCount: number
}>()

const total = computed(() => props.positiveCount + props.neutralCount + props.negativeCount)

const positivePercent = computed(() =>
  total.value > 0 ? ((props.positiveCount / total.value) * 100).toFixed(1) : '0.0',
)
const neutralPercent = computed(() =>
  total.value > 0 ? ((props.neutralCount / total.value) * 100).toFixed(1) : '0.0',
)
const negativePercent = computed(() =>
  total.value > 0 ? ((props.negativeCount / total.value) * 100).toFixed(1) : '0.0',
)

const positiveWidth = computed(() =>
  total.value > 0 ? `${(props.positiveCount / total.value) * 100}%` : '0%',
)
const neutralWidth = computed(() =>
  total.value > 0 ? `${(props.neutralCount / total.value) * 100}%` : '0%',
)
const negativeWidth = computed(() =>
  total.value > 0 ? `${(props.negativeCount / total.value) * 100}%` : '0%',
)
</script>

<template>
  <div>
    <!-- Stacked Progress Bar -->
    <div class="mb-3 flex h-3 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
      <div
        class="bg-green-500 transition-all duration-500"
        :style="{ width: positiveWidth }"
      />
      <div
        class="bg-gray-400 transition-all duration-500"
        :style="{ width: neutralWidth }"
      />
      <div
        class="bg-red-500 transition-all duration-500"
        :style="{ width: negativeWidth }"
      />
    </div>

    <!-- Legend -->
    <div class="flex flex-wrap items-center gap-4 text-xs">
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-green-500" />
        <span class="text-gray-600 dark:text-gray-400">
          긍정 {{ positivePercent }}%
        </span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ positiveCount.toLocaleString('ko-KR') }}건
        </span>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-gray-400" />
        <span class="text-gray-600 dark:text-gray-400">
          중립 {{ neutralPercent }}%
        </span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ neutralCount.toLocaleString('ko-KR') }}건
        </span>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-2.5 w-2.5 rounded-full bg-red-500" />
        <span class="text-gray-600 dark:text-gray-400">
          부정 {{ negativePercent }}%
        </span>
        <span class="font-semibold text-gray-900 dark:text-gray-100">
          {{ negativeCount.toLocaleString('ko-KR') }}건
        </span>
      </div>
    </div>
  </div>
</template>
