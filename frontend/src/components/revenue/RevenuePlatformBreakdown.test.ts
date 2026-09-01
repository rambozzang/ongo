import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import RevenuePlatformBreakdown from './RevenuePlatformBreakdown.vue'
import koMessages from '@/locales/ko/common.json'

/**
 * 수익이 없을 때 **측정된 0원처럼 보이지 않는지** 고정한다.
 *
 * 예전에는 `data = []` 여도 도넛과 가운데 **"₩0"** 을 그렸다. 그 화면은 "측정했더니 0원"
 * 으로 읽힌다 — 아직 아무것도 수집되지 않은 상태와 구분되지 않는다. 수익 화면에서
 * 그 차이는 크리에이터가 플랫폼을 계속 운영할지 판단하는 근거를 바꾼다.
 */
describe('RevenuePlatformBreakdown 빈 상태', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })

  const mountBreakdown = (data: { platform: string; revenue: number; percentage: number }[]) =>
    mount(RevenuePlatformBreakdown, {
      props: { data },
      global: { plugins: [i18n] },
    })

  it('데이터가 없으면 도넛 대신 빈 상태를 보여준다', () => {
    const wrapper = mountBreakdown([])

    expect(wrapper.find('[data-testid="platform-breakdown-empty"]').exists()).toBe(true)
    expect(wrapper.find('canvas').exists()).toBe(false)
    // 측정된 적 없는 값을 금액으로 쓰지 않는다.
    expect(wrapper.text()).not.toContain('₩0')
  })

  /**
   * 행은 있는데 합계가 0 이면 그릴 조각이 없다. 그대로 그리면 빈 링에 더해
   * 범례·툴팁 퍼센트가 `0/0` 이라 `NaN%` 가 된다.
   */
  it('합계가 0이면 도넛을 그리지 않는다', () => {
    const wrapper = mountBreakdown([{ platform: 'YOUTUBE', revenue: 0, percentage: 0 }])

    expect(wrapper.find('[data-testid="platform-breakdown-empty"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('수익이 있으면 도넛과 합계를 보여준다', () => {
    const wrapper = mountBreakdown([
      { platform: 'YOUTUBE', revenue: 700000, percentage: 70 },
      { platform: 'INSTAGRAM', revenue: 300000, percentage: 30 },
    ])

    expect(wrapper.find('[data-testid="platform-breakdown-empty"]').exists()).toBe(false)
    expect(wrapper.find('canvas').exists()).toBe(true)
    expect(wrapper.text()).toContain('100만')
  })
})
