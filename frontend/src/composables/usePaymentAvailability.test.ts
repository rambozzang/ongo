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

  /* ── 실패와 비활성을 구분한다 ─────────────────────────────────── */

  /**
   * "서버가 비활성이라고 답했다"와 "물어보지 못했다"는 다른 사실이다. 둘 다 결제를 막지만
   * 사용자가 할 일이 다르다 — 앞은 기다리는 것, 뒤는 다시 시도해 보는 것이다.
   */
  it('조회 실패를 서버가 준 비활성과 구분한다', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('Network Error'))
    const failed = usePaymentAvailability()
    await failed.loadPaymentAvailability()

    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: false, reason: '결제 설정이 없습니다' },
    ] as never)
    const disabled = usePaymentAvailability()
    await disabled.loadPaymentAvailability()

    expect(failed.paymentCheckFailed.value).toBe(true)
    expect(disabled.paymentCheckFailed.value).toBe(false)
    expect(disabled.paymentDisabledReason.value).toBe('결제 설정이 없습니다')
  })
})

/**
 * 재확인은 **서버에 다시 묻는 것뿐이다.**
 *
 * capability 응답은 캐시된다. 운영자가 결제 설정을 켜도 이미 열려 있던 탭은 캐시가 만료될
 * 때까지 계속 사용 불가를 보여준다. 그래서 사용자가 직접 확인할 수단이 필요하다.
 *
 * 그렇다고 눌렀다는 이유로 결제가 열려서는 안 된다. 서버가 여전히 비활성이라고 답하면
 * 그대로 비활성이다.
 */
describe('usePaymentAvailability 재확인', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /** **핵심.** 캐시를 건너뛰어야 방금 켠 설정이 이 탭에도 반영된다. */
  it('캐시를 건너뛰고 서버에 다시 묻는다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: false, reason: '결제 설정이 없습니다' },
    ] as never)
    const { paymentEnabled, recheckPaymentAvailability } = usePaymentAvailability()
    await recheckPaymentAvailability()

    expect(capabilitiesApi.list).toHaveBeenCalledWith({ force: true })
    expect(paymentEnabled.value).toBe(false)
  })

  /** 운영자가 설정을 켜면 새로고침 없이 이 탭에서도 결제가 열린다. */
  it('서버가 활성으로 바뀌면 재확인으로 반영된다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: false, reason: '결제 설정이 없습니다' },
    ] as never)
    const { paymentEnabled, paymentDisabledReason, loadPaymentAvailability, recheckPaymentAvailability }
      = usePaymentAvailability()
    await loadPaymentAvailability()

    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    await recheckPaymentAvailability()

    expect(paymentEnabled.value).toBe(true)
    expect(paymentDisabledReason.value).toBeNull()
  })

  /**
   * **핵심.** 서버가 여전히 막고 있으면 재확인은 재확인일 뿐이다. 눌렀다는 이유로
   * 결제가 열리면 사용자는 결제창에서 원인 모를 오류를 보고, 서버에는 대기 결제가 남는다.
   */
  it('서버가 여전히 비활성이면 결제를 열지 않는다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: false, reason: '결제 설정이 없습니다' },
    ] as never)
    const { paymentEnabled, paymentDisabledReason, recheckPaymentAvailability } = usePaymentAvailability()

    await recheckPaymentAvailability()
    await recheckPaymentAvailability()

    expect(paymentEnabled.value).toBe(false)
    expect(paymentDisabledReason.value).toBe('결제 설정이 없습니다')
  })

  /** 재확인이 실패해도 열지 않는다 — 모르는 채 여는 것보다 막는 편이 낫다. */
  it('재확인에 실패하면 사용 불가를 유지하고 실패를 알린다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    const { paymentEnabled, paymentCheckFailed, loadPaymentAvailability, recheckPaymentAvailability }
      = usePaymentAvailability()
    await loadPaymentAvailability()

    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('Network Error'))
    await recheckPaymentAvailability()

    expect(paymentEnabled.value).toBe(false)
    expect(paymentCheckFailed.value).toBe(true)
  })

  /** 진행 중임을 알려야 버튼을 잠그고 사용자가 기다릴 수 있다. */
  it('진행 중에는 확인 중 상태를 알린다', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(capabilitiesApi.list).mockReturnValue(new Promise((r) => { resolve = r }) as never)
    const { paymentChecking, recheckPaymentAvailability } = usePaymentAvailability()

    const pending = recheckPaymentAvailability()
    expect(paymentChecking.value).toBe(true)

    resolve([{ key: 'payment', enabled: true, reason: null }])
    await pending

    expect(paymentChecking.value).toBe(false)
  })

  /** **연타 보호.** 버튼을 여러 번 눌러도 요청은 하나다. */
  it('진행 중에 다시 눌러도 요청을 늘리지 않는다', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(capabilitiesApi.list).mockReturnValue(new Promise((r) => { resolve = r }) as never)
    const { recheckPaymentAvailability } = usePaymentAvailability()

    const first = recheckPaymentAvailability()
    const second = recheckPaymentAvailability()
    resolve([{ key: 'payment', enabled: true, reason: null }])
    await Promise.all([first, second])

    expect(capabilitiesApi.list).toHaveBeenCalledTimes(1)
  })

  /** 실패 뒤 다시 시도해 성공하면 실패 표시가 남지 않아야 한다. */
  it('실패한 뒤 성공하면 실패 표시를 지운다', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('Network Error'))
    const { paymentCheckFailed, paymentEnabled, recheckPaymentAvailability } = usePaymentAvailability()
    await recheckPaymentAvailability()

    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    await recheckPaymentAvailability()

    expect(paymentCheckFailed.value).toBe(false)
    expect(paymentEnabled.value).toBe(true)
  })

  /** 재조회 중에는 이전 활성 판정을 믿지 않는다 — 응답 전 잠깐 결제 버튼이 열리면 안 된다. */
  it('재조회가 끝날 때까지 이전 활성 판정을 폐기한다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValueOnce([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    const availability = usePaymentAvailability()
    await availability.loadPaymentAvailability()

    let resolve!: (value: unknown) => void
    vi.mocked(capabilitiesApi.list).mockReturnValueOnce(new Promise((r) => { resolve = r }) as never)
    const pending = availability.loadPaymentAvailability()

    expect(availability.paymentEnabled.value).toBe(false)
    expect(availability.paymentChecked.value).toBe(false)
    expect(availability.paymentChecking.value).toBe(true)

    resolve([{ key: 'payment', enabled: false, reason: '결제 설정이 없습니다' }])
    await pending

    expect(availability.paymentEnabled.value).toBe(false)
    expect(availability.paymentDisabledReason.value).toBe('결제 설정이 없습니다')
  })
})
