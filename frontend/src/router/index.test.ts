import { describe, expect, it } from 'vitest'
import router from './index'

describe('application navigation contract', () => {
  it('resolves every server-enabled menu path to a concrete route', () => {
    const menuPaths = [
      '/today', '/compose', '/videos', '/ai', '/templates', '/brandkit', '/assets',
      '/calendar-v2', '/automation', '/channels-v2',
      '/performance', '/revenue', '/ab-tests', '/competitors', '/inbox-v2', '/audience', '/channel-audit',
      '/brand-deals', '/linkbio', '/ugc/campaigns', '/creator/campaigns',
      '/ugc/shorts/prompts', '/ugc/shorts/templates', '/ugc/shorts/runs', '/team',
      '/manual', '/subscription', '/settings-v2', '/admin',
    ]

    for (const path of menuPaths) {
      expect(router.resolve(path).matched.length, path).toBeGreaterThan(0)
    }
    for (const removedPath of ['/ideas', '/keyword-research', '/trends']) {
      expect(router.getRoutes().some((route) => route.path === removedPath)).toBe(false)
      const resolved = router.resolve(removedPath)
      expect(resolved.matched[resolved.matched.length - 1]?.redirect).toBe('/today')
    }
  })

  it('keeps required public legal and support documents directly reachable', () => {
    for (const path of ['/terms', '/privacy', '/refund', '/data-deletion', '/support']) {
      const resolved = router.resolve(path)
      expect(resolved.matched.length, path).toBeGreaterThan(0)
      expect(resolved.meta.requiresAuth, path).toBe(false)
      expect(resolved.meta.allowAuthenticated, path).toBe(true)
    }
  })

  /**
   * 모바일 결제는 PG 에서 이 경로로 돌아온다. 라우트가 없으면 404 를 만나고, 승인된
   * 결제를 확정할 방법이 사라진다 — 돈은 빠졌는데 PENDING 만 남는다.
   */
  describe('모바일 결제 복귀 경로', () => {
    it('/payment-redirect 가 라우트로 존재한다', () => {
      const resolved = router.resolve('/payment-redirect')

      expect(resolved.matched.length).toBeGreaterThan(0)
      expect(resolved.name).toBe('payment-redirect')
    })

    /**
     * 결제 확정은 서버가 사용자 토큰으로 검증한다. 공개로 열면 남의 결제를 확정하려는
     * 시도가 가능해진다. 세션이 끊겼다면 가드가 fullPath 를 보존해 로그인 뒤 되돌린다.
     */
    it('인증을 요구한다', () => {
      expect(router.resolve('/payment-redirect').meta.requiresAuth).not.toBe(false)
    })

    /** 쿼리를 잃으면 어떤 결제인지 알 수 없다. fullPath 에 paymentId 가 남아야 한다. */
    it('로그인 리다이렉트에 쓰이는 fullPath 가 paymentId 를 보존한다', () => {
      const resolved = router.resolve('/payment-redirect?paymentId=pay_1&code=X')

      expect(resolved.fullPath).toContain('paymentId=pay_1')
    })

    /**
     * capability 게이트에 걸리면 안 된다. 구독 기능이 꺼져 있어도 이미 승인된 결제는
     * 확정해야 한다.
     */
    it('capability 게이트 경로와 겹치지 않는다', async () => {
      const { requiredCapabilityForPath } = await import('./capability')

      expect(requiredCapabilityForPath('/payment-redirect')).toBeFalsy()
      // 게이트가 걸리는 경로와 대조 — 이 단언이 무의미하지 않다는 근거.
      expect(requiredCapabilityForPath('/subscription')).toBe('subscription')
    })
  })

  /**
   * 빌링키 복귀 라우트는 **존재하면 안 된다.**
   *
   * 리디렉션은 결과를 쿼리로 돌려주고 빌링키 발급의 결과값은 billingKey 다. 브라우저가
   * 그 URL 로 이동하는 순간 요청 라인이 우리 Nginx·CDN 액세스 로그에 평문으로 남는다.
   * 페이지에서 지워도 이미 늦다. 받을 경로 자체를 두지 않는다.
   */
  it('빌링키를 쿼리로 받는 복귀 라우트를 두지 않는다', () => {
    expect(router.getRoutes().some((route) => route.name === 'billing-key-redirect')).toBe(false)
    expect(router.getRoutes().some((route) => route.path === '/billing-key-redirect')).toBe(false)
    // catch-all 이 있어 matched 는 비지 않는다. 전용 라우트가 아님을 이름으로 확인한다.
    expect(router.resolve('/billing-key-redirect').name).not.toBe('billing-key-redirect')
  })
})
