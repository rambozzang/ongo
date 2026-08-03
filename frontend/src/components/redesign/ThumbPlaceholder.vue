<template>
  <div class="relative shrink-0 overflow-hidden rounded-md" :style="frameStyle">
    <img v-if="src" :src="src" alt="" class="h-full w-full object-cover" />
    <div v-else class="h-full w-full" :style="PLACEHOLDER_PATTERN" />
    <span
      v-if="duration"
      class="absolute bottom-1 right-1 rounded-[3px] px-1 py-px font-mono text-[9px] leading-tight text-content"
      style="background: #101120b0"
    >
      {{ duration }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 영상 썸네일. src 가 없으면 시안의 사선 자리표시자를 그린다.
 *
 * 자리표시자 패턴 hex 는 핸드오프에서 값으로 확정된 것이라 토큰화하지 않았다(Assets 절).
 */
const props = withDefaults(
  defineProps<{
    src?: string | null
    duration?: string | null
    width?: number
    height?: number
  }>(),
  { width: 84, height: 46 },
)

const PLACEHOLDER_PATTERN = {
  background: 'repeating-linear-gradient(135deg,#262a41 0 6px,#2d3149 6px 12px)',
}

const frameStyle = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height}px`,
}))
</script>
