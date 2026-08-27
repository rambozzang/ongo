import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as PortOne from '@portone/browser-sdk/v2'
import { usePortOne } from './usePortOne'
import { portoneApi } from '@/api/portone'

/**
 * 구독 결제 전 정기결제 수단 등록.
 *
 * 등록에 실패한 채로 결제만 받으면 첫 달 요금은 걷히지만 다음 달 갱신이 불가능해진다.
 * 고객은 결제한 줄 알고, 한 달 뒤 아무 안내 없이 PAST_DUE 로 내려간다.
 *
 * 그래서 여기서 고정하는 것은 **순서와 중단 조건**이다. 수단 등록이 성립하지 않으면
 * `requestPayment` 가 호출되면 안 된다.
 */
vi.mock('@portone/browser-sdk/v2', () => ({
  requestIssueBillingKey: vi.fn(),
  requestPayment: vi.fn(),
}))

vi.mock('@/api/portone', () => ({
  portoneApi: {
    createSubscriptionCheckout: vi.fn(),
    createCreditCheckout: vi.fn(),
    registerBillingKey: vi.fn(),
    complete: vi.fn(),
  },
}))

const intent = {
  paymentId: 'ongo-1',
  storeId: 'store-1',
  channelKey: 'channel-1',
  amount: 19900,
  currency: 'KRW' as const,
  orderName: 'Pro 구독',
  customerEmail: 'a@b.c',
  customerName: '테스터',
}

const issued = { transactionType: 'ISSUE_BILLING_KEY', billingKey: 'bk_live_secret' }
const paid = { paymentId: 'ongo-1', transactionType: 'PAYMENT' }

describe('usePortOne 정기결제 수단 등록', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(portoneApi.createSubscriptionCheckout).mockResolvedValue(intent as never)
    vi.mocked(portoneApi.createCreditCheckout).mockResolvedValue(intent as never)
    vi.mocked(portoneApi.registerBillingKey).mockResolvedValue(undefined as never)
    vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'COMPLETED' } as never)
    vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue(issued as never)
    vi.mocked(PortOne.requestPayment).mockResolvedValue(paid as never)
  })

  /* ---- 정상 순서 ---- */

  it('구독 결제 전에 빌링키를 발급받아 서버에 등록한다', async () => {
    const order: string[] = []
    vi.mocked(PortOne.requestIssueBillingKey).mockImplementation(async () => {
      order.push('issue')
      return issued as never
    })
    vi.mocked(portoneApi.registerBillingKey).mockImplementation(async () => {
      order.push('register')
    })
    vi.mocked(PortOne.requestPayment).mockImplementation(async () => {
      order.push('pay')
      return paid as never
    })

    await usePortOne().openSubscriptionCheckout('PRO')

    expect(order).toEqual(['issue', 'register', 'pay'])
  })

  it('발급 요청에 상점·채널·카드 수단을 넘긴다', async () => {
    await usePortOne().openSubscriptionCheckout('PRO')

    expect(PortOne.requestIssueBillingKey).toHaveBeenCalledWith(
      expect.objectContaining({
        storeId: 'store-1',
        channelKey: 'channel-1',
        billingKeyMethod: 'CARD',
      }),
    )
  })

  it('발급받은 빌링키를 그대로 서버에 넘긴다', async () => {
    await usePortOne().openSubscriptionCheckout('PRO')

    expect(portoneApi.registerBillingKey).toHaveBeenCalledWith('bk_live_secret')
  })

  /* ---- 중단 조건 ---- */

  /** 사용자가 카드 등록 창을 닫았다. 오류가 아니므로 조용히 중단한다. */
  it('발급 창을 닫으면 결제창을 열지 않고 onClose 를 부른다', async () => {
    vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue(undefined as never)
    const onClose = vi.fn()
    const onSuccess = vi.fn()

    await usePortOne().openSubscriptionCheckout('PRO', { onClose, onSuccess })

    expect(PortOne.requestPayment).not.toHaveBeenCalled()
    expect(portoneApi.registerBillingKey).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('발급이 실패하면 결제창을 열지 않고 오류를 올린다', async () => {
    vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue({
      transactionType: 'ISSUE_BILLING_KEY',
      billingKey: '',
      code: 'CARD_ERROR',
      message: '카드 인증에 실패했습니다.',
    } as never)

    await expect(usePortOne().openSubscriptionCheckout('PRO')).rejects.toThrow('카드 인증에 실패했습니다.')
    expect(PortOne.requestPayment).not.toHaveBeenCalled()
    expect(portoneApi.registerBillingKey).not.toHaveBeenCalled()
  })

  /**
   * code 는 없는데 billingKey 가 비어 있는 비정상 응답. SDK 타입상 일어나지 않아야 하지만,
   * 그대로 보내면 서버 왕복 동안 사용자는 카드 등록이 된 줄 알고, 서버가 통과시키면
   * 청구 불가능한 구독이 만들어진다.
   */
  it.each([
    ['빈 문자열', ''],
    ['undefined', undefined],
  ])('빌링키가 %s 이면 서버 전송도 결제도 하지 않는다', async (_label, billingKey) => {
    vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue({
      transactionType: 'ISSUE_BILLING_KEY',
      billingKey,
    } as never)

    await expect(usePortOne().openSubscriptionCheckout('PRO')).rejects.toThrow(
      '정기결제 수단 정보를 받지 못했습니다. 다시 시도해 주세요.',
    )
    expect(portoneApi.registerBillingKey).not.toHaveBeenCalled()
    expect(PortOne.requestPayment).not.toHaveBeenCalled()
  })

  /**
   * 서버 저장이 실패했는데 결제를 받으면 갱신 불가능한 구독이 만들어진다.
   * 첫 달만 걷히고 다음 달에 조용히 끊긴다.
   */
  it('서버 저장이 실패하면 결제창을 열지 않는다', async () => {
    vi.mocked(portoneApi.registerBillingKey).mockRejectedValue(new Error('저장 실패'))

    await expect(usePortOne().openSubscriptionCheckout('PRO')).rejects.toThrow('저장 실패')
    expect(PortOne.requestPayment).not.toHaveBeenCalled()
    expect(portoneApi.complete).not.toHaveBeenCalled()
  })

  /* ---- 크레딧 결제는 그대로 ---- */

  /** 크레딧은 1회 결제다. 정기결제 수단을 요구하면 기존 흐름이 깨진다. */
  it('크레딧 결제는 빌링키를 발급하지 않는다', async () => {
    await usePortOne().openCreditCheckout('STARTER')

    expect(PortOne.requestIssueBillingKey).not.toHaveBeenCalled()
    expect(portoneApi.registerBillingKey).not.toHaveBeenCalled()
    expect(PortOne.requestPayment).toHaveBeenCalledTimes(1)
    expect(portoneApi.complete).toHaveBeenCalledWith('ongo-1')
  })

  /* ---- 평문 보관 금지 ---- */

  /**
   * 빌링키가 localStorage 나 URL 에 남으면 브라우저를 공유하는 순간 결제 수단이 새고,
   * 그 값 하나로 반복 청구가 가능하다.
   */
  it('빌링키를 localStorage 나 sessionStorage 에 남기지 않는다', async () => {
    const localSpy = vi.spyOn(Storage.prototype, 'setItem')

    await usePortOne().openSubscriptionCheckout('PRO')

    const stored = localSpy.mock.calls.map((call) => String(call[1]))
    expect(stored.some((value) => value.includes('bk_live_secret'))).toBe(false)
    localSpy.mockRestore()
  })
})
