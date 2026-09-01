import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { EyeIcon } from '@heroicons/vue/24/outline'
import SummaryCard from './SummaryCard.vue'
import koMessages from '@/locales/ko/common.json'

/**
 * 증감이 **비교 불가**일 때 숫자를 만들어내지 않는지 고정한다.
 *
 * 서버는 이전 기간 데이터가 없으면 `viewsChangePercent = null` 을 준다. 예전 가드는
 * `!== undefined` 만 검사해서 `null` 이 통과했고, `Math.abs(null) === 0` 이라
 * 화면에 **"↑0%"** 라는 측정된 적 없는 값이 떴다. 0% 는 "변화 없음"이라는 사실을
 * 주장하는 숫자다 — 비교 자체가 불가능한 상황과 구분되지 않는다.
 */
describe('SummaryCard 증감 표시', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })

  const mountCard = (change: number | null | undefined) =>
    mount(SummaryCard, {
      // `icon` 은 필수 prop 이다. 빼면 마운트마다 Vue 경고가 쌓여 진짜 경고를 가린다.
      props: { title: '총 조회수', value: 50000, change, changeType: 'percent', icon: EyeIcon } as never,
      global: { plugins: [i18n] },
    })

  it('null이면 증감을 표시하지 않는다', () => {
    const wrapper = mountCard(null)

    expect(wrapper.text()).not.toContain('%')
    expect(wrapper.text()).not.toContain('0%')
    expect(wrapper.text()).not.toContain('null')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('undefined면 증감을 표시하지 않는다', () => {
    const wrapper = mountCard(undefined)

    expect(wrapper.text()).not.toContain('%')
  })

  /** aria-label 은 눈에 보이지 않아 회귀를 놓치기 쉽다. 여기도 같이 고정한다. */
  it('null이면 aria-label에도 증감을 넣지 않는다', () => {
    const label = mountCard(null).attributes('aria-label') ?? ''

    expect(label).not.toContain('전주 대비')
    expect(label).not.toContain('null')
    expect(label).not.toContain('NaN')
  })

  it('실제로 0%인 변화는 그대로 보여준다 — 비교 불가와 다르다', () => {
    const wrapper = mountCard(0)

    expect(wrapper.text()).toContain('0%')
  })

  it('측정된 증감은 부호와 함께 보여준다', () => {
    expect(mountCard(12.5).text()).toContain('12.5%')
    expect(mountCard(-30).text()).toContain('30%')
  })
})
