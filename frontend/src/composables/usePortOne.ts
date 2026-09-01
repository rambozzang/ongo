import { ref } from 'vue'
import * as PortOne from '@portone/browser-sdk/v2'
import { portoneApi, type PortOneCheckoutIntent } from '@/api/portone'

const initialized = ref(false)
const loading = ref(false)

export const PAYMENT_REDIRECT_PATH = '/payment-redirect'

/**
 * 모바일 결제가 돌아올 곳.
 *
 * PortOne V2 는 모바일·REDIRECTION 결제에서 브라우저를 PG 로 넘긴다. 그때 `redirectUrl`
 * 이 없으면 **돌아올 주소가 없어 결제가 끝나도 우리 화면으로 복귀하지 못한다.** 승인은
 * 됐는데 서버 `complete` 를 못 불러 PENDING 결제만 남는다.
 *
 * `forceRedirect` 는 일부러 켜지 않는다. 데스크톱은 지금처럼 팝업에서 반환값을 받아
 * 그 자리에서 처리하는 편이 흐름이 짧고, 그 경로는 이미 테스트로 고정돼 있다.
 * `redirectUrl` 만 주면 SDK 가 환경에 따라 알아서 고른다.
 */
export function paymentRedirectUrl(): string {
  return `${window.location.origin}${PAYMENT_REDIRECT_PATH}`
}

/** 환경 판별에 필요한 값만 받는다. 순수 함수라야 테스트할 수 있다. */
export interface ClientEnvironment {
  userAgent: string
  maxTouchPoints: number
}

const MOBILE_USER_AGENT =
  /Android|iPhone|iPod|iPad|Windows Phone|webOS|BlackBerry|Opera Mini|IEMobile|Mobile Safari/i

/**
 * PG 기본값에 맡기면 정기결제 수단 발급이 **리디렉션으로 흘러갈** 환경인가.
 *
 * PortOne SDK 계약상 "대부분의 모바일 환경이 리디렉션 방식"이다
 * (`IssueBillingKeyRequestBase.redirectUrl` 주석). 리디렉션은 결과를 **쿼리 파라미터로**
 * 돌려주고, 빌링키 발급의 결과값은 `billingKey` 다 — 그 값 하나로 무기한 반복 청구가
 * 가능하다. 브라우저가 그 URL 로 이동하는 순간 Nginx·CDN·프록시 액세스 로그에 평문으로
 * 먼저 남는다. `replaceState` 로 지워도 늦다 — 자바스크립트가 돌기 전에 기록이 끝난다.
 *
 * ## 예전에는 여기서 결제를 통째로 막았다
 *
 * 그 대가로 **모바일에서는 유료 구독을 시작조차 할 수 없었다.** 지금은 발급 요청에
 * `windowType.mobile = 'IFRAME'` 을 명시해 리디렉션이 아닌 창을 먼저 시도한다
 * ([usePortOne] 의 `registerBillingMethod` 참고).
 *
 * ## 그래도 이 판별이 남아 있는 이유
 *
 * 안전장치는 창 유형이 아니라 **`redirectUrl` 을 주지 않는 것**이다(SDK 문서: 리디렉션
 * 방식은 그 값이 "필수"다). 주지 않으므로 PG 가 IFRAME 을 지원하지 않아도 billingKey 가
 * 우리 URL 로 되돌아올 수 없다 — 대신 발급이 **실패**한다.
 *
 * 그 실패는 이 환경에서만 일어나므로, 사용자에게 "PC 에서 시도해 보라"는 실행 가능한
 * 안내를 붙일 수 있는지 판단하는 데 쓴다. 흐름을 막는 데는 더 이상 쓰지 않는다.
 */
export function isRedirectOnlyBillingEnvironment(env: ClientEnvironment): boolean {
  if (MOBILE_USER_AGENT.test(env.userAgent)) return true
  /*
   * iPadOS 13+ 는 데스크톱 Safari 로 위장해 UA 에 "Macintosh" 만 남는다. 터치 포인트가
   * 유일한 단서다. macOS 는 트랙패드가 있어도 maxTouchPoints 가 0 이라 오판하지 않는다.
   */
  return /Macintosh/i.test(env.userAgent) && env.maxTouchPoints > 1
}

function currentEnvironment(): ClientEnvironment {
  return {
    userAgent: navigator.userAgent,
    maxTouchPoints: navigator.maxTouchPoints ?? 0,
  }
}

/**
 * 창 유형을 지원하지 않아 발급이 거절됐음을 가리키는 코드 조각.
 *
 * PG 마다 코드 문자열이 달라 정확한 목록을 만들 수 없다. 그래서 **넓게 알아보되 좁게
 * 바꾼다** — 여기 걸리면 안내 문구만 달라지고, 걸리지 않으면 PG 가 준 사유를 그대로
 * 보여 준다. 어느 쪽이든 발급은 실패로 끝난다.
 */
const UNSUPPORTED_WINDOW_CODE_HINTS = ['WINDOW', 'NOT_SUPPORTED', 'UNSUPPORTED', 'IFRAME']

/**
 * 빌링키 발급 실패를 **사용자가 무엇을 해야 하는지 아는** 문구로 바꾼다.
 *
 * PG 가 모바일 IFRAME 발급을 지원하지 않으면 원문 오류는 대개 창 유형 이야기다. 그대로
 * 보여 주면 사용자는 자기가 뭘 잘못했는지 알 수 없다. 이 환경에서 실제로 할 수 있는
 * 일은 PC 에서 다시 시도하는 것뿐이므로 그렇게 말해 준다.
 *
 * 그 밖의 실패(카드 인증 거절 등)는 **PG 문구를 그대로 쓴다.** 우리가 고쳐 쓰면
 * 사용자가 카드사에 문의할 근거를 잃는다.
 */
export function billingKeyIssueErrorMessage(
  code: string,
  message: string | undefined,
  env: ClientEnvironment = currentEnvironment(),
): string {
  const unsupportedWindow = UNSUPPORTED_WINDOW_CODE_HINTS.some((hint) =>
    code.toUpperCase().includes(hint),
  )
  if (unsupportedWindow && isRedirectOnlyBillingEnvironment(env)) {
    return '이 브라우저에서는 정기결제 수단을 등록할 수 없습니다. PC 브라우저에서 다시 시도해 주세요.'
  }
  return message ?? '정기결제 수단 등록이 취소되었거나 실패했습니다.'
}

/**
 * 결제는 확인됐는데 정기결제 수단이 등록되지 않은 상태.
 *
 * 구독에서 가장 조용히 나빠지는 조합이다. 첫 달 요금은 걷히고 화면도 정상이지만 갱신
 * 수단이 없어, 한 달 뒤 아무 안내 없이 PAST_DUE 로 내려간다. 그때는 사용자도 우리도
 * 원인을 되짚기 어렵다.
 *
 * 그래서 조용히 넘기지 않고 **지금** 알린다. 사용자가 할 수 있는 일이 재시도가 아니라
 * 문의뿐이라 그렇게 안내한다 — 돈은 이미 빠져나갔으므로 다시 결제하게 두면 안 된다.
 */
function billingMethodMissingAfterPaymentError(): Error {
  return new Error(
    '결제는 확인됐지만 정기결제 수단이 등록되지 않았습니다. 중복 결제하지 마시고 고객지원에 문의해 주세요.',
  )
}

/** PortOne은 실패 응답에만 optional `code`를 넣는다. 빈 문자열도 오류 코드로 취급한다. */
export function isPortOnePaymentError(
  result: Awaited<ReturnType<typeof PortOne.requestPayment>>,
): boolean {
  return result != null && result.code !== undefined
}

export function usePortOne() {
  async function ensureInitialized() {
    initialized.value = true
  }

  /**
   * 정기결제 수단을 발급받아 서버에 등록한다.
   *
   * ## 왜 결제보다 먼저인가
   *
   * 등록에 실패한 채로 결제만 받으면 첫 달 요금은 걷히지만 다음 달 갱신이 불가능해진다.
   * 고객은 결제한 줄 알고, 한 달 뒤 아무 안내 없이 PAST_DUE 로 내려간다. 그래서 수단
   * 등록이 성립하지 않으면 **결제창을 아예 열지 않는다.**
   *
   * ## billingKey 를 어떻게 다루는가
   *
   * 이 함수 안에서만 존재한다. localStorage·store·URL·로그 어디에도 남기지 않고,
   * 곧바로 HTTPS POST 본문으로 서버에 넘긴 뒤 버린다.
   *
   * @returns 등록에 성공하면 true. 사용자가 창을 닫았으면 false.
   * @throws 발급 실패·서버 저장 실패. 호출자가 결제를 중단해야 한다.
   */
  async function registerBillingMethod(intent: PortOneCheckoutIntent): Promise<boolean> {
    const issued = await PortOne.requestIssueBillingKey({
      storeId: intent.storeId,
      channelKey: intent.channelKey,
      billingKeyMethod: 'CARD',
      issueName: intent.orderName,
      /*
       * **모바일에서도 리디렉션이 아닌 창을 먼저 요청한다.**
       *
       * 이 값을 주지 않으면 PG 기본값을 따르고, 대부분의 모바일 PG 기본값이
       * REDIRECTION 이다(SDK `IssueBillingKeyRequestBase.redirectUrl` 주석). 그러면
       * billingKey 가 쿼리 파라미터로 돌아와 액세스 로그에 남는다.
       *
       * `pc` 는 **일부러 비운다.** 데스크톱은 지금까지 PG 기본 창으로 정상 동작했고,
       * 여기서 창 유형을 지정하면 그 검증된 경로를 바꾸게 된다.
       */
      windowType: { mobile: 'IFRAME' },
      /*
       * **redirectUrl / forceRedirect 를 절대 넣지 않는다.**
       *
       * 이것이 이 함수의 유일한 구조적 안전장치다. SDK 문서상 리디렉션 방식은
       * `redirectUrl` 이 **필수**이므로, 주지 않으면 PG 가 IFRAME 을 지원하지 않더라도
       * billingKey 가 우리 URL 로 되돌아올 수 없다 — 대신 발급이 실패로 끝난다.
       *
       * 창 유형은 "시도"이고 이쪽이 "보장"이다. 둘의 역할을 바꾸지 말 것.
       */
      customer: { email: intent.customerEmail, fullName: intent.customerName },
    })

    // undefined = 사용자가 발급 창을 닫았다. 오류가 아니므로 조용히 중단한다.
    if (!issued) return false

    if (issued.code !== undefined) {
      throw new Error(billingKeyIssueErrorMessage(issued.code, issued.message))
    }

    /*
     * code 는 없는데 billingKey 가 비어 있는 응답. SDK 타입상 일어나지 않아야 하지만
     * 실제로 오면 빈 문자열을 서버에 보내게 되고, 서버는 그걸 거절한다 — 그 왕복을
     * 하는 동안 사용자는 카드 등록이 된 줄 안다. 더 나쁘게는 서버가 통과시키면 청구
     * 불가능한 구독이 만들어진다. 여기서 끊는다.
     */
    if (!issued.billingKey) {
      throw new Error('정기결제 수단 정보를 받지 못했습니다. 다시 시도해 주세요.')
    }

    await portoneApi.registerBillingKey(issued.billingKey)
    return true
  }

  async function openSubscriptionCheckout(
    planType: string,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
    billingCycle: 'MONTHLY' | 'YEARLY' = 'MONTHLY',
  ) {
    /*
     * **환경으로 미리 막지 않는다.**
     *
     * 예전에는 모바일이면 여기서 곧바로 예외를 던졌다. 안전했지만 대가가 컸다 —
     * 모바일 사용자는 플랜을 고르고 정기결제에 동의한 **마지막 클릭에서야** 실패를
     * 알게 됐고, 유료 구독 전환은 그 환경에서 0 이었다.
     *
     * 이제 `registerBillingMethod` 가 `windowType.mobile = 'IFRAME'` 으로 리디렉션이
     * 아닌 창을 시도한다. 지원되면 그대로 결제까지 이어지고, 지원되지 않으면
     * **발급이 실패한다** — `redirectUrl` 을 주지 않으므로 billingKey 가 URL 로 새는
     * 경로는 그대로 닫혀 있다.
     *
     * 실패해도 결제창은 열리지 않는다. 아래 순서가 그것을 보장한다: 발급 → 서버 등록이
     * 모두 성공해야 `requestPayment` 에 도달한다. 발급 실패로 남는 PENDING intent 는
     * `reconcileAfterAbort` 가 PG 재조회로 닫는다.
     */
    loading.value = true
    try {
      const intent = await portoneApi.createSubscriptionCheckout(planType, billingCycle)

      /*
       * 정기결제 수단 등록이 먼저다. 발급 취소·실패·서버 저장 실패면 여기서 끝나고
       * requestPayment 는 호출되지 않는다 — 갱신 불가능한 구독을 만들지 않기 위해서다.
       */
      let registered: boolean
      try {
        registered = await registerBillingMethod(intent)
      } catch (error) {
        /*
         * 빌링키 단계에서 멈춰도 intent 는 이미 PENDING 으로 저장돼 있다. PG 를 재조회해
         * 확정된 실패일 때만 닫고, 조회 불능이면 보수적인 상태 확인 오류를 낸다.
         *
         * **성공은 알리지 않는다(`notifySuccess = false`).** 여기까지 왔다는 것은 갱신
         * 수단이 등록되지 않았다는 뜻이고, 구독에서 그것은 성공이 아니다.
         */
        const reconciled = await reconcileAfterAbort(intent.paymentId, callbacks, true, false)
        if (reconciled === 'COMPLETED') throw billingMethodMissingAfterPaymentError()
        if (reconciled === 'PENDING') {
          throw new Error('결제 상태가 아직 확정되지 않았습니다. 결제 내역을 확인해 주세요.')
        }
        throw error
      }
      if (!registered) {
        // 위 catch 와 같은 이유로 성공을 알리지 않는다.
        const reconciled = await reconcileAfterAbort(intent.paymentId, callbacks, true, false)
        if (reconciled === 'COMPLETED') throw billingMethodMissingAfterPaymentError()
        if (reconciled === 'PENDING') {
          throw new Error('결제 상태가 아직 확정되지 않았습니다. 결제 내역을 확인해 주세요.')
        }
        return
      }

      const result = await PortOne.requestPayment({
        storeId: intent.storeId,
        channelKey: intent.channelKey,
        paymentId: intent.paymentId,
        orderName: intent.orderName,
        totalAmount: intent.amount,
        currency: intent.currency,
        payMethod: 'CARD',
        redirectUrl: paymentRedirectUrl(),
        customer: { email: intent.customerEmail, fullName: intent.customerName },
      })
      await completeResult(result, callbacks, intent.paymentId)
    } finally {
      loading.value = false
    }
  }

  async function openCreditCheckout(
    packageName: string,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
  ) {
    loading.value = true
    try {
      const intent = await portoneApi.createCreditCheckout(packageName)
      const result = await PortOne.requestPayment({
        storeId: intent.storeId,
        channelKey: intent.channelKey,
        paymentId: intent.paymentId,
        orderName: intent.orderName,
        totalAmount: intent.amount,
        currency: intent.currency,
        payMethod: 'CARD',
        redirectUrl: paymentRedirectUrl(),
        customer: { email: intent.customerEmail, fullName: intent.customerName },
      })
      await completeResult(result, callbacks, intent.paymentId)
    } finally {
      loading.value = false
    }
  }

  async function completeResult(
    result: Awaited<ReturnType<typeof PortOne.requestPayment>>,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
    paymentId?: string,
  ) {
    if (!result) {
      if (!paymentId) {
        callbacks?.onClose?.()
        return
      }
      const reconciled = await reconcileAfterAbort(paymentId, callbacks)
      if (reconciled === 'PENDING') {
        throw new Error('결제 상태가 아직 확정되지 않았습니다. 결제 내역을 확인해 주세요.')
      }
      return
    }
    if (isPortOnePaymentError(result)) {
      // SDK 오류 응답은 paymentId를 생략할 수 있다. 그래도 요청에 사용한 intent id로
      // 재조회해야 승인 후 오류 응답이 남긴 PENDING 결제를 놓치지 않는다.
      const reconciliationPaymentId = result.paymentId ?? paymentId
      if (reconciliationPaymentId) {
        const reconciled = await reconcileAfterAbort(reconciliationPaymentId, callbacks, false)
        if (reconciled === 'COMPLETED') return
        if (reconciled === 'PENDING') {
          throw new Error('결제 상태가 아직 확정되지 않았습니다. 결제 내역을 확인해 주세요.')
        }
      }
      throw new Error(result.message ?? '포트원 결제가 취소되거나 실패했습니다.')
    }
    const confirmedPaymentId = result.paymentId ?? paymentId
    if (!confirmedPaymentId) {
      throw new Error('결제 식별자를 받지 못했습니다. 결제 내역을 확인해 주세요.')
    }
    await portoneApi.complete(confirmedPaymentId)
    callbacks?.onSuccess?.()
  }

  async function reconcileAfterAbort(
    paymentId: string,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
    notifyClose = true,
    /**
     * 재조회가 `COMPLETED` 일 때 성공을 알릴지.
     *
     * **빌링키가 아직 등록되지 않은 중단 분기에서는 꺼야 한다.** 구독에서 "성공"은
     * 결제만으로 성립하지 않는다 — 갱신 수단이 없으면 첫 달만 걷히고 한 달 뒤 아무
     * 안내 없이 PAST_DUE 로 내려간다. 그 상태를 성공으로 알리면 사용자는 구독이 된
     * 줄 알고, 문제는 다음 청구일에야 드러난다.
     *
     * 기본값이 `true` 인 이유는 나머지 호출자(초회 결제 후 재조정, 크레딧 결제)가
     * **이미 그 시점에 등록을 마쳤거나 애초에 등록이 필요 없기** 때문이다.
     */
    notifySuccess = true,
  ): Promise<'COMPLETED' | 'FAILED' | 'PENDING'> {
    let result: Awaited<ReturnType<typeof portoneApi.reconcile>>
    try {
      result = await portoneApi.reconcile(paymentId)
    } catch {
      throw new Error('결제 상태를 확인하지 못했습니다. 결제 내역을 확인해 주세요.')
    }

    if (result.status === 'COMPLETED') {
      if (notifySuccess) callbacks?.onSuccess?.()
      return 'COMPLETED'
    }
    if (result.status === 'FAILED' || result.status === 'REFUNDED') {
      if (notifyClose) callbacks?.onClose?.()
      return 'FAILED'
    }
    return 'PENDING'
  }

  return { initialized, loading, ensureInitialized, openSubscriptionCheckout, openCreditCheckout }
}
