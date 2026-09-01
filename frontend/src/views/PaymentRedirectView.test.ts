import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import PaymentRedirectView from './PaymentRedirectView.vue'
import { portoneApi } from '@/api/portone'
import koMessages from '@/locales/ko/common.json'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * 모바일 결제 복귀 화면의 계약.
 *
 * **쿼리만 보고 성공으로 처리하면 안 된다.** 이 URL 은 사용자가 직접 열 수 있고 값도
 * 바꿀 수 있다. `/payment-redirect?paymentId=아무거나` 로 구독이 열리면 결제 없이 유료
 * 기능을 쓰게 된다. 성공 여부는 서버 `complete`/`reconcile` 응답만이 정한다.
 */
vi.mock('@/api/portone', () => ({
  portoneApi: {
    complete: vi.fn(),
    reconcile: vi.fn(),
  },
}))

/*
 * 결제 확정 뒤 화면이 프로필을 다시 읽는지 관측하기 위한 것이다. 실제 인증 API 는 이
 * 화면의 검증 대상이 아니므로 호출만 기록한다.
 */
vi.mock('@/api/auth', () => ({
  authApi: {
    getProfile: vi.fn().mockResolvedValue({ id: 1, email: 'a@b.c', planType: 'PRO' }),
  },
}))

async function renderRedirect(
  query: Record<string, string | string[]>,
  options: { failProfileRefresh?: boolean } = {},
) {
  // 이 화면은 결제 확정 뒤 authStore 로 프로필을 다시 읽는다.
  localStorage.setItem('accessToken', 'session-token')
  setActivePinia(createPinia())
  /*
   * 실패는 **스토어 수준**에서 주입한다. `authApi.getProfile` 만 거부시키면
   * `fetchProfile` 이 그 예외를 내부에서 삼켜 정상 반환하므로, 화면이 실패를 어떻게
   * 다루는지 전혀 확인하지 못한다.
   */
  if (options.failProfileRefresh) {
    vi.spyOn(useAuthStore(), 'fetchProfile').mockRejectedValue(new Error('profile down'))
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/payment-redirect', name: 'payment-redirect', component: PaymentRedirectView },
      { path: '/subscription', name: 'subscription', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })

  await router.push({ path: '/payment-redirect', query })
  await router.isReady()

  const wrapper = mount(PaymentRedirectView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return { wrapper, router }
}

const succeeded = (wrapper: { find: (s: string) => { exists: () => boolean } }) =>
  wrapper.find('[data-testid="payment-redirect-success"]').exists()

describe('PaymentRedirectView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('성공 복귀', () => {
    it('code 가 없으면 서버 complete 를 거친 뒤에만 성공으로 본다', async () => {
      vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'PAID' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'pay_1' })

      expect(portoneApi.complete).toHaveBeenCalledExactlyOnceWith('pay_1')
      expect(succeeded(wrapper)).toBe(true)
    })

    it('완료 후 구독 화면으로 돌아간다', async () => {
      vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'PAID' } as never)

      const { wrapper, router } = await renderRedirect({ paymentId: 'pay_1' })
      await wrapper.get('button').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/subscription')
    })
  })

  describe('검증 실패', () => {
    /** 서버가 확정하지 못했으면 성공이 아니다. */
    it('complete 가 실패하면 성공으로 처리하지 않는다', async () => {
      vi.mocked(portoneApi.complete).mockRejectedValue(new Error('server error'))

      const { wrapper } = await renderRedirect({ paymentId: 'pay_1' })

      expect(succeeded(wrapper)).toBe(false)
      expect(wrapper.get('[data-testid="payment-redirect-error"]').text())
        .toContain('결제 상태를 확인하지 못했습니다')
    })
  })

  describe('실패 복귀', () => {
    it('code 가 있으면 reconcile 로 실제 상태를 확인한다', async () => {
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'FAILED' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'pay_1', code: 'PAY_PROCESS_CANCELED' })

      expect(portoneApi.reconcile).toHaveBeenCalledExactlyOnceWith('pay_1')
      // 실패 응답에 complete 를 부르면 안 된다.
      expect(portoneApi.complete).not.toHaveBeenCalled()
      expect(succeeded(wrapper)).toBe(false)
      expect(wrapper.get('[data-testid="payment-redirect-error"]').text()).toContain('취소되었거나 실패')
    })

    /** PG 는 실패를 알렸지만 승인만 나고 응답이 끊긴 경우가 있다. */
    it('실패 코드여도 재조회가 완료면 성공으로 본다', async () => {
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'COMPLETED' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'pay_1', code: 'FAILURE_TYPE_PG' })

      expect(succeeded(wrapper)).toBe(true)
    })

    it('재조회가 확정 전이면 대기로 안내한다', async () => {
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'PENDING' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'pay_1', code: 'FAILURE_TYPE_PG' })

      expect(succeeded(wrapper)).toBe(false)
      const text = wrapper.get('[data-testid="payment-redirect-error"]').text()
      expect(text).toContain('확정되지 않았습니다')
      expect(text).toContain('결제 내역을 확인')
    })

    /** PG 가 준 message 를 그대로 보여주지 않는다 — 신뢰할 수 없는 외부 문자열이다. */
    it('쿼리의 message 를 화면에 그대로 노출하지 않는다', async () => {
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'FAILED' } as never)

      const { wrapper } = await renderRedirect({
        paymentId: 'pay_1',
        code: 'X',
        message: '<img src=x onerror=alert(1)>',
      })

      expect(wrapper.text()).not.toContain('onerror')
    })
  })

  describe('비정상 query', () => {
    it('paymentId 가 없으면 서버를 부르지 않고 오류를 보여준다', async () => {
      const { wrapper } = await renderRedirect({ code: 'PAY_PROCESS_CANCELED' })

      expect(portoneApi.complete).not.toHaveBeenCalled()
      expect(portoneApi.reconcile).not.toHaveBeenCalled()
      expect(succeeded(wrapper)).toBe(false)
      expect(wrapper.get('[data-testid="payment-redirect-error"]').text())
        .toContain('결제 정보를 확인할 수 없습니다')
    })

    it('빈 paymentId 도 결제 완료로 취급하지 않는다', async () => {
      const { wrapper } = await renderRedirect({ paymentId: '' })

      expect(portoneApi.complete).not.toHaveBeenCalled()
      expect(succeeded(wrapper)).toBe(false)
    })

    /** 같은 키가 반복되면 Vue Router 가 배열을 준다. 문자열 메서드를 부르면 터진다. */
    it('배열로 조작된 paymentId 를 받지 않는다', async () => {
      const { wrapper } = await renderRedirect({ paymentId: ['pay_1', 'pay_2'] })

      expect(portoneApi.complete).not.toHaveBeenCalled()
      expect(portoneApi.reconcile).not.toHaveBeenCalled()
      expect(succeeded(wrapper)).toBe(false)
    })

    it('배열로 조작된 code 는 실패 경로로 해석하지 않는다', async () => {
      vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'PAID' } as never)

      await renderRedirect({ paymentId: 'pay_1', code: ['a', 'b'] })

      // code 를 인식하지 못했으니 성공 경로(complete)를 탄다. reconcile 로 새지 않는다.
      expect(portoneApi.complete).toHaveBeenCalledExactlyOnceWith('pay_1')
      expect(portoneApi.reconcile).not.toHaveBeenCalled()
    })
  })

  /* ── 결제 확정 후 세션 반영 ─────────────────────────────────────── */

  /*
   * 서버는 결제 완료 시 구독과 `users.plan_type` 을 함께 올린다. 그런데 이 화면은 결제
   * **전에** 로드된 세션을 들고 있어, 다시 읽지 않으면 방금 결제한 사용자가 세션 내내
   * 상단바에서 옛 플랜을 본다 — 권한은 열렸는데 안 열린 것처럼 보인다.
   */
  describe('결제 확정 후 세션 반영', () => {
    it('완료가 확정되면 프로필을 다시 읽는다', async () => {
      vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'PAID' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'ongo-1' })

      expect(succeeded(wrapper)).toBe(true)
      expect(vi.mocked(authApi.getProfile)).toHaveBeenCalledTimes(1)
    })

    it('실패 코드여도 재조회가 완료면 프로필을 다시 읽는다', async () => {
      vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'COMPLETED' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'ongo-1', code: 'FAILURE' })

      expect(succeeded(wrapper)).toBe(true)
      expect(vi.mocked(authApi.getProfile)).toHaveBeenCalledTimes(1)
    })

    /** **확정되지 않은 결제로 세션을 건드리지 않는다.** 열리지 않은 권한을 읽어올 이유가 없다. */
    it.each([
      ['검증 실패', { paymentId: 'ongo-1' }, () => vi.mocked(portoneApi.complete).mockRejectedValue(new Error('x'))],
      ['확정 실패', { paymentId: 'ongo-1', code: 'FAILURE' }, () => vi.mocked(portoneApi.reconcile).mockResolvedValue({ id: 1, status: 'FAILED' } as never)],
      ['식별자 없음', {}, () => {}],
    ])('%s 면 프로필을 다시 읽지 않는다', async (_label, query, arrange) => {
      arrange()

      const { wrapper } = await renderRedirect(query as Record<string, string>)

      expect(succeeded(wrapper)).toBe(false)
      expect(vi.mocked(authApi.getProfile)).not.toHaveBeenCalled()
    })

    /**
     * 프로필 재조회는 **결제 성공의 후속 작업**이다. 실패해도 되돌릴 결제가 아니므로
     * 성공 표시를 취소하면 안 된다 — 돈은 빠졌는데 화면은 실패라고 말하게 된다.
     */
    /**
     * 프로필 재조회는 **결제 성공의 후속 작업**이다. 실패해도 되돌릴 결제가 아니므로
     * 성공 표시를 취소하면 안 된다 — 돈은 빠졌는데 화면은 실패라고 말하게 된다.
     *
     * 그래서 성공 표시는 재조회 **앞에서** 확정한다. 뒤로 옮기면 갱신 실패가 그대로
     * 결제 실패 화면이 된다.
     */
    it('프로필 재조회가 실패해도 성공 표시를 유지한다', async () => {
      vi.mocked(portoneApi.complete).mockResolvedValue({ id: 1, status: 'PAID' } as never)

      const { wrapper } = await renderRedirect({ paymentId: 'ongo-1' }, { failProfileRefresh: true })

      expect(succeeded(wrapper)).toBe(true)
      expect(wrapper.find('[data-testid="payment-redirect-error"]').exists()).toBe(false)
    })
  })
})
