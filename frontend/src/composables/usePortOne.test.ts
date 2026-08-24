// describe/it은 vitest에서 가져와야 러너가 테스트를 수집한다.
// node:test에서 가져오면 Node 러너에 등록되어 vitest가 "No test suite found"로 실패한다.
// 단언은 node:assert/strict 그대로 쓴다 — AssertionError를 vitest가 실패로 정확히 보고한다.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import assert from 'node:assert/strict'
import * as PortOne from '@portone/browser-sdk/v2'
import { portoneApi } from '@/api/portone'
import { isPortOnePaymentError, usePortOne } from './usePortOne'

vi.mock('@portone/browser-sdk/v2', () => ({ requestPayment: vi.fn() }))
vi.mock('@/api/portone', () => ({
  portoneApi: {
    createSubscriptionCheckout: vi.fn(),
    createCreditCheckout: vi.fn(),
    complete: vi.fn(),
  },
}))

/** 서버가 내려주는 결제 intent. 금액·상점 정보는 전부 여기서 온다. */
const INTENT = {
  paymentId: 'pay_20260822_0001',
  storeId: 'store-test',
  channelKey: 'channel-test',
  amount: 9900,
  currency: 'KRW' as const,
  orderName: 'onGo Starter',
  customerEmail: 'creator@example.com',
  customerName: '크리에이터',
}

function callbacks() {
  return { onSuccess: vi.fn(), onClose: vi.fn() }
}

type PaymentResponse = NonNullable<Awaited<ReturnType<typeof PortOne.requestPayment>>>

const response = (overrides: Partial<PaymentResponse> = {}): PaymentResponse => ({
  transactionType: 'PAYMENT',
  txId: 'tx-1',
  paymentId: 'ongo-1',
  ...overrides,
})

describe('isPortOnePaymentError', () => {
  it('treats an omitted result as a user closing the payment UI', () => {
    assert.equal(isPortOnePaymentError(undefined), false)
  })

  it('treats any present error code as a failure, including an empty string', () => {
    assert.equal(isPortOnePaymentError(response({ code: '' })), true)
    assert.equal(isPortOnePaymentError(response({ code: 'PAYMENT_FAILED' })), true)
  })

  it('allows a response without an error code to continue to completion', () => {
    assert.equal(isPortOnePaymentError(response()), false)
  })
})

/*
 * 이 파일이 지키는 것은 "돈을 받았는가"와 "성공이라고 말했는가"가 어긋나지 않는 것이다.
 *
 * completeResult 는 비공개라 공개 진입점으로만 검증한다. 결제창 결과 세 갈래
 * (닫힘 / 실패 / 성공)가 각각 onClose·throw·onSuccess 로만 이어져야 하며,
 * 특히 onSuccess 는 **서버 complete 가 성공한 뒤에만** 불려야 한다.
 * 이 계약이 깨지면 결제 없이 유료 기능이 열린다.
 */
describe('usePortOne 결제 결과 처리 계약', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(portoneApi.createSubscriptionCheckout).mockResolvedValue(INTENT as never)
    vi.mocked(portoneApi.createCreditCheckout).mockResolvedValue(INTENT as never)
    vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'PAID' } as never)
  })

  describe('결제창을 닫은 경우', () => {
    it('null 결과는 onClose 만 부르고 성공으로 처리하지 않는다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue(undefined as never)
      const cb = callbacks()

      await usePortOne().openSubscriptionCheckout('STARTER', cb)

      expect(cb.onClose).toHaveBeenCalledTimes(1)
      expect(cb.onSuccess).not.toHaveBeenCalled()
      // 서버에 결제 완료를 알리지도 않아야 한다.
      expect(portoneApi.complete).not.toHaveBeenCalled()
    })
  })

  describe('결제가 실패한 경우', () => {
    it('오류 code 가 있으면 throw 하고 onSuccess 를 부르지 않는다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({
        code: 'FAILURE_TYPE_PG',
        message: '카드 한도를 초과했습니다',
      } as never)
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow(
        '카드 한도를 초과했습니다',
      )

      expect(cb.onSuccess).not.toHaveBeenCalled()
      expect(cb.onClose).not.toHaveBeenCalled()
      expect(portoneApi.complete).not.toHaveBeenCalled()
    })

    it('빈 문자열 code 도 실패로 취급한다', async () => {
      // code 가 있으면 실패다. 빈 문자열을 falsy 로 흘려보내면 미결제 성공이 된다.
      vi.mocked(PortOne.requestPayment).mockResolvedValue({
        code: '',
        paymentId: INTENT.paymentId,
      } as never)
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow()

      expect(cb.onSuccess).not.toHaveBeenCalled()
      expect(portoneApi.complete).not.toHaveBeenCalled()
    })

    it('사유가 없는 실패에도 기본 문구로 throw 한다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ code: 'UNKNOWN' } as never)
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow(
        '포트원 결제가 취소되거나 실패했습니다.',
      )
      expect(cb.onSuccess).not.toHaveBeenCalled()
    })

    it('서버 complete 가 실패하면 onSuccess 를 부르지 않는다', async () => {
      // 결제창은 성공을 반환했지만 서버 검증이 실패한 경우다.
      // 여기서 onSuccess 를 부르면 검증되지 않은 결제가 성공으로 통과한다.
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
      vi.mocked(portoneApi.complete).mockRejectedValue(new Error('결제 검증 실패'))
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow(
        '결제 검증 실패',
      )

      expect(cb.onSuccess).not.toHaveBeenCalled()
      expect(cb.onClose).not.toHaveBeenCalled()
    })
  })

  describe('결제가 성공한 경우', () => {
    it('서버 complete 가 끝난 뒤에만 onSuccess 를 부른다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
      const cb = callbacks()

      await usePortOne().openSubscriptionCheckout('STARTER', cb)

      expect(portoneApi.complete).toHaveBeenCalledExactlyOnceWith(INTENT.paymentId)
      expect(cb.onSuccess).toHaveBeenCalledTimes(1)
      expect(cb.onClose).not.toHaveBeenCalled()

      // 순서까지 고정한다 — complete 이전에 성공을 알리면 검증 자체가 의미를 잃는다.
      const completeOrder = vi.mocked(portoneApi.complete).mock.invocationCallOrder[0]
      expect(completeOrder).toBeLessThan(cb.onSuccess.mock.invocationCallOrder[0])
    })

    it('청구 금액과 상점 정보는 서버 intent 를 그대로 쓴다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      // 클라이언트가 청구액을 정할 수 없어야 한다.
      expect(vi.mocked(PortOne.requestPayment).mock.calls[0][0]).toMatchObject({
        storeId: INTENT.storeId,
        channelKey: INTENT.channelKey,
        paymentId: INTENT.paymentId,
        totalAmount: INTENT.amount,
        currency: INTENT.currency,
      })
    })

    it('요금제와 결제주기를 서버 체크아웃 생성에 그대로 전달한다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('PRO', callbacks(), 'YEARLY')

      expect(portoneApi.createSubscriptionCheckout).toHaveBeenCalledExactlyOnceWith('PRO', 'YEARLY')
    })

    it('결제주기를 생략하면 월간으로 요청한다', async () => {
      // PaymentModal 은 billing-cycle 을 넘기지 않는다. 기본값이 바뀌면 표시가와 청구액이 어긋난다.
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      expect(portoneApi.createSubscriptionCheckout).toHaveBeenCalledExactlyOnceWith(
        'STARTER',
        'MONTHLY',
      )
    })
  })

  describe('크레딧 결제도 같은 계약을 따른다', () => {
    it('닫으면 onClose, 성공하면 complete 뒤 onSuccess', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue(undefined as never)
      const closed = callbacks()
      await usePortOne().openCreditCheckout('CREDIT_1000', closed)
      expect(closed.onClose).toHaveBeenCalledTimes(1)
      expect(closed.onSuccess).not.toHaveBeenCalled()
      expect(portoneApi.complete).not.toHaveBeenCalled()

      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
      const paid = callbacks()
      await usePortOne().openCreditCheckout('CREDIT_1000', paid)
      expect(portoneApi.complete).toHaveBeenCalledExactlyOnceWith(INTENT.paymentId)
      expect(paid.onSuccess).toHaveBeenCalledTimes(1)
    })

    it('오류 code 는 throw 하고 onSuccess 를 부르지 않는다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ code: 'FAILURE_TYPE_PG' } as never)
      const cb = callbacks()

      await expect(usePortOne().openCreditCheckout('CREDIT_1000', cb)).rejects.toThrow()
      expect(cb.onSuccess).not.toHaveBeenCalled()
      expect(portoneApi.complete).not.toHaveBeenCalled()
    })
  })

  it('실패로 끝나도 loading 이 풀린다', async () => {
    // loading 이 걸린 채 남으면 재시도 버튼이 영구히 잠긴다.
    vi.mocked(PortOne.requestPayment).mockResolvedValue({ code: 'FAILURE_TYPE_PG' } as never)
    const portone = usePortOne()

    await expect(portone.openSubscriptionCheckout('STARTER', callbacks())).rejects.toThrow()

    expect(portone.loading.value).toBe(false)
  })

  it('콜백을 넘기지 않아도 성공·닫힘 경로가 터지지 않는다', async () => {
    vi.mocked(PortOne.requestPayment).mockResolvedValue(undefined as never)
    await expect(usePortOne().openSubscriptionCheckout('STARTER')).resolves.toBeUndefined()

    vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
    await expect(usePortOne().openSubscriptionCheckout('STARTER')).resolves.toBeUndefined()
    expect(portoneApi.complete).toHaveBeenCalledTimes(1)
  })
})
