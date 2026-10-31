<script setup lang="ts">
import { computed } from 'vue'
import { scoreTier } from '@/views/ugc/shortsSegmentOptions'

/**
 * 맥락 컷 AI 점수 배지와 근거.
 *
 * 점수가 없으면 "점수 없음"으로 보인다. 0점으로 바꿔 보이면 AI 가 나쁘다고 판정한 것처럼 읽힌다.
 */
const props = withDefaults(
  defineProps<{
    score?: number | null
    reason?: string | null
    /** 근거 문장을 배지 옆이 아니라 아래 줄에 펼쳐 보인다. */
    showReason?: boolean
  }>(),
  { score: null, reason: null, showReason: false },
)

const tier = computed(() => scoreTier(props.score))
const badgeClass = computed(() => ({
  high: 'bg-success-subtle text-success-strong',
  mid: 'bg-warning-subtle text-warning-strong',
  low: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
  none: 'bg-gray-100 text-gray-400 dark:bg-gray-800 dark:text-gray-500',
}[tier.value]))
</script>

<template>
  <span class="inline-flex min-w-0 flex-col gap-0.5">
    <span
      class="inline-flex w-fit items-center rounded-full px-2 py-0.5 text-caption font-semibold"
      :class="badgeClass"
      :title="$t('ugc.shorts.editor.scoreHint')"
      data-testid="clip-score"
    >
      {{ tier === 'none' ? $t('ugc.shorts.editor.noScore') : $t('ugc.shorts.editor.score', { score }) }}
    </span>
    <span
      v-if="showReason && reason"
      class="text-body-xs text-gray-500 dark:text-gray-400"
      data-testid="clip-score-reason"
    >
      {{ $t('ugc.shorts.editor.scoreReason') }}: {{ reason }}
    </span>
  </span>
</template>
