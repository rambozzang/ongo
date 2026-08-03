<template>
  <section class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
    <header
      v-if="title || $slots.action"
      class="flex items-center gap-2 border-b border-line-row px-[15px] py-3"
    >
      <h2 v-if="title" class="text-[13px] font-bold text-content">{{ title }}</h2>
      <span v-if="meta" class="font-mono text-[10px] text-content-tertiary">{{ meta }}</span>
      <div class="ml-auto flex items-center gap-2">
        <slot name="action" />
      </div>
    </header>
    <div :class="bodyClass">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * 카드 프레임 — 헤더(제목 + 모노 메타 + 우측 액션)와 본문 슬롯.
 * 테이블을 담을 때는 padded=false 로 두어 행이 카드 가장자리까지 닿게 한다.
 */
withDefaults(
  defineProps<{
    title?: string
    meta?: string
    bodyClass?: string
  }>(),
  { bodyClass: '' },
)
</script>
