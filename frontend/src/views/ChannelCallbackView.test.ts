import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ChannelCallbackView from './ChannelCallbackView.vue'
import apiClient from '@/api/client'
import koMessages from '@/locales/ko/common.json'

/*
 * 무료 플랜의 채널 한도(maxPlatforms=1)에 걸리는 실제 마찰 화면.
 *
 * 사용자는 OAuth 동의까지 마치고 이 화면으로 돌아온 뒤에야 거절당한다. 서버는
 * PlanLimitExceededException 을 400 + ResData.message 로 정확히 내려보내지만, 보강 전에는
 * 이 화면이 'Request failed with status code 400' 을 그대로 렌더했다. 결제를 설득할 수 있는
 * 유일한 순간에 사용자는 원인도 다음 행동도 알 수 없었다.
 *
 * 이 테스트는 channelApi 를 모킹하지 않는다 — 인터셉터부터 화면까지 실제 경로를 지나야
 * 보강이 사용자에게 닿는지 증명할 수 있다.
 */

const STATE_NONCE = 'nonce-1'

function rejectWith(status: number, data: unknown) {
  return async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    throw new AxiosError(
      `Request failed with status code ${status}`,
      'ERR_BAD_REQUEST',
      config,
      undefined,
      { data, status, statusText: 'Bad Request', headers: {}, config },
    )
  }
}

async function renderCallback() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/auth/channel-callback', component: ChannelCallbackView },
      { path: '/channels', component: { template: '<div />' } },
      // CTA 가 router-link 로 렌더되려면 대상 경로가 라우터에 있어야 한다.
      { path: '/subscription', component: { template: '<div />' } },
    ],
  })
  await router.push({
    path: '/auth/channel-callback',
    query: { code: 'auth-code', state: `YOUTUBE|/channels|${STATE_NONCE}` },
  })
  await router.isReady()

  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(ChannelCallbackView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return wrapper
}

/** code·state 가 없는 콜백. 서버를 부르지 않고 화면에서 바로 거절된다. */
async function renderInvalidCallback() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/auth/channel-callback', component: ChannelCallbackView },
      { path: '/channels', component: { template: '<div />' } },
      { path: '/subscription', component: { template: '<div />' } },
    ],
  })
  await router.push('/auth/channel-callback')
  await router.isReady()

  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(ChannelCallbackView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return wrapper
}

describe('ChannelCallbackView 플랜 한도 거절', () => {
  let originalAdapter: typeof apiClient.defaults.adapter

  beforeEach(() => {
    originalAdapter = apiClient.defaults.adapter
    sessionStorage.setItem('channel_oauth_state_nonce', STATE_NONCE)
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    sessionStorage.clear()
    localStorage.clear()
    vi.restoreAllMocks()
  })

  const planLimitBody = (reason: string) => ({
    success: false,
    message: reason,
    data: null,
    error: 'PLAN_LIMIT_EXCEEDED',
  })

  const cta = (wrapper: Awaited<ReturnType<typeof renderCallback>>) =>
    wrapper.findAll('a').find((a) => a.text().includes(koMessages.channelCallbackView.planLimitCta))

  it('한도 초과 사유를 한국어 그대로 사용자에게 보여준다', async () => {
    const reason = '연동 플랫폼 한도를 초과했습니다. 현재 플랜 한도: 1'
    apiClient.defaults.adapter = rejectWith(400, planLimitBody(reason))

    const wrapper = await renderCallback()

    expect(wrapper.text()).toContain(reason)
    // 이 문구가 보이면 사용자는 결제해야 할 이유를 끝내 알지 못한다.
    expect(wrapper.text()).not.toContain('Request failed with status code')
    expect(wrapper.text()).not.toContain(koMessages.channelCallbackView.connectError)
  })

  /*
   * OAuth 동의를 마치고 온 사용자에게 사유만 보여주고 끝내면 다음 행동을 알 수 없다.
   * CTA 는 안정 코드 PLAN_LIMIT_EXCEEDED 일 때만 나와야 한다 — 일반 오류에까지 결제를
   * 권하면 사용자를 오도하고 신뢰를 잃는다.
   */
  it('PLAN_LIMIT_EXCEEDED 면 /subscription 으로 가는 CTA 를 보여준다', async () => {
    apiClient.defaults.adapter = rejectWith(400, planLimitBody('연동 플랫폼 한도를 초과했습니다. 현재 플랜 한도: 1'))

    const wrapper = await renderCallback()

    expect(wrapper.text()).toContain(koMessages.channelCallbackView.planLimitHint)
    const link = cta(wrapper)
    expect(link).toBeDefined()
    expect(link!.attributes('href')).toBe('/subscription')
    // 기존 돌아가기 경로는 그대로 남아야 한다.
    expect(wrapper.text()).toContain(koMessages.channelCallbackView.goBack)
  })

  it.each([
    ['기계 코드 BAD_REQUEST', { success: false, data: null, error: 'BAD_REQUEST' }],
    ['사람이 읽는 일반 채널 오류', { success: false, data: null, error: '이미 연결된 채널입니다' }],
    ['다른 안정 코드', { success: false, message: '크레딧이 부족합니다', data: null, error: 'CREDIT_INSUFFICIENT' }],
    ['본문 없음', { success: false, data: null }],
    // 사유 문구에 '한도'가 들어가도 업그레이드 대상 코드가 아니면 CTA 를 띄우면 안 된다.
    // 문구는 번역·수정될 수 있으므로 판별은 안정 코드로만 해야 한다는 계약을 고정한다.
    // (크레딧은 플랜이 아니라 별도 구매로 푸는 문제라 목적지가 다르다.)
    [
      '한도라는 말이 들어간 비대상 코드',
      { success: false, message: '크레딧 한도를 초과했습니다', data: null, error: 'CREDIT_INSUFFICIENT' },
    ],
    /*
     * 저장 공간 한도도 업그레이드로 풀리지만 **이 화면의 대상은 아니다.**
     * 여기 안내는 '채널을 더 연결하려면…' 이라 저장 공간 사유에 붙으면 사유와 안내가 어긋난다.
     * 공통 유틸을 넓게 허용하도록 되돌리면 이 케이스가 깨져야 한다.
     */
    [
      '저장 공간 한도(다른 화면의 대상)',
      { success: false, message: '저장 공간 한도를 초과했습니다', data: null, error: 'STORAGE_QUOTA_EXCEEDED' },
    ],
  ])('%s 에는 CTA 를 보여주지 않는다', async (_label, body) => {
    apiClient.defaults.adapter = rejectWith(400, body)

    const wrapper = await renderCallback()

    expect(wrapper.text()).not.toContain(koMessages.channelCallbackView.planLimitHint)
    expect(cta(wrapper)).toBeUndefined()
    expect(wrapper.find('a[href="/subscription"]').exists()).toBe(false)
  })

  it('401 에는 CTA 를 보여주지 않는다', async () => {
    apiClient.defaults.adapter = rejectWith(401, { success: false, data: null, error: 'PLAN_LIMIT_EXCEEDED' })

    const wrapper = await renderCallback()

    // 401 은 client.ts 가 보강하지 않고, 여기서도 결제를 권할 자리가 아니다.
    expect(cta(wrapper)).toBeUndefined()
  })

  it('응답 없는 네트워크 오류에는 CTA 를 보여주지 않는다', async () => {
    apiClient.defaults.adapter = async (config) => {
      throw new AxiosError('Network Error', 'ERR_NETWORK', config)
    }

    const wrapper = await renderCallback()

    expect(cta(wrapper)).toBeUndefined()
    expect(wrapper.text()).toContain('Network Error')
  })

  it('잘못된 콜백(코드·state 누락)에는 CTA 를 보여주지 않는다', async () => {
    const wrapper = await renderInvalidCallback()

    expect(wrapper.text()).toContain(koMessages.channelCallbackView.invalidCallback)
    expect(cta(wrapper)).toBeUndefined()
  })

  it('사유가 없는 실패에는 기존 안내 문구를 유지한다', async () => {
    apiClient.defaults.adapter = rejectWith(400, { success: false, data: null, error: 'BAD_REQUEST' })

    const wrapper = await renderCallback()

    /*
     * 보강할 사유가 없으면 기존 메시지가 그대로 남는다.
     *
     * 이 화면의 폴백(`error.message` 가 빈 문자열일 때만 안내 문구)은 AxiosError 가 항상
     * message 를 갖기 때문에 발동하지 않는다. 즉 서버가 사유를 주지 않는 오류에서는 여전히
     * 영문 전송 문구가 보인다 — 이번 변경 범위(client.ts)가 아니라 뷰의 폴백 조건 문제라
     * 손대지 않고, 현재 동작을 있는 그대로 고정해 둔다.
     */
    expect(wrapper.text()).toContain('Request failed with status code 400')
  })
})
