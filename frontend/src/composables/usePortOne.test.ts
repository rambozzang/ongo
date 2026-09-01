// describe/it은 vitest에서 가져와야 러너가 테스트를 수집한다.
// node:test에서 가져오면 Node 러너에 등록되어 vitest가 "No test suite found"로 실패한다.
// 단언은 node:assert/strict 그대로 쓴다 — AssertionError를 vitest가 실패로 정확히 보고한다.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import assert from 'node:assert/strict'
import * as PortOne from '@portone/browser-sdk/v2'
import { portoneApi } from '@/api/portone'
import {
  PAYMENT_REDIRECT_PATH,
  billingKeyIssueErrorMessage,
  isPortOnePaymentError,
  isRedirectOnlyBillingEnvironment,
  usePortOne,
} from './usePortOne'

// 구독 결제는 빌링키 발급을 먼저 지난다. 그 계약은 usePortOneBillingKey.test.ts 가 본다.
vi.mock('@portone/browser-sdk/v2', () => ({
  requestPayment: vi.fn(),
  requestIssueBillingKey: vi.fn(),
}))
vi.mock('@/api/portone', () => ({
  portoneApi: {
    createSubscriptionCheckout: vi.fn(),
    createCreditCheckout: vi.fn(),
    registerBillingKey: vi.fn(),
    complete: vi.fn(),
    reconcile: vi.fn(),
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

/**
 * 리디렉션 환경 판별.
 *
 * 정기결제 수단 발급을 리디렉션으로 하면 billingKey 가 쿼리에 실려 우리 서버 액세스
 * 로그에 평문으로 남는다. 그래서 그런 환경은 **시작 자체를 막는다.** 판별이 틀리면
 * 데스크톱 사용자가 결제를 못 하거나(과차단), 모바일에서 자격증명이 새어 나간다(미차단).
 */
describe('isRedirectOnlyBillingEnvironment', () => {
  const MAC =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'

  it('데스크톱은 막지 않는다', () => {
    expect(isRedirectOnlyBillingEnvironment({ userAgent: MAC, maxTouchPoints: 0 })).toBe(false)
    expect(
      isRedirectOnlyBillingEnvironment({
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36',
        maxTouchPoints: 0,
      }),
    ).toBe(false)
    expect(
      isRedirectOnlyBillingEnvironment({
        userAgent: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
        maxTouchPoints: 0,
      }),
    ).toBe(false)
  })

  /** 터치 스크린 노트북을 모바일로 오판하면 정상 결제가 막힌다. */
  it('터치 지원 윈도우 데스크톱은 막지 않는다', () => {
    expect(
      isRedirectOnlyBillingEnvironment({
        userAgent:
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36',
        maxTouchPoints: 10,
      }),
    ).toBe(false)
  })

  it('모바일 브라우저를 막는다', () => {
    const mobiles = [
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1',
      'Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36',
      'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1',
      'Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
    ]
    for (const userAgent of mobiles) {
      expect(isRedirectOnlyBillingEnvironment({ userAgent, maxTouchPoints: 5 }), userAgent).toBe(true)
    }
  })

  /** iPadOS 13+ 는 데스크톱 Safari 로 위장한다. 터치 포인트가 유일한 단서다. */
  it('데스크톱으로 위장한 iPad 를 터치 포인트로 잡는다', () => {
    expect(isRedirectOnlyBillingEnvironment({ userAgent: MAC, maxTouchPoints: 5 })).toBe(true)
  })
})

/**
 * 발급 실패 문구.
 *
 * PG 가 모바일 IFRAME 을 지원하지 않으면 원문은 창 유형 이야기다("IFRAME window type is
 * not supported"). 그대로 보여 주면 사용자는 자기가 뭘 잘못했는지 알 수 없고, 이 환경에서
 * 실제로 할 수 있는 일은 PC 에서 다시 시도하는 것뿐이다.
 *
 * 반대로 카드 거절 같은 실패까지 고쳐 쓰면 사용자가 카드사에 문의할 근거를 잃는다.
 */
describe('billingKeyIssueErrorMessage', () => {
  const IPHONE = {
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1',
    maxTouchPoints: 5,
  }
  const DESKTOP = {
    userAgent:
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
    maxTouchPoints: 0,
  }

  it('모바일 창 미지원은 PC 에서 시도하라고 안내한다', () => {
    expect(
      billingKeyIssueErrorMessage('WINDOW_TYPE_NOT_SUPPORTED', 'IFRAME is not supported', IPHONE),
    ).toMatch(/PC 브라우저/)
  })

  /** 카드 거절은 PG 문구를 그대로 쓴다. 고쳐 쓰면 문의할 근거가 사라진다. */
  it('그 밖의 실패는 PG 문구를 그대로 쓴다', () => {
    expect(billingKeyIssueErrorMessage('CARD_ERROR', '카드 한도를 초과했습니다', IPHONE)).toBe(
      '카드 한도를 초과했습니다',
    )
  })

  /**
   * 데스크톱에서 창 오류가 나면 PC 에서 시도하라는 안내는 도움이 되지 않는다 —
   * 이미 PC 다. PG 사유를 그대로 보여 준다.
   */
  it('데스크톱에서는 PC 안내로 바꾸지 않는다', () => {
    expect(
      billingKeyIssueErrorMessage('WINDOW_TYPE_NOT_SUPPORTED', 'not supported', DESKTOP),
    ).toBe('not supported')
  })

  it('사유가 없으면 기본 문구를 쓴다', () => {
    expect(billingKeyIssueErrorMessage('UNKNOWN', undefined, DESKTOP)).toBe(
      '정기결제 수단 등록이 취소되었거나 실패했습니다.',
    )
  })
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
    vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'FAILED' } as never)
    // 빌링키 발급·저장은 통과시키고, 이 파일은 그 뒤 결제 결과 처리만 본다.
    vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue({
      transactionType: 'ISSUE_BILLING_KEY',
      billingKey: 'bk_test',
    } as never)
    vi.mocked(portoneApi.registerBillingKey).mockResolvedValue(undefined as never)
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
      expect(portoneApi.reconcile).toHaveBeenCalledExactlyOnceWith(INTENT.paymentId)
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

    it('실패 콜백이어도 재조회 결과가 PAID면 성공을 놓치지 않는다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({
        code: 'CLIENT_TIMEOUT',
        paymentId: INTENT.paymentId,
      } as never)
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'COMPLETED' } as never)
      const cb = callbacks()

      await usePortOne().openCreditCheckout('CREDIT_1000', cb)

      expect(portoneApi.reconcile).toHaveBeenCalledExactlyOnceWith(INTENT.paymentId)
      expect(cb.onSuccess).toHaveBeenCalledTimes(1)
      expect(cb.onClose).not.toHaveBeenCalled()
      expect(portoneApi.complete).not.toHaveBeenCalled()
    })

    it('재조회 결과가 이미 환불된 결제면 대기 오류 대신 닫힘으로 끝낸다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue(undefined as never)
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'REFUNDED' } as never)
      const cb = callbacks()

      await usePortOne().openCreditCheckout('CREDIT_1000', cb)

      expect(cb.onClose).toHaveBeenCalledTimes(1)
      expect(cb.onSuccess).not.toHaveBeenCalled()
    })

    it('사유가 없는 실패에도 기본 문구로 throw 한다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ code: 'UNKNOWN' } as never)
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow(
        '포트원 결제가 취소되거나 실패했습니다.',
      )
      expect(cb.onSuccess).not.toHaveBeenCalled()
    })

    it('오류 응답에 paymentId가 없어도 요청 intent로 결제 상태를 재조회한다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ code: 'CLIENT_TIMEOUT' } as never)
      const cb = callbacks()

      await expect(usePortOne().openCreditCheckout('CREDIT_1000', cb)).rejects.toThrow()

      expect(portoneApi.reconcile).toHaveBeenCalledExactlyOnceWith(INTENT.paymentId)
      expect(portoneApi.complete).not.toHaveBeenCalled()
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

    /**
     * 모바일은 팝업이 아니라 PG 로 브라우저를 넘긴다. `redirectUrl` 이 없으면 결제가
     * 끝나도 돌아올 곳이 없어 서버 complete 를 못 부르고 PENDING 만 남는다.
     */
    it('모바일 복귀를 위해 redirectUrl 을 항상 넘긴다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      const request = vi.mocked(PortOne.requestPayment).mock.calls[0][0] as Record<string, unknown>
      expect(request.redirectUrl).toBe(`${window.location.origin}${PAYMENT_REDIRECT_PATH}`)
      // forceRedirect 를 켜면 데스크톱도 페이지 이동이 된다. 반환값 흐름을 유지한다.
      expect(request.forceRedirect).toBeUndefined()
    })

    /**
     * **빌링키 발급에는 redirectUrl 을 넣지 않는다.**
     *
     * 넣으면 모바일이 리디렉션으로 billingKey 를 쿼리에 실어 보내고, 그 요청 라인이
     * 우리 Nginx·CDN 액세스 로그에 평문으로 남는다. 그 값 하나로 무기한 반복 청구가
     * 가능하다.
     */
    it('빌링키 발급에는 redirectUrl 을 넘기지 않는다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      const issue = vi.mocked(PortOne.requestIssueBillingKey).mock.calls[0][0] as Record<string, unknown>
      expect(issue.redirectUrl).toBeUndefined()
      expect(issue.forceRedirect).toBeUndefined()
      // 결제 복귀 경로가 발급 요청에 새어 들어가서도 안 된다.
      expect(JSON.stringify(issue)).not.toContain(PAYMENT_REDIRECT_PATH)
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

    /** 크레딧 결제도 모바일에서 돌아와야 한다. 한쪽만 빠지면 그 흐름만 조용히 깨진다. */
    it('크레딧 결제도 redirectUrl 을 넘긴다', async () => {
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openCreditCheckout('CREDIT_1000', callbacks())

      const request = vi.mocked(PortOne.requestPayment).mock.calls[0][0] as Record<string, unknown>
      expect(request.redirectUrl).toBe(`${window.location.origin}${PAYMENT_REDIRECT_PATH}`)
      expect(request.forceRedirect).toBeUndefined()
    })
  })

  /**
   * 모바일 구독 결제.
   *
   * ## 계약이 바뀌었다
   *
   * 예전에는 모바일이면 **아무것도 시작하지 않고** 예외를 던졌다. 안전했지만 그 환경의
   * 유료 전환이 0 이었다 — 사용자는 플랜을 고르고 정기결제에 동의한 마지막 클릭에서야
   * 실패를 알았다.
   *
   * 지금은 발급 요청에 `windowType.mobile = 'IFRAME'` 을 명시해 리디렉션이 아닌 창을
   * 시도한다. 안전장치는 창 유형이 아니라 **`redirectUrl` 을 주지 않는 것**이다 —
   * SDK 문서상 리디렉션 방식은 그 값이 필수이므로, 주지 않으면 PG 가 IFRAME 을
   * 지원하지 않아도 billingKey 가 우리 URL 로 돌아올 수 없고 발급이 실패로 끝난다.
   *
   * 그래서 이 블록이 지키는 것은 둘이다.
   *  1. 모바일에서도 **시도한다** (IFRAME 으로, redirectUrl 없이)
   *  2. 지원되지 않으면 **성공한 것처럼 보이지 않는다** (결제창·성공 콜백 없음)
   */
  describe('모바일 구독 결제', () => {
    /*
     * jsdom 의 navigator 는 maxTouchPoints 를 자체 속성으로 갖지 않아 spyOn 이 실패한다.
     * defineProperty 로 직접 덮는다. (프로덕션 코드가 `?? 0` 으로 방어하는 이유이기도 하다.)
     */
    function stubUserAgent(userAgent: string, maxTouchPoints: number) {
      Object.defineProperty(navigator, 'userAgent', { value: userAgent, configurable: true })
      Object.defineProperty(navigator, 'maxTouchPoints', {
        value: maxTouchPoints,
        configurable: true,
      })
    }

    const IPHONE =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1'

    /** 창 유형을 지원하지 않는 PG 가 돌려주는 모양. 코드 문자열은 PG 마다 다르다. */
    const WINDOW_UNSUPPORTED = {
      transactionType: 'ISSUE_BILLING_KEY',
      billingKey: '',
      code: 'WINDOW_TYPE_NOT_SUPPORTED',
      message: 'IFRAME window type is not supported by this channel',
    }

    /**
     * **핵심.** 모바일 기본값은 REDIRECTION 이다. 명시하지 않으면 billingKey 가 쿼리
     * 파라미터로 돌아와 액세스 로그에 평문으로 남는다.
     */
    it('모바일에서 빌링키 발급 창을 IFRAME 으로 요청한다', async () => {
      stubUserAgent(IPHONE, 5)
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      const issue = vi.mocked(PortOne.requestIssueBillingKey).mock.calls[0][0] as Record<
        string,
        unknown
      >
      expect(issue.windowType).toEqual({ mobile: 'IFRAME' })
    })

    /**
     * **구조적 안전장치.** SDK 문서상 리디렉션 방식은 `redirectUrl` 이 필수다. 주지
     * 않으면 PG 가 IFRAME 을 지원하지 않아도 billingKey 가 우리 URL 로 돌아올 수 없다.
     */
    it('모바일에서도 빌링키 발급에 redirectUrl 을 넘기지 않는다', async () => {
      stubUserAgent(IPHONE, 5)
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      const issue = vi.mocked(PortOne.requestIssueBillingKey).mock.calls[0][0] as Record<
        string,
        unknown
      >
      expect(issue.redirectUrl).toBeUndefined()
      expect(issue.forceRedirect).toBeUndefined()
      expect(JSON.stringify(issue)).not.toContain(PAYMENT_REDIRECT_PATH)
    })

    /** 정상 응답이면 모바일도 서버 등록 → 결제 → 서버 검증까지 그대로 이어진다. */
    it('발급에 성공하면 서버 등록과 결제까지 이어진다', async () => {
      stubUserAgent(IPHONE, 5)
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
      const cb = callbacks()

      await usePortOne().openSubscriptionCheckout('STARTER', cb)

      expect(portoneApi.registerBillingKey).toHaveBeenCalledExactlyOnceWith('bk_test')
      expect(PortOne.requestPayment).toHaveBeenCalledTimes(1)
      expect(portoneApi.complete).toHaveBeenCalledExactlyOnceWith(INTENT.paymentId)
      expect(cb.onSuccess).toHaveBeenCalledTimes(1)
    })

    /**
     * **핵심 fail-closed.** IFRAME 미지원 PG 에서 결제가 성공한 것처럼 보이면 안 된다.
     */
    it('창 유형 미지원이면 결제창도 성공 콜백도 없다', async () => {
      stubUserAgent(IPHONE, 5)
      vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue(WINDOW_UNSUPPORTED as never)
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow()

      expect(portoneApi.registerBillingKey).not.toHaveBeenCalled()
      expect(PortOne.requestPayment).not.toHaveBeenCalled()
      expect(portoneApi.complete).not.toHaveBeenCalled()
      expect(cb.onSuccess).not.toHaveBeenCalled()
    })

    /** 원문 오류("IFRAME window type is not supported")로는 사용자가 할 일을 알 수 없다. */
    it('창 유형 미지원은 사용자가 할 수 있는 일로 안내한다', async () => {
      stubUserAgent(IPHONE, 5)
      vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue(WINDOW_UNSUPPORTED as never)

      await expect(usePortOne().openSubscriptionCheckout('STARTER', callbacks())).rejects.toThrow(
        /PC 브라우저/,
      )
    })

    /** 발급이 비면 서버로 아무것도 보내지 않는다 — 청구 불가능한 구독을 만들지 않는다. */
    it('빈 발급 응답이면 서버 등록도 결제도 하지 않는다', async () => {
      stubUserAgent(IPHONE, 5)
      vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue({
        transactionType: 'ISSUE_BILLING_KEY',
        billingKey: '',
      } as never)
      const cb = callbacks()

      await expect(usePortOne().openSubscriptionCheckout('STARTER', cb)).rejects.toThrow()

      expect(portoneApi.registerBillingKey).not.toHaveBeenCalled()
      expect(PortOne.requestPayment).not.toHaveBeenCalled()
      expect(cb.onSuccess).not.toHaveBeenCalled()
    })

    /** 데스크톱 위장 iPad 도 같은 경로다 — 판별이 흐름을 막지는 않는다. */
    it('데스크톱으로 위장한 iPad 도 IFRAME 으로 시도한다', async () => {
      stubUserAgent(
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15',
        5,
      )
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)

      await usePortOne().openSubscriptionCheckout('STARTER', callbacks())

      expect(portoneApi.createSubscriptionCheckout).toHaveBeenCalledTimes(1)
      const issue = vi.mocked(PortOne.requestIssueBillingKey).mock.calls[0][0] as Record<
        string,
        unknown
      >
      expect(issue.windowType).toEqual({ mobile: 'IFRAME' })
    })

    it('발급이 실패해도 loading 이 걸린 채 남지 않는다', async () => {
      stubUserAgent('Mozilla/5.0 (Linux; Android 14) Chrome/120.0 Mobile Safari/537.36', 5)
      vi.mocked(PortOne.requestIssueBillingKey).mockResolvedValue(WINDOW_UNSUPPORTED as never)
      const portone = usePortOne()

      await expect(portone.openSubscriptionCheckout('STARTER', callbacks())).rejects.toThrow()

      expect(portone.loading.value).toBe(false)
    })

    /** 크레딧 결제는 빌링키 발급이 없어 모바일에서도 막을 이유가 없다. */
    it('크레딧 결제는 모바일에서도 막지 않는다', async () => {
      stubUserAgent(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1',
        5,
      )
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
      const cb = callbacks()

      await usePortOne().openCreditCheckout('CREDIT_1000', cb)

      expect(portoneApi.createCreditCheckout).toHaveBeenCalledTimes(1)
      expect(cb.onSuccess).toHaveBeenCalledTimes(1)
    })

    it('데스크톱은 기존 흐름을 그대로 탄다', async () => {
      stubUserAgent(
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
        0,
      )
      vi.mocked(PortOne.requestPayment).mockResolvedValue({ paymentId: INTENT.paymentId } as never)
      const cb = callbacks()

      await usePortOne().openSubscriptionCheckout('STARTER', cb)

      expect(portoneApi.createSubscriptionCheckout).toHaveBeenCalledTimes(1)
      expect(PortOne.requestIssueBillingKey).toHaveBeenCalledTimes(1)
      expect(portoneApi.registerBillingKey).toHaveBeenCalledTimes(1)
      expect(cb.onSuccess).toHaveBeenCalledTimes(1)
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
