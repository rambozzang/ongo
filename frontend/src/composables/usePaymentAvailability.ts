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
 */
export function usePaymentAvailability() {
  const paymentEnabled = ref(false)
  const paymentDisabledReason = ref<string | null>(null)
  const paymentChecked = ref(false)

  async function loadPaymentAvailability() {
    try {
      const items = await capabilitiesApi.list()
      const payment = items.find((item) => item.key === 'payment')
      // 서버가 이 키를 안 내려주면 판단 근거가 없다. 열지 않는다.
      paymentEnabled.value = payment?.enabled === true
      paymentDisabledReason.value = payment?.reason ?? null
    } catch {
      paymentEnabled.value = false
      paymentDisabledReason.value = null
    } finally {
      paymentChecked.value = true
    }
  }

  return { paymentEnabled, paymentDisabledReason, paymentChecked, loadPaymentAvailability }
}
