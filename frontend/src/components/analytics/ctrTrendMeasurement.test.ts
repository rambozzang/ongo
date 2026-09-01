import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CTRTrendChart from './CTRTrendChart.vue'
import type { CTRResponse } from '@/types/analytics'

/**
 * CTR 차트가 **측정 없이 숫자를 보여주지 않는지** 고정한다.
 *
 * 노출을 조회하는 어댑터는 YouTube 하나뿐이라, 다른 플랫폼만 쓰는 크리에이터에게는
 * 분모가 없다. 예전에는 서버가 `avgCTR = 0.0`, `totalImpressions = 0` 을 내려줬고
 * 화면은 그것을 **"평균 CTR 0% · 총 노출 0"** 으로 보라색 강조까지 해서 보여줬다.
 *
 * 서버는 이제 측정 가능한 행이 없으면 `null` 과 빈 `data` 를 준다. 화면이 그것을 0 으로
 * 되돌리거나 Chart.js 에 빈 배열을 넘겨 축만 있는 그래프를 그리면 수정이 무의미해진다.
 */
describe('CTR 추세 측정 불가 표시', () => {
  function response(overrides: Partial<CTRResponse> = {}): CTRResponse {
    return {
      period: '30d',
      avgCTR: null,
      totalImpressions: null,
      data: [],
      measuredPlatforms: [],
      unavailableReason: '노출 수가 수집되지 않아 클릭률을 계산할 수 없습니다',
      ...overrides,
    }
  }

  function chart(data: CTRResponse | null) {
    return mount(CTRTrendChart, { props: { data } })
  }

  it('측정되지 않으면 평균 CTR과 총 노출을 측정 불가로 보여준다', () => {
    const wrapper = chart(response())

    expect(wrapper.find('[data-testid="ctr-avg-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ctr-impressions-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ctr-avg"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('0%')
  })

  /** Chart.js 에 빈 배열을 넘기면 축만 있는 빈 그래프가 "값이 0" 처럼 보인다. */
  it('측정 포인트가 없으면 차트를 그리지 않고 사유를 보여준다', () => {
    const wrapper = chart(response())

    expect(wrapper.find('canvas').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ctr-empty"]').text()).toContain('노출 수가 수집되지 않아')
  })

  it('사유가 없어도 빈 상태 문구를 보여준다', () => {
    const wrapper = chart(response({ unavailableReason: null }))

    expect(wrapper.find('[data-testid="ctr-empty"]').exists()).toBe(true)
  })

  it('응답 자체가 없으면 헤더 숫자를 만들지 않는다', () => {
    const wrapper = chart(null)

    expect(wrapper.find('[data-testid="ctr-avg"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ctr-impressions"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('0%')
  })

  // ── 측정된 값은 그대로 ───────────────────────────────────────────────────

  it('측정된 값은 기존대로 숫자와 차트를 보여준다', () => {
    const wrapper = chart(response({
      avgCTR: 25.0,
      totalImpressions: 1000,
      data: [{ date: '2026-08-10', impressions: 1000, views: 250, ctr: 25.0 }],
      measuredPlatforms: ['YOUTUBE'],
      unavailableReason: null,
    }))

    expect(wrapper.find('[data-testid="ctr-avg"]').text()).toContain('25')
    expect(wrapper.find('[data-testid="ctr-impressions"]').text()).toContain('1,000')
    expect(wrapper.find('[data-testid="ctr-empty"]').exists()).toBe(false)
  })

  /** **측정된 0% 는 관측 결과다.** 측정 불가로 감추면 실제 관찰을 잃는다. */
  it('측정된 0%는 숫자로 보여준다', () => {
    const wrapper = chart(response({
      avgCTR: 0,
      totalImpressions: 5000,
      data: [{ date: '2026-08-10', impressions: 5000, views: 0, ctr: 0 }],
      unavailableReason: null,
    }))

    expect(wrapper.find('[data-testid="ctr-avg"]').text()).toContain('0%')
    expect(wrapper.find('[data-testid="ctr-avg-unavailable"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ctr-impressions"]').text()).toContain('5,000')
  })
})
