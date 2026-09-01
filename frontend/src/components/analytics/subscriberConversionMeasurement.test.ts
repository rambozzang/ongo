import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SubscriberConversionChart from './SubscriberConversionChart.vue'
import type { SubscriberConversionResponse } from '@/types/analytics'

/**
 * 구독 전환 차트가 **측정 없이 성과를 보여주지 않는지** 고정한다.
 *
 * 구독 증가를 조회하는 어댑터는 YouTube 하나뿐이라, 다른 플랫폼만 쓰는 크리에이터에게는
 * 분자가 없다. 예전에는 서버가 `totalGained = 0` 을 내려줬고 화면은 그것을
 * **`총 신규 구독: +0`** 으로 초록색(`text-success-strong`) 강조까지 해서 보여줬다 —
 * 재지 않았는데 성과 색이다.
 *
 * 서버는 이제 유효한 행이 없으면 `null` 과 빈 `data` 를 준다. 화면이 그것을 0 으로
 * 되돌리거나 Chart.js 에 빈 배열을 넘기면 수정이 무의미해진다.
 */
describe('구독 전환 측정 불가 표시', () => {
  function response(overrides: Partial<SubscriberConversionResponse> = {}): SubscriberConversionResponse {
    return {
      period: '30d',
      totalGained: null,
      data: [],
      measuredPlatforms: [],
      unavailableReason: '구독 증가 수가 수집되지 않아 전환율을 계산할 수 없습니다',
      ...overrides,
    }
  }

  function chart(data: SubscriberConversionResponse | null) {
    return mount(SubscriberConversionChart, { props: { data } })
  }

  it('측정되지 않으면 총 신규 구독을 측정 불가로 보여준다', () => {
    const wrapper = chart(response())

    expect(wrapper.find('[data-testid="subscriber-total-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="subscriber-total"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('+0')
  })

  /** 초록색 강조는 그 자체로 성과 주장이다. 미측정에는 붙으면 안 된다. */
  it('측정되지 않으면 성과 색을 쓰지 않는다', () => {
    const wrapper = chart(response())

    expect(wrapper.find('[data-testid="subscriber-total-unavailable"]').classes()).not.toContain('text-success-strong')
  })

  /** Chart.js 에 빈 배열을 넘기면 축만 있는 빈 그래프가 "값이 0" 처럼 보인다. */
  it('측정 포인트가 없으면 차트를 그리지 않고 사유를 보여준다', () => {
    const wrapper = chart(response())

    expect(wrapper.find('canvas').exists()).toBe(false)
    expect(wrapper.find('[data-testid="subscriber-empty"]').text()).toContain('구독 증가 수가 수집되지 않아')
  })

  it('사유가 없어도 빈 상태 문구를 보여준다', () => {
    const wrapper = chart(response({ unavailableReason: null }))

    expect(wrapper.find('[data-testid="subscriber-empty"]').exists()).toBe(true)
  })

  it('응답 자체가 없으면 헤더 숫자를 만들지 않는다', () => {
    const wrapper = chart(null)

    expect(wrapper.find('[data-testid="subscriber-total"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="subscriber-total-unavailable"]').exists()).toBe(false)
  })

  // ── 측정된 값은 그대로 ───────────────────────────────────────────────────

  it('측정된 값은 기존대로 숫자와 차트를 보여준다', () => {
    const wrapper = chart(response({
      totalGained: 50,
      data: [{ date: '2026-08-10', gained: 50, views: 1000, conversionRate: 5 }],
      measuredPlatforms: ['YOUTUBE'],
      unavailableReason: null,
    }))

    expect(wrapper.find('[data-testid="subscriber-total"]').text()).toContain('50')
    expect(wrapper.find('[data-testid="subscriber-total"]').classes()).toContain('text-success-strong')
    expect(wrapper.find('[data-testid="subscriber-empty"]').exists()).toBe(false)
  })

  /** **측정된 0 은 관측 결과다.** 측정 불가로 감추면 실제 관찰을 잃는다. */
  it('측정된 0은 숫자로 보여준다', () => {
    const wrapper = chart(response({
      totalGained: 0,
      data: [{ date: '2026-08-10', gained: 0, views: 5000, conversionRate: 0 }],
      unavailableReason: null,
    }))

    expect(wrapper.find('[data-testid="subscriber-total"]').text()).toContain('+0')
    expect(wrapper.find('[data-testid="subscriber-total-unavailable"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="subscriber-empty"]').exists()).toBe(false)
  })
})
