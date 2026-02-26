<script setup lang="ts">
defineProps<{
  positivePct: number
  neutralPct: number
  negativePct: number
}>()
</script>

<template>
  <div class="w-full">
    <div class="flex h-3 w-full overflow-hidden rounded-full">
      <div
        class="bg-green-500 transition-all duration-500"
        :style="{ width: `${positivePct}%` }"
        :title="`${$t('commentSummary.positive')}: ${positivePct}%`"
      />
      <div
        class="bg-gray-400 transition-all duration-500"
        :style="{ width: `${neutralPct}%` }"
        :title="`${$t('commentSummary.neutral')}: ${neutralPct}%`"
      />
      <div
        class="bg-red-500 transition-all duration-500"
        :style="{ width: `${negativePct}%` }"
        :title="`${$t('commentSummary.negative')}: ${negativePct}%`"
      />
    </div>
    <div class="mt-1.5 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
      <div class="flex items-center gap-1">
        <span class="inline-block h-2 w-2 rounded-full bg-green-500" />
        <span>{{ $t('commentSummary.positive') }} {{ positivePct }}%</span>
      </div>
      <div class="flex items-center gap-1">
        <span class="inline-block h-2 w-2 rounded-full bg-gray-400" />
        <span>{{ $t('commentSummary.neutral') }} {{ neutralPct }}%</span>
      </div>
      <div class="flex items-center gap-1">
        <span class="inline-block h-2 w-2 rounded-full bg-red-500" />
        <span>{{ $t('commentSummary.negative') }} {{ negativePct }}%</span>
      </div>
    </div>
  </div>
</template>
