import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import type { TrendDataPoint } from '@/types/analytics'

/*
 * Chart.js 는 jsdom 캔버스에서 동작하지 않는다. 렌더링이 아니라 **차트에 넘기는 데이터**가
 * 검증 대상이므로, 넘겨받은 props 를 그대로 노출하는 컴포넌트로 바꿔 끼운다.
 */
vi.mock('vue-chartjs', () => ({
  Line: defineComponent({
    name: 'LineStub',
    props: {
      data: { type: Object, required: true },
      options: { type: Object, default: () => ({}) },
      plugins: { type: Array, default: () => [] },
    },
    template: '<div class="line-stub" />',
  }),
}))

import TrendChart from './TrendChart.vue'

/**
 * 조회수 트렌드가 **수집하지 않은 날을 0 으로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 문제였나
 *
 * 서버(`AnalyticsUseCase.getTrends`)는 그 날 조회수를 실제로 수집한 플랫폼만
 * `platformViews` 에 담는다. 키가 없다는 것은 **수집 행이 없었다**는 뜻이다.
 *
 * 화면이 그 자리를 `?? 0` 으로 채우면, 토큰이 만료됐거나 동기화가 실패해 하루치가 빈
 * 플랫폼의 선이 바닥까지 떨어진다. 크리에이터는 **"조회수가 0 으로 폭락했다"** 는
 * 일어나지 않은 사건을 보게 되고, 그 판단으로 콘텐츠 전략을 바꾼다.
 *
 * 이 차트는 모바일·데스크톱 대시보드 양쪽의 첫 화면에 있고, 13개 플랫폼 중 11개가
 * 조회수를 수집하므로 여러 시리즈가 동시에 그려지는 것이 정상 상태다.
 */
describe('조회수 트렌드 — 미수집과 실측 0 구분', () => {
  beforeEach(() => {
    // `TrendChart` 가 `useThemeStore` 를 통해 읽는다. jsdom 에는 없다.
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: () => ({
        matches: false,
        addEventListener: () => {},
        removeEventListener: () => {},
      }),
    })
  })

  /** Chart.js 는 jsdom 캔버스에서 동작하지 않는다. 넘겨받는 데이터만 확인한다. */
  const mountChart = (data: TrendDataPoint[]) => {
    setActivePinia(createPinia())
    return mount(TrendChart, { props: { data, period: '7d' } })
  }

  const datasetFor = (wrapper: ReturnType<typeof mountChart>, label: string) => {
    const chartData = wrapper.findComponent({ name: 'LineStub' }).props('data') as {
      datasets: Array<{ label: string; data: Array<number | null> }>
    }
    return chartData.datasets.find(d => d.label === label)
  }

  /**
   * **핵심 회귀.** 수집 행이 없는 날은 `null` 이어야 Chart.js 가 선을 끊는다.
   */
  it('수집 행이 없는 날은 0 이 아니라 null 로 남긴다', () => {
    const wrapper = mountChart([
      { date: '2026-08-01', totalViews: 2000, platformViews: { YOUTUBE: 1200, TIKTOK: 800 }, platformSubscribers: {} },
      // 이 날 TikTok 동기화가 실패해 행이 없다. 조회수가 0 이었다는 뜻이 아니다.
      { date: '2026-08-02', totalViews: 1500, platformViews: { YOUTUBE: 1500 }, platformSubscribers: {} },
      { date: '2026-08-03', totalViews: 2300, platformViews: { YOUTUBE: 1400, TIKTOK: 900 }, platformSubscribers: {} },
    ])

    const tiktok = datasetFor(wrapper, 'TikTok')

    expect(tiktok?.data).toEqual([800, null, 900])
    // 0 이 들어가면 "폭락" 으로 그려진다.
    expect(tiktok?.data).not.toContain(0)
  })

  /**
   * **반대 방향도 지켜야 한다.** 서버가 실제로 0 을 보냈다면 그것은 관측이므로 그려야 한다.
   * null 로 바꾸면 "그 날 조회수가 없었다" 는 사실이 화면에서 사라진다.
   */
  it('서버가 보낸 실측 0 은 그대로 그린다', () => {
    const wrapper = mountChart([
      { date: '2026-08-01', totalViews: 1200, platformViews: { YOUTUBE: 1200 }, platformSubscribers: {} },
      { date: '2026-08-02', totalViews: 0, platformViews: { YOUTUBE: 0 }, platformSubscribers: {} },
    ])

    const youtube = datasetFor(wrapper, 'YouTube')

    expect(youtube?.data).toEqual([1200, 0])
  })

  /** 수집한 플랫폼만 시리즈가 된다 — 없는 플랫폼의 빈 선을 만들지 않는다. */
  it('한 번도 수집되지 않은 플랫폼은 시리즈를 만들지 않는다', () => {
    const wrapper = mountChart([
      { date: '2026-08-01', totalViews: 1200, platformViews: { YOUTUBE: 1200 }, platformSubscribers: {} },
      { date: '2026-08-02', totalViews: 1500, platformViews: { YOUTUBE: 1500 }, platformSubscribers: {} },
    ])

    const chartData = wrapper.findComponent({ name: 'LineStub' }).props('data') as {
      datasets: Array<{ label: string }>
    }

    expect(chartData.datasets.map(d => d.label)).toEqual(['YouTube'])
  })

  /**
   * Naver Clip 은 분석 API 자체가 없어 서버가 모든 지표를 미수집으로 선언한다.
   * 과거 행이 남아 있어도 조회수 시리즈로 그리지 않는다.
   */
  it('Naver Clip 은 조회수 시리즈에서 제외한다', () => {
    const wrapper = mountChart([
      { date: '2026-08-01', totalViews: 1500, platformViews: { YOUTUBE: 1200, NAVER_CLIP: 300 }, platformSubscribers: {} },
    ])

    const chartData = wrapper.findComponent({ name: 'LineStub' }).props('data') as {
      datasets: Array<{ label: string }>
    }

    expect(chartData.datasets.map(d => d.label)).toEqual(['YouTube'])
  })
})
