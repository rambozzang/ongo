<template>
  <span
    class="inline-flex items-center rounded-[5px] px-[7px] py-[3px] text-[10.5px] font-semibold leading-none"
    :class="variantClass"
  >
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 상태 배지 — 리디자인 시안의 pill(bg, fg) 규칙.
 *
 * success 정상·완료·상승 / warning 검수·썸네일·한도 근접 / error 오류·하락 / muted 처리 중·일반
 */
const props = withDefaults(
  defineProps<{
    variant?: 'success' | 'warning' | 'error' | 'muted'
  }>(),
  { variant: 'muted' },
)

const VARIANTS = {
  success: 'bg-success-subtle text-success-strong',
  warning: 'bg-warning-subtle text-warning-strong',
  error: 'bg-error-subtle text-error-strong',
  muted: 'bg-muted-subtle text-muted-strong',
} as const

const variantClass = computed(() => VARIANTS[props.variant])
</script>
