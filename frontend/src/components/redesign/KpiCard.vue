<template>
  <div class="rounded-[11px] border border-line bg-surface-card px-[15px] py-[13px]">
    <div class="text-[11px] text-content-secondary">{{ label }}</div>
    <div class="mt-1.5 flex items-baseline gap-2">
      <span class="font-mono text-[25px] font-bold leading-none tracking-[-0.02em] text-content">
        {{ value }}
      </span>
      <StatusPill v-if="delta" :variant="deltaVariant">{{ delta }}</StatusPill>
    </div>
    <div v-if="note" class="mt-1.5 text-[11px] text-content-tertiary">{{ note }}</div>
  </div>
</template>

<script setup lang="ts">
import StatusPill from './StatusPill.vue'

/**
 * KPI 카드 — 오늘·성과 화면 공용.
 * 값은 모노 25px/700. 델타는 상승 success / 하락·실패 error / 그 외 muted.
 */
withDefaults(
  defineProps<{
    label: string
    value: string
    delta?: string
    deltaVariant?: 'success' | 'warning' | 'error' | 'muted'
    note?: string
  }>(),
  { deltaVariant: 'success' },
)
</script>
