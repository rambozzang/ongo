import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from './LoginView.vue'
import { authApi } from '@/api/auth'
import { PLANS } from '@/types/subscription'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/auth', () => ({
  authApi: {
    authorizationUrl: vi.fn(),
    login: vi.fn(),
    devLogin: vi.fn(),
  },
}))

const stub = { template: '<div />' }

async function renderLogin() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: stub },
      { path: '/terms', component: stub },
      { path: '/privacy', component: stub },
    ],
  })
  await router.push('/login')
  await router.isReady()

  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(LoginView, {
    global: { plugins: [pinia, router, i18n], stubs: { OnGoLogo: true } },
  })
  await flushPromises()
  return wrapper
}

const plan = (type: string) => PLANS.find((p) => p.type === type)!

/**
 * 로그인 화면은 비로그인 방문자가 도달하는 **유일한** 제품 설명 표면이다.
 * `/` 는 requiresAuth 라 라우터가 여기로 보내고, 가격을 보여주는 다른 화면은 전부 인증 뒤에 있다.
 * 그래서 가치 제안·가격·CTA 세 가지가 이 화면에서 동시에 성립해야 한다.
 */
describe('LoginView 공개 전환 표면', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('가격을 PLANS 상수 그대로 보여준다', async () => {
    const wrapper = await renderLogin()
    const pricing = wrapper.get(`section[aria-label="${koMessages.subscription.planComparison}"]`)
    const text = pricing.text()

    // 하드코딩이 아니라 상수에서 온 값인지 확인한다. PLANS 가 바뀌면 이 테스트가 같이 움직인다.
    expect(plan('STARTER').price).toBe(9900)
    expect(plan('PRO').price).toBe(19900)
    expect(text).toContain('9,900')
    expect(text).toContain('19,900')
    // 0원은 금액이 아니라 "무료"로 읽혀야 한다.
    expect(text).toContain(koMessages.subscription.statusFree)
    expect(text).not.toContain('₩0')
  })

  it('Free/Starter/Pro 세 가지와 핵심 차이를 보여준다', async () => {
    const wrapper = await renderLogin()
    const cards = wrapper.findAll(`section[aria-label="${koMessages.subscription.planComparison}"] li`)

    expect(cards).toHaveLength(3)
    expect(cards.map((c) => c.text())).toEqual([
      expect.stringContaining('Free'),
      expect.stringContaining('Starter'),
      expect.stringContaining('Pro'),
    ])
    // 무엇이 다른지가 없으면 가격만 있는 표는 판단 근거가 되지 못한다.
    const first = cards[0].text()
    expect(first).toContain(koMessages.subscription.connectedChannels)
    expect(first).toContain(koMessages.subscription.monthlyUploads)
    expect(cards[1].text()).toContain(String(plan('STARTER').maxUploadsPerMonth))
    expect(cards[2].text()).toContain(String(plan('PRO').maxUploadsPerMonth))
    // Business 는 로그인 화면 대상이 아니다.
    expect(wrapper.text()).not.toContain('Business')
  })

  it('로그인 CTA가 요금 안내보다 위에 있어 초기 화면에서 밀리지 않는다', async () => {
    const wrapper = await renderLogin()
    const html = wrapper.html()
    const googleAt = html.indexOf(koMessages.loginView.continueWithGoogle)
    const kakaoAt = html.indexOf(koMessages.loginView.continueWithKakao)
    const pricingAt = html.indexOf(koMessages.subscription.planComparison)

    expect(googleAt).toBeGreaterThan(-1)
    expect(kakaoAt).toBeGreaterThan(-1)
    expect(pricingAt).toBeGreaterThan(-1)
    expect(googleAt).toBeLessThan(pricingAt)
    expect(kakaoAt).toBeLessThan(pricingAt)
  })

  /*
   * jsdom 은 CSS 를 적용하지 않아 두 블록이 모두 DOM 에 존재한다.
   * 따라서 "모바일에서 보이는가"는 렌더 여부가 아니라 **가시성 클래스 계약**으로 검증한다.
   * 이 계약이 깨지면 한쪽 브레이크포인트에서 가치 제안이 통째로 사라진다.
   */
  it('데스크톱과 모바일 각각에 가치 제안이 존재한다', async () => {
    const wrapper = await renderLogin()

    const hero = wrapper.find('.tablet\\:flex')
    expect(hero.exists()).toBe(true)
    expect(hero.classes()).toContain('hidden')
    expect(hero.text()).toContain(koMessages.loginView.heroTitle.replace(/\n/g, ' ').split(' ')[0])

    const mobileBlock = wrapper.find('.tablet\\:hidden')
    expect(mobileBlock.exists()).toBe(true)
    expect(mobileBlock.text()).toContain(koMessages.app.description)
    // 모바일에는 히어로가 없으므로 부제까지 여기서 말해야 한다.
    expect(mobileBlock.text()).toContain(koMessages.loginView.heroSubtitle.split('\n')[0])
  })

  it('요금 안내는 어느 브레이크포인트에서도 숨겨지지 않는다', async () => {
    const wrapper = await renderLogin()
    const pricing = wrapper.get(`section[aria-label="${koMessages.subscription.planComparison}"]`)

    expect(pricing.classes()).not.toContain('hidden')
    expect(pricing.classes().some((c) => c.includes('tablet:hidden'))).toBe(false)
  })

  it('기존 OAuth 흐름과 법적 링크를 보존한다', async () => {
    vi.mocked(authApi.authorizationUrl).mockResolvedValue({
      authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth?client_id=x',
      state: 'state-token',
    } as never)

    const wrapper = await renderLogin()
    expect(wrapper.find('a[href="/terms"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/privacy"]').exists()).toBe(true)

    const google = wrapper
      .findAll('button')
      .find((b) => b.text().includes(koMessages.loginView.continueWithGoogle))!
    await google.trigger('click')
    await flushPromises()

    expect(authApi.authorizationUrl).toHaveBeenCalledOnce()
    expect(vi.mocked(authApi.authorizationUrl).mock.calls[0][0]).toBe('google')
    // state 는 콜백에서 대조해야 하므로 세션에 남아야 한다.
    expect(sessionStorage.getItem('oauth_state:google')).toBe('state-token')
  })
})
