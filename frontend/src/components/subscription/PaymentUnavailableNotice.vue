<template>
  <div
    class="rounded-lg border border-warning-strong/40 bg-warning-subtle p-3 text-body-xs text-warning-strong"
    role="status"
    data-testid="payment-unavailable-notice"
  >
    <p>{{ copy }}</p>
    <button
      type="button"
      class="btn-secondary mt-2 text-body-xs"
      :disabled="checking"
      data-testid="payment-recheck"
      @click="emit('recheck')"
    >
      {{ checking ? '확인 중…' : '결제 상태 다시 확인' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { paymentUnavailableCopy } from './paymentAvailabilityCopy'

/**
 * 결제를 지금 시작할 수 없다는 안내와 **다시 확인할 수단**.
 *
 * 이 버튼은 서버에 다시 묻기만 한다. 서버가 여전히 비활성이라고 답하면 그대로 비활성이고,
 * 버튼을 눌렀다는 이유로 결제가 열리지 않는다 — 열어 봐야 사용자는 결제창에서 원인을 알 수
 * 없는 오류를 보고, 서버에는 아무도 정리하지 않는 대기 결제가 남는다.
 *
 * 안내가 필요한 이유는 캐시 때문이다. 운영자가 결제 설정을 켜도 이미 열려 있던 탭은
 * 캐시가 만료될 때까지 사용 불가를 보여준다. 사용자는 새로고침이 필요하다는 사실조차 모른다.
 */
interface Props {
  /** 서버가 준 사유. 없으면 같은 뜻의 기본 문구를 쓴다. */
  reason?: string | null
  /** 재확인 진행 중 */
  checking?: boolean
  /**
   * 마지막 조회가 실패했는지. "서버가 비활성이라고 답했다"와 "물어보지 못했다"는
   * 사용자가 할 일이 다르다.
   */
  checkFailed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  reason: null,
  checking: false,
  checkFailed: false,
})

const emit = defineEmits<{ recheck: [] }>()

// 문구는 잠긴 버튼의 툴팁과 공유한다 — 둘이 다른 말을 하면 사용자는 무엇을 믿을지 모른다.
const copy = computed(() => paymentUnavailableCopy(props.reason, props.checkFailed))
</script>
