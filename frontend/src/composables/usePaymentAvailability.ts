import { ref } from 'vue'
import { capabilitiesApi } from '@/api/capabilities'

/**
 * 온라인 결제를 지금 시작할 수 있는지. **서버가 정한다.**
 *
 * 클라이언트 상수나 빌드 플래그로 판단하지 않는다. 결제 설정은 배포 환경에 있고 화면은
 * 그 값을 볼 수 없다. 서버가 실제 설정을 보고 내려준 신호만 믿는다.
 *
 * SDK 를 연 뒤에 실패를 감추는 방식도 쓰지 않는다. 그러면 사용자는 결제창까지 갔다가
 * 원인을 알 수 없는 오류를 보고, 서버에는 아무도 정리하지 않는 대기 결제가 남는다.
 *
 * 조회 실패는 **사용 불가로 본다.** 결제 가능 여부를 모르는 채 결제창을 여는 것보다
 * 잠시 막는 편이 낫다.
 *
 * ## 왜 재확인이 필요한가
 *
 * capability 응답은 캐시된다. 운영자가 결제 설정을 켜도 **이미 열려 있던 탭**은 캐시가
 * 만료될 때까지 계속 "사용 불가"를 보여준다. 사용자는 설정이 켜졌다는 사실도, 새로고침이
 * 필요하다는 사실도 알 수 없다. [usePaymentAvailability] 를 쓰는 화면은 사용 불가를
 * 알릴 때 `recheckPaymentAvailability` 를 같이 내줘야 한다.
 *
 * 재확인은 **서버에 다시 묻는 것뿐이다.** 서버가 여전히 비활성이라고 답하면 그대로
 * 비활성이다 — 눌렀다는 이유로 결제가 열리지 않는다.
 */
export function usePaymentAvailability() {
  const paymentEnabled = ref(false)
  const paymentDisabledReason = ref<string | null>(null)
  const paymentChecked = ref(false)
  /** 재확인 요청이 진행 중인지. 버튼을 잠그고 진행 상황을 알리는 데 쓴다. */
  const paymentChecking = ref(false)
  /**
   * 마지막 조회가 **실패했는지.**
   *
   * "서버가 비활성이라고 답했다"와 "물어보지 못했다"는 다른 사실이다. 둘 다 결제를 막지만
   * 사용자가 할 일이 다르다 — 앞은 기다리는 것, 뒤는 다시 시도해 보는 것이다.
   */
  const paymentCheckFailed = ref(false)

  async function fetch(force: boolean) {
    // 새 응답을 받기 전에는 이전의 "사용 가능" 판정을 재사용하지 않는다.
    // 설정이 바뀌었거나 조회가 실패했는데도 결제 버튼이 잠깐 열리는 것을 막는다.
    paymentChecked.value = false
    paymentEnabled.value = false
    paymentDisabledReason.value = null
    paymentCheckFailed.value = false
    try {
      const items = await capabilitiesApi.list(force ? { force: true } : undefined)
      const payment = items.find((item) => item.key === 'payment')
      // 서버가 이 키를 안 내려주면 판단 근거가 없다. 열지 않는다.
      paymentEnabled.value = payment?.enabled === true
      paymentDisabledReason.value = payment?.reason ?? null
      paymentCheckFailed.value = false
    } catch {
      paymentEnabled.value = false
      paymentDisabledReason.value = null
      paymentCheckFailed.value = true
    } finally {
      paymentChecked.value = true
    }
  }

  async function loadPaymentAvailability() {
    if (paymentChecking.value) return
    paymentChecking.value = true
    try {
      await fetch(false)
    } finally {
      paymentChecking.value = false
    }
  }

  /**
   * 서버에 **다시 묻는다.** 캐시를 건너뛰므로 운영자가 방금 켠 설정이 이 탭에도 반영된다.
   *
   * 진행 중이면 아무것도 하지 않는다 — 연타가 요청을 늘리지 않게 한다(API 계층에도 같은
   * 중복 제거가 있지만, 여기서 막아야 버튼 상태와 실제 요청 수가 어긋나지 않는다).
   */
  async function recheckPaymentAvailability() {
    if (paymentChecking.value) return
    paymentChecking.value = true
    try {
      await fetch(true)
    } finally {
      paymentChecking.value = false
    }
  }

  return {
    paymentEnabled,
    paymentDisabledReason,
    paymentChecked,
    paymentChecking,
    paymentCheckFailed,
    loadPaymentAvailability,
    recheckPaymentAvailability,
  }
}
