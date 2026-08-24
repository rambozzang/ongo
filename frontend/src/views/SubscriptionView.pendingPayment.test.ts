import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import ko from '@/locales/ko/common.json'
import en from '@/locales/en/common.json'

/**
 * 미확정(PENDING) 결제를 화면이 **정직하게** 다루는지 고정한다.
 *
 * 체크아웃은 결제창을 열기 **전에** PENDING 결제 행을 만든다. 사용자가 결제를 취소하거나
 * 브라우저를 닫으면 그 행이 그대로 남고, 서버에는 "취소함"과 "웹훅 대기 중"을 구분할
 * 근거가 없다. 그래서 화면은 어느 쪽으로도 단정하면 안 된다.
 */
describe('subscription pending payment copy', () => {
  // JSON 은 리터럴 타입으로 추론되므로 캐스팅 없이 그대로 읽는다.
  const koNotice: string = ko.subscription.paymentPendingNotice
  const enNotice: string = en.subscription.paymentPendingNotice

  /** 성공·실패를 단정하는 표현이 들어가면 안 된다. */
  it('never claims the payment succeeded or failed', () => {
    for (const notice of [koNotice, enNotice]) {
      expect(notice).not.toContain('완료되었')
      expect(notice).not.toContain('실패했')
      expect(notice).not.toContain('취소되었')
      expect(notice.toLowerCase()).not.toContain('succeeded')
      expect(notice.toLowerCase()).not.toContain('failed to')
    }
  })

  /**
   * **이 화면에는 폴링도 SSE 도 없다.** "자동으로 바뀐다"고 쓰면 사용자는 화면을 열어둔
   * 채 기다리게 되는데 영원히 바뀌지 않는다. 서버 상태는 갱신될 수 있어도 사용자는
   * 새로고침하거나 다시 방문해야 본다 — 문구가 그 사실을 말해야 한다.
   */
  it('does not promise the screen updates by itself', () => {
    expect(koNotice).not.toContain('자동으로 바뀝니다')
    expect(enNotice.toLowerCase()).not.toContain('updates automatically')

    expect(koNotice).toContain('자동으로 갱신되지 않')
    expect(koNotice).toContain('새로고침')
    expect(koNotice).toContain('다시 방문')

    expect(enNotice.toLowerCase()).toContain('does not update on its own')
    expect(enNotice.toLowerCase()).toContain('refresh')
    expect(enNotice.toLowerCase()).toContain('visit again')
  })

  /** 재결제로 인한 중복 결제를 막는 안내가 있어야 한다. */
  it('tells the user to check before paying again', () => {
    expect(koNotice).toContain('다시 결제하기 전에')
    expect(enNotice.toLowerCase()).toContain('before paying again')
  })

  it('labels the pending state neutrally in both locales', () => {
    expect(ko.subscription.paymentPending).toBe('확인 중')
    expect(en.subscription.paymentPending).toBe('Awaiting confirmation')
  })
})

/**
 * 뷰가 PENDING 을 실제로 다루는지 소스로 확인한다. 이 뷰에는 테스트 하네스가 없고
 * 스토어 의존이 많아 마운트 비용이 크지만, 막아야 할 회귀는 "PENDING 매핑이 사라져
 * 영문 원문이 그대로 노출되는 것"이라 소스 존재 여부로 충분히 고정된다.
 */
describe('SubscriptionView pending payment handling', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/views/SubscriptionView.vue'), 'utf-8')

  it('maps PENDING to a translated label instead of the raw enum name', () => {
    expect(source).toContain("PENDING: 'subscription.paymentPending'")
  })

  it('reads the date field the server actually sends', () => {
    // 서버 DTO 는 createdAt 이다. paidAt 은 존재하지 않아 모든 행이 Invalid Date 였다.
    expect(source).toContain('formatDateTime(payment.createdAt)')
    expect(source).not.toContain('payment.paidAt')
  })

  it('offers a refresh that only re-reads, never mutates payment state', () => {
    expect(source).toContain('refreshPayments')
    expect(source).toContain('subscriptionStore.fetchPayments(paymentList.value?.page ?? 0, 20)')
  })
})
