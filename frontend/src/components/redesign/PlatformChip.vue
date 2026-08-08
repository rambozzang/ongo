<template>
  <span
    class="inline-flex shrink-0 items-center justify-center rounded-md font-mono font-medium leading-none"
    :class="[colorClass, sizeClass]"
  >
    {{ CODES[platform] }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 플랫폼 칩.
 *
 * 시안에서는 2글자 약어지만, 실제 배포 시 각 플랫폼 브랜드 가이드의 공식 로고로 교체하는 것이
 * 원칙이다(핸드오프 Assets 절). 여기서는 로고 자리를 차지하는 역할이다.
 */
const props = withDefaults(
  defineProps<{
    platform: 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW'
    size?: 'sm' | 'md'
  }>(),
  { size: 'md' },
)

const CODES = {
  YT: 'YT',
  IG: 'IG',
  TT: 'TT',
  FB: 'FB',
  NV: 'NV',
  TH: 'TH',
  TW: 'X',
} as const

const COLORS = {
  YT: 'bg-platform-yt-bg text-platform-yt-fg',
  IG: 'bg-platform-ig-bg text-platform-ig-fg',
  TT: 'bg-platform-tt-bg text-platform-tt-fg',
  FB: 'bg-platform-fb-bg text-platform-fb-fg',
  NV: 'bg-platform-nv-bg text-platform-nv-fg',
  TH: 'bg-platform-th-bg text-platform-th-fg',
  TW: 'bg-platform-x-bg text-platform-x-fg',
} as const

const colorClass = computed(() => COLORS[props.platform])
const sizeClass = computed(() =>
  props.size === 'sm' ? 'h-[18px] w-[18px] text-[9px]' : 'h-[26px] w-[26px] text-[10.5px]',
)
</script>
