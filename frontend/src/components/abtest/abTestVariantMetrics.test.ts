import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import apiClient from '@/api/client'
import { abTestApi } from '@/api/abtest'
import AbTestResultChart from './AbTestResultChart.vue'
import VariantCompare from './VariantCompare.vue'
import koMessages from '@/locales/ko/common.json'
import type { AbTestVariant } from '@/types/abtest'

/**
 * A/B 변형 지표가 **측정 없이 숫자로 보이지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 백엔드 `ABTestVariantResponse` 는 도메인 기본값 `views=0, clicks=0` 을 그대로 내려보냈고,
 * 프론트 매퍼는 `ctr: variant.views > 0 ? ... : 0` 으로 한 번 더 0 을 만들었다.
 * 결과 차트는 그것을 **"0.0%" · "노출 0" · "클릭 0"** 으로 정상 측정값처럼 그렸다.
 *
 * 이 값들을 채우는 경로가 코드 어디에도 없다 — onGo 는 썸네일을 직접 서빙하지 않으므로
 * 노출·클릭을 관측할 수단 자체가 없다. 즉 **모든 변형이 항상 "0.0% 성과"** 였다.
 *
 * `null` 은 "0" 이 아니라 "재지 않았다" 다. 막대도 그리지 않는다 — 0 폭 막대는
 * "가장 낮은 성과" 로 읽힌다.
 */
describe('A/B 변형 지표 측정 불가 계약', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })

  function variant(overrides: Partial<AbTestVariant> = {}): AbTestVariant {
    return {
      id: '1', label: 'A', value: '변형 A',
      impressions: null, clicks: null, ctr: null, views: null,
      metricsUnavailableReason: '노출이 수집되지 않아 이 변형의 성과를 측정할 수 없습니다',
      isWinner: false,
      ...overrides,
    }
  }

  // ── API 매퍼 ─────────────────────────────────────────────────────────────

  describe('매퍼', () => {
    const get = vi.spyOn(apiClient, 'get')

    function givenVariants(variants: unknown[]) {
      get.mockResolvedValue({
        data: {
          success: true,
          data: {
            tests: [{
              id: 1, videoId: 10, testName: 't', status: 'RUNNING', metricType: 'THUMBNAIL',
              winnerVariantId: null, variants, createdAt: '2026-08-01T00:00:00',
            }],
          },
        },
      } as never)
    }

    beforeEach(() => vi.clearAllMocks())

    /** **매퍼가 null 을 0 으로 바꾸면 화면은 되돌릴 방법이 없다.** */
    it('서버가 null을 주면 지표를 0으로 만들지 않는다', async () => {
      givenVariants([{
        id: 1, variantName: 'A', title: null, description: null, thumbnailUrl: null,
        views: null, clicks: null, engagementRate: null,
        metricsUnavailableReason: '노출이 수집되지 않아 이 변형의 성과를 측정할 수 없습니다',
      }])

      const mapped = (await abTestApi.getTests())[0].variants[0]

      expect(mapped.impressions).toBeNull()
      expect(mapped.clicks).toBeNull()
      expect(mapped.ctr).toBeNull()
      expect(mapped.metricsUnavailableReason).toBeTruthy()
    })

    it('측정된 값은 CTR을 계산해 보존한다', async () => {
      givenVariants([{
        id: 1, variantName: 'A', title: null, description: null, thumbnailUrl: null,
        views: 1000, clicks: 37, engagementRate: 0, metricsUnavailableReason: null,
      }])

      const mapped = (await abTestApi.getTests())[0].variants[0]

      expect(mapped.impressions).toBe(1000)
      expect(mapped.clicks).toBe(37)
      expect(mapped.ctr).toBeCloseTo(3.7)
    })

    /** 노출이 있으면 클릭 0 은 측정된 사실이다. CTR 0% 도 마찬가지다. */
    it('노출이 있고 클릭이 0이면 CTR 0을 계산한다', async () => {
      givenVariants([{
        id: 1, variantName: 'A', title: null, description: null, thumbnailUrl: null,
        views: 1000, clicks: 0, engagementRate: 0, metricsUnavailableReason: null,
      }])

      expect((await abTestApi.getTests())[0].variants[0].ctr).toBe(0)
    })

    /** 측정된 변형이 하나도 없으면 총 노출도 0 이 아니다. */
    it('측정된 변형이 없으면 총 노출은 null이다', async () => {
      givenVariants([{
        id: 1, variantName: 'A', title: null, description: null, thumbnailUrl: null,
        views: null, clicks: null, engagementRate: null, metricsUnavailableReason: 'x',
      }])

      expect((await abTestApi.getTests())[0].totalImpressions).toBeNull()
    })
  })

  // ── 결과 차트 ─────────────────────────────────────────────────────────────

  describe('결과 차트', () => {
    function chart(variants: AbTestVariant[]) {
      return mount(AbTestResultChart, { props: { variants }, global: { plugins: [i18n] } })
    }

    it('미측정 변형은 CTR 숫자 대신 측정 불가를 보여준다', () => {
      const wrapper = chart([variant()])

      expect(wrapper.find('[data-testid="ab-ctr-A-unavailable"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="ab-ctr-A"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('0.0%')
      expect(wrapper.text()).not.toContain('0.00%')
      expect(wrapper.text()).not.toContain('NaN')
    })

    /** 0 폭 막대는 "가장 낮은 성과" 로 읽힌다. 아예 그리지 않는다. */
    it('미측정 변형은 막대와 노출·클릭 숫자를 그리지 않는다', () => {
      const wrapper = chart([variant()])

      expect(wrapper.find('[data-testid="ab-metrics-A-unavailable"]').exists()).toBe(true)
      // 노출/클릭/CTR 이 들어가던 행 자체가 렌더링되지 않는다.
      // (사유 문장에도 "노출" 이 들어가므로 텍스트 검색이 아니라 요소로 확인한다.)
      expect(wrapper.find('[data-testid="ab-metrics-row-A"]').exists()).toBe(false)
    })

    it('측정된 변형은 기존대로 숫자와 막대를 그린다', () => {
      const wrapper = chart([variant({ impressions: 1000, clicks: 37, ctr: 3.7, views: 1000, metricsUnavailableReason: undefined })])

      expect(wrapper.find('[data-testid="ab-ctr-A"]').text()).toContain('3.7%')
      expect(wrapper.find('[data-testid="ab-ctr-A-unavailable"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('1,000')
    })

    /** 섞여 있어도 측정된 쪽 막대 기준이 미측정 때문에 왜곡되면 안 된다. */
    it('측정과 미측정이 섞여도 각각 맞게 그린다', () => {
      const wrapper = chart([
        variant({ id: '1', label: 'A', impressions: 1000, clicks: 50, ctr: 5, views: 1000, metricsUnavailableReason: undefined }),
        variant({ id: '2', label: 'B' }),
      ])

      expect(wrapper.find('[data-testid="ab-ctr-A"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="ab-ctr-B-unavailable"]').exists()).toBe(true)
    })
  })

  // ── 변형 비교 ─────────────────────────────────────────────────────────────

  describe('변형 비교', () => {
    function compare(variants: AbTestVariant[]) {
      return mount(VariantCompare, {
        props: { variants, type: 'THUMBNAIL' as const },
        global: { plugins: [i18n] },
      })
    }

    it('미측정 변형은 노출·클릭·CTR 모두 측정 불가로 보여준다', () => {
      const wrapper = compare([variant()])

      expect(wrapper.find('[data-testid="vc-impressions-A-unavailable"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="vc-clicks-A-unavailable"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="vc-ctr-A-unavailable"]').exists()).toBe(true)
      expect(wrapper.text()).not.toContain('NaN')
      expect(wrapper.text()).not.toContain('0.00%')
    })

    it('측정된 변형은 기존대로 숫자를 보여준다', () => {
      const wrapper = compare([
        variant({ impressions: 1000, clicks: 37, ctr: 3.7, views: 1000, metricsUnavailableReason: undefined }),
      ])

      expect(wrapper.find('[data-testid="vc-impressions-A"]').text()).toContain('1,000')
      expect(wrapper.find('[data-testid="vc-ctr-A"]').text()).toContain('3.70%')
    })
  })

  it('ko/en 두 로케일에 측정 불가 문구가 있다', async () => {
    const en = await import('@/locales/en/common.json')

    expect(koMessages.abTest.metricUnavailable).toBeTruthy()
    expect((en.default as { abTest: { metricUnavailable?: string } }).abTest.metricUnavailable).toBeTruthy()
  })
})
