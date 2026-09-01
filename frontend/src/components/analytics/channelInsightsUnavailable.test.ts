import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TrafficSourceChart from './TrafficSourceChart.vue'
import DemographicsChart from './DemographicsChart.vue'
import type { DemographicsResponse, TrafficSourceResponse } from '@/types/analytics'

/**
 * 트래픽 소스·인구통계 차트가 **미지원 상태를 0 데이터로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버는 수집 경로가 없어도 **성공 응답**을 준다.
 *
 * ```
 * { "period": "30d", "sources": {}, "total": 0 }
 * ```
 *
 * 예전 차트는 `data` 가 `null` 일 때만 "데이터가 없습니다" 를 그렸다. 위 응답은 `null`
 * 이 아니므로 **빈 도넛/빈 막대가 그려졌고**, 화면상으로는 "유입 0 건" / "그런 시청자가
 * 없었다" 는 측정 결과와 구분되지 않았다.
 *
 * 실제로는 `channel_insights_daily` 를 채우는 `upsertChannelInsights` 의 호출부가 하나도
 * 없고, 어댑터 응답에도 그 필드가 없다. 즉 **연동되지 않은 기능**이다. 서버가 이제
 * `available=false` 와 사유를 함께 주므로 화면은 그것을 그대로 보여준다.
 */
describe('채널 인사이트 미지원 표시', () => {
  const reason = '트래픽 소스·시청자 인구통계는 현재 플랫폼 분석 연동에서 수집하지 않아 표시할 수 없습니다.'

  // ── 트래픽 소스 ─────────────────────────────────────────────────────────

  const trafficUnavailable: TrafficSourceResponse = {
    period: '30d',
    sources: {},
    total: 0,
    available: false,
    unavailableReason: reason,
  }

  /** **이 케이스가 빈 도넛을 "유입 0 건" 으로 보여주던 자리다.** */
  it('트래픽: 미지원이면 차트 대신 사유를 보여준다', () => {
    const wrapper = mount(TrafficSourceChart, { props: { data: trafficUnavailable } })

    expect(wrapper.text()).toContain(reason)
    // 차트를 그리면 안 된다 — 빈 도넛은 0 건으로 읽힌다.
    expect(wrapper.findComponent({ name: 'DoughnutChart' }).exists()).toBe(false)
    expect(wrapper.html()).not.toContain('<canvas')
  })

  /** "데이터 없음" 과 "지원 안 함" 은 사용자가 할 일이 다르다. */
  it('트래픽: 미지원 문구가 단순 데이터 없음과 다르다', () => {
    const wrapper = mount(TrafficSourceChart, { props: { data: trafficUnavailable } })

    expect(wrapper.text()).not.toContain('데이터가 없습니다')
  })

  it('트래픽: 응답 자체가 없으면 기존 문구를 유지한다', () => {
    const wrapper = mount(TrafficSourceChart, { props: { data: null } })

    expect(wrapper.text()).toContain('데이터가 없습니다')
  })

  /**
   * `available` 을 모르는 옛 응답은 판단할 수 없다. 분포가 비었으면 그릴 것이
   * 없으므로 빈 차트 대신 기존 문구를 쓴다.
   */
  it('트래픽: available 없는 옛 응답의 빈 분포는 차트를 그리지 않는다', () => {
    const wrapper = mount(TrafficSourceChart, {
      props: { data: { period: '30d', sources: {}, total: 0 } },
    })

    expect(wrapper.html()).not.toContain('<canvas')
    expect(wrapper.text()).toContain('데이터가 없습니다')
  })

  /** **측정된 값은 그대로 그린다.** 과도한 차단 회귀를 막는다. */
  it('트래픽: 수집된 분포는 차트로 그린다', () => {
    const wrapper = mount(TrafficSourceChart, {
      props: {
        data: { period: '30d', sources: { SEARCH: 120 }, total: 120, available: true, unavailableReason: null },
      },
    })

    expect(wrapper.html()).toContain('<canvas')
    expect(wrapper.text()).not.toContain(reason)
    expect(wrapper.text()).not.toContain('데이터가 없습니다')
  })

  // ── 인구통계 ────────────────────────────────────────────────────────────

  const demographicsUnavailable: DemographicsResponse = {
    period: '30d',
    ageDistribution: {},
    genderDistribution: {},
    topCountries: {},
    available: false,
    unavailableReason: reason,
  }

  /** **이 케이스가 빈 막대·빈 도넛을 "시청자 0 명" 으로 보여주던 자리다.** */
  it('인구통계: 미지원이면 차트 대신 사유를 보여준다', () => {
    const wrapper = mount(DemographicsChart, { props: { data: demographicsUnavailable } })

    expect(wrapper.text()).toContain(reason)
    expect(wrapper.html()).not.toContain('<canvas')
    // 3단 그리드(연령대/성별/상위 국가)를 그리면 빈 칸이 관측처럼 보인다.
    expect(wrapper.text()).not.toContain('연령대')
    expect(wrapper.text()).not.toContain('상위 국가')
  })

  it('인구통계: 응답 자체가 없으면 기존 문구를 유지한다', () => {
    const wrapper = mount(DemographicsChart, { props: { data: null } })

    expect(wrapper.text()).toContain('데이터가 없습니다')
  })

  it('인구통계: available 없는 옛 응답의 빈 분포는 차트를 그리지 않는다', () => {
    const wrapper = mount(DemographicsChart, {
      props: {
        data: { period: '30d', ageDistribution: {}, genderDistribution: {}, topCountries: {} },
      },
    })

    expect(wrapper.html()).not.toContain('<canvas')
  })

  /** **측정된 값은 그대로 그린다.** */
  it('인구통계: 수집된 분포는 차트로 그린다', () => {
    const wrapper = mount(DemographicsChart, {
      props: {
        data: {
          period: '30d',
          ageDistribution: { '25-34': 40 },
          genderDistribution: { male: 60 },
          topCountries: { KR: 900 },
          available: true,
          unavailableReason: null,
        },
      },
    })

    expect(wrapper.html()).toContain('<canvas')
    expect(wrapper.text()).toContain('연령대')
    expect(wrapper.text()).toContain('KR')
    expect(wrapper.text()).not.toContain(reason)
  })
})
