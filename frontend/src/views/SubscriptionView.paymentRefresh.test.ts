import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * 결제가 확정된 뒤 화면이 **무엇을 다시 읽는지** 고정한다.
 *
 * ## 왜 프로필까지 읽어야 하나
 *
 * 서버는 결제 완료 시 구독과 `users.plan_type` 을 함께 올린다. 그런데 이 화면은 결제
 * **전에** 로드된 세션을 들고 있어 `authStore.user.planType` 이 아직 이전 플랜이다.
 * 구독 스토어만 갱신하면 이 화면은 맞아 보이지만 `TopBar` 는 세션 내내 옛 플랜을 보여준다 —
 * 방금 결제한 사용자에게 "반영이 안 됐다" 는 신호가 되고, 문의와 환불로 이어진다.
 *
 * 같은 이유로 `OnboardingView.handlePlanPaymentSuccess` 가 이미 `fetchProfile` 을 부른다.
 * 세 결제 진입점 중 두 곳에만 빠져 있었다.
 *
 * ## 왜 소스를 읽어 검사하나
 *
 * 이 화면은 마운트에 스토어 10곳 조회와 PortOne 초기화가 딸려 있어 컴포넌트 테스트 비용이
 * 크다. 이 저장소의 다른 `SubscriptionView.*.test.ts` 도 같은 이유로 소스·문구 계약을
 * 직접 읽어 고정한다.
 */
describe('결제 확정 후 화면 갱신 대상', () => {
  const source = readFileSync(
    resolve(__dirname, 'SubscriptionView.vue'),
    'utf-8',
  )

  /** `handlePaymentConfirm` 본문만 떼어 본다 — 다른 핸들러의 리페치와 섞이지 않게 한다. */
  const confirmBody = (() => {
    const start = source.indexOf('async function handlePaymentConfirm()')
    expect(start).toBeGreaterThan(-1)
    const next = source.indexOf('\nasync function ', start + 1)
    return source.slice(start, next === -1 ? undefined : next)
  })()

  /** **핵심 회귀.** 이 한 줄이 빠지면 결제한 사용자가 세션 내내 옛 플랜으로 보인다. */
  it('결제 확정 뒤 프로필을 다시 읽는다', () => {
    expect(confirmBody).toContain('authStore.fetchProfile()')
  })

  /** 기존 갱신 대상도 함께 남아 있어야 한다 — 하나만 남기고 지우는 회귀를 막는다. */
  it('구독·크레딧·결제내역도 함께 다시 읽는다', () => {
    expect(confirmBody).toContain('subscriptionStore.fetchSubscription()')
    expect(confirmBody).toContain('creditStore.fetchBalance()')
    expect(confirmBody).toContain('subscriptionStore.fetchPayments(')
  })

  /** 갱신은 결제 성공의 후속 작업이다. 실패가 결제 결과를 뒤집지 않아야 한다. */
  it('갱신 실패가 결제 성공 안내를 취소하지 않는다', () => {
    const successNotice = confirmBody.indexOf("notification.success(t('subscription.upgradeSuccess'))")
    const refetch = confirmBody.indexOf('authStore.fetchProfile()')

    expect(successNotice).toBeGreaterThan(-1)
    // 성공 안내가 갱신보다 앞에 있어야 갱신 실패로 안내가 사라지지 않는다.
    expect(successNotice).toBeLessThan(refetch)
  })

  /** 세 결제 진입점이 같은 계약을 쓰는지 — 온보딩 경로가 이 규약의 출처다. */
  it('온보딩 결제 경로도 같은 갱신을 한다', () => {
    const onboarding = readFileSync(resolve(__dirname, 'OnboardingView.vue'), 'utf-8')
    expect(onboarding).toContain('authStore.fetchProfile()')
  })
})
