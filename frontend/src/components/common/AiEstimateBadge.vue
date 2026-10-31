<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * "AI 추정" 배지와 근거.
 *
 * 측정값과 추정값이 같은 모양이면 사용자는 둘을 구분할 수 없다. 서버가 `aiEstimate: { estimated, basis }` 를
 * 보낸 값 옆에 이것을 붙인다. 근거(basis)는 "무엇을 보고, 무엇을 보지 않고 추정했는지"이며 숨기지 않는다 —
 * 기본은 배지만 보이고 누르면 펼쳐진다(`showBasis` 로 처음부터 펼칠 수 있다).
 */
const props = withDefaults(
  defineProps<{
    basis?: string | null
    /** 처음부터 근거를 펼쳐 보인다. */
    showBasis?: boolean
    /** 배지 글자를 바꾼다(예: "AI 제안"). 기본은 "AI 추정". */
    label?: string | null
  }>(),
  { basis: null, showBasis: false, label: null },
)

const open = ref(props.showBasis)
const hasBasis = computed(() => !!props.basis && props.basis.trim().length > 0)
</script>

<template>
  <span class="inline-flex min-w-0 flex-col gap-1" data-testid="ai-estimate">
    <button
      type="button"
      class="inline-flex w-fit items-center gap-1 rounded-full border border-warning bg-warning-subtle px-2 py-0.5 text-caption font-semibold text-warning-strong"
      :aria-expanded="hasBasis ? open : undefined"
      :disabled="!hasBasis"
      :title="hasBasis ? $t('common.aiEstimate.showBasis') : undefined"
      data-testid="ai-estimate-badge"
      @click="open = !open"
    >
      {{ label ?? $t('common.aiEstimate.label') }}
    </button>
    <span
      v-if="hasBasis && open"
      class="text-body-xs text-gray-500 dark:text-gray-400"
      data-testid="ai-estimate-basis"
    >
      {{ $t('common.aiEstimate.basis') }}: {{ basis }}
    </span>
  </span>
</template>
