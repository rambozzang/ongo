/**
 * 결제를 지금 시작할 수 없을 때 쓰는 문구.
 *
 * 잠긴 버튼의 툴팁과 그 아래 안내가 **같은 사실을 말해야 한다.** 각자 문구를 들고 있으면
 * 하나는 "서버가 막았다", 다른 하나는 "확인하지 못했다"로 갈리고, 사용자는 무엇을 믿을지
 * 모르게 된다. 그래서 한 곳에 둔다.
 */

/** 서버가 사유를 주지 않고 비활성이라고만 답한 경우. */
export const PAYMENT_DISABLED_FALLBACK =
  '온라인 결제를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.'

/**
 * 물어보지 못한 경우. 서버가 막은 것과는 **다른 사실이다** — 사용자가 할 일이 다르다.
 * 어느 쪽이든 결제는 열지 않는다.
 */
export const PAYMENT_CHECK_FAILED_COPY =
  '결제 사용 여부를 확인하지 못했습니다. 확인될 때까지 결제를 시작하지 않습니다.'

/** 지금 상태에 맞는 문구 하나. */
export function paymentUnavailableCopy(reason: string | null, checkFailed: boolean): string {
  if (checkFailed) return PAYMENT_CHECK_FAILED_COPY
  return reason ?? PAYMENT_DISABLED_FALLBACK
}
