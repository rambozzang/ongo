import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePaymentAvailability } from './usePaymentAvailability'
import { capabilitiesApi } from '@/api/capabilities'

/**
 * 결제 가능 여부는 **서버가 정한다.**
 *
 * 결제 설정은 배포 환경에 있고 화면은 그 값을 볼 수 없다. 클라이언트 상수로 판단하면
 * 배포마다 어긋나고, SDK 를 연 뒤 실패를 감추면 사용자는 원인을 알 수 없는 오류를 본다.
 */
vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn(), clearCache: vi.fn() },
}))

describe('usePaymentAvailability', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('서버가 payment 를 활성으로 주면 결제를 허용한다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)

    const { paymentEnabled, paymentDisabledReason, loadPaymentAvailability } = usePaymentAvailability()
    await loadPaymentAvailability()

    expect(paymentEnabled.value).toBe(true)
    expect(paymentDisabledReason.value).toBeNull()
  })

  it('서버가 payment 를 비활성으로 주면 이유와 함께 막는다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: false, reason: '온라인 결제를 일시적으로 사용할 수 없습니다.' },
    ] as never)

    const { paymentEnabled, paymentDisabledReason, loadPaymentAvailability } = usePaymentAvailability()
    await loadPaymentAvailability()

    expect(paymentEnabled.value).toBe(false)
    expect(paymentDisabledReason.value).toContain('일시적으로 사용할 수 없습니다')
  })

  /* 결제 가능 여부를 모르는 채 결제창을 여는 것보다 잠시 막는 편이 낫다. */
  it('capability 조회가 실패하면 사용 불가로 본다', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('Network Error'))

    const { paymentEnabled, paymentChecked, loadPaymentAvailability } = usePaymentAvailability()
    await loadPaymentAvailability()

    expect(paymentEnabled.value).toBe(false)
    expect(paymentChecked.value).toBe(true)
  })

  /* 서버가 키를 안 내려주면 판단 근거가 없다. 열지 않는다. */
  it('payment 키가 없으면 사용 불가로 본다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'subscription', enabled: true, reason: null },
    ] as never)

    const { paymentEnabled, loadPaymentAvailability } = usePaymentAvailability()
    await loadPaymentAvailability()

    expect(paymentEnabled.value).toBe(false)
  })
})
