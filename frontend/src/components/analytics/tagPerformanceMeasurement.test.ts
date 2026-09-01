import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import TagCloud from './TagCloud.vue'
import TagPerformanceTable from './TagPerformanceTable.vue'
import koMessages from '@/locales/ko/common.json'
import type { TagPerformance } from '@/types/analytics'

/**
 * 태그 성과 화면이 **미수집 지표를 0 으로 그리지 않는지** 고정한다.
 *
 * 태그는 여러 영상·여러 플랫폼에 걸쳐 있어 서버가 지표별로 `null` 을 줄 수 있다.
 * Tumblr 의 `views` 는 노트 총합, Pinterest 의 `likes` 는 저장(Save) 수라 합계에서
 * 빠지기 때문이다. 화면이 `?? 0` 을 하면 그 구분이 사라진다.
 */
describe('태그 성과 미측정 표시', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'ko', messages: { ko: koMessages } })

  function tag(overrides: Partial<TagPerformance> = {}): TagPerformance {
    return {
      tag: '브이로그',
      videoCount: 3,
      totalViews: 1_000,
      totalLikes: 100,
      avgViews: 333,
      avgEngagement: 10,
      trend: 'up',
      unavailableMetrics: [],
      ...overrides,
    }
  }

  const unmeasured = tag({
    totalViews: null,
    totalLikes: null,
    avgViews: null,
    avgEngagement: null,
    trend: null,
    unavailableMetrics: ['views', 'likes'],
  })

  // ── 표 ───────────────────────────────────────────────────────────────────

  /** **이 케이스가 노트 총합/저장 수를 0 으로 그리던 자리다.** */
  it('표가 미측정 지표를 0이 아니라 측정 불가로 보여준다', () => {
    const wrapper = mount(TagPerformanceTable, {
      props: { tags: [unmeasured] },
      global: { plugins: [i18n] },
    })

    const text = wrapper.text()
    expect(text).toContain(koMessages.analyticsView.notMeasured)
    expect(text).not.toContain('0.0%')
    // 영상 수는 실제 개수라 그대로 남는다.
    expect(text).toContain('3')
  })

  it('표가 측정된 값은 그대로 보여준다', () => {
    const wrapper = mount(TagPerformanceTable, {
      props: { tags: [tag()] },
      global: { plugins: [i18n] },
    })

    expect(wrapper.text()).toContain('10.0%')
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  /** **측정된 0 은 관측이다.** */
  it('표가 측정된 0% 참여율은 숫자로 보여준다', () => {
    const wrapper = mount(TagPerformanceTable, {
      props: { tags: [tag({ avgEngagement: 0, totalLikes: 0 })] },
      global: { plugins: [i18n] },
    })

    expect(wrapper.text()).toContain('0.0%')
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  // ── 태그 클라우드 ────────────────────────────────────────────────────────

  it('클라우드가 미측정 참여율을 측정 불가로 보여준다', () => {
    const wrapper = mount(TagCloud, {
      props: { tags: [unmeasured] },
      global: { plugins: [i18n] },
    })

    expect(wrapper.text()).toContain(koMessages.analyticsView.notMeasured)
  })

  /**
   * 백분위는 **측정된 값만** 놓고 내야 한다. `null` 을 0 으로 보면 미수집 태그가 하위
   * 25% 를 채워 실제 하위 태그가 중간으로 올라간다.
   */
  it('미측정 태그가 백분위 색 판정을 흔들지 않는다', () => {
    const measuredOnly = mount(TagCloud, {
      props: { tags: [tag({ tag: 'A', avgEngagement: 1 }), tag({ tag: 'B', avgEngagement: 9 })] },
      global: { plugins: [i18n] },
    })
    const withUnmeasured = mount(TagCloud, {
      props: {
        tags: [
          tag({ tag: 'A', avgEngagement: 1 }),
          tag({ tag: 'B', avgEngagement: 9 }),
          tag({ tag: 'C', avgEngagement: null }),
        ],
      },
      global: { plugins: [i18n] },
    })

    // A·B 의 색이 C 의 존재로 바뀌면 안 된다.
    const colorOf = (w: ReturnType<typeof mount>, label: string) =>
      w.findAll('button').find((b) => b.text().includes(label))?.attributes('style')

    expect(colorOf(withUnmeasured, 'A')).toBe(colorOf(measuredOnly, 'A'))
    expect(colorOf(withUnmeasured, 'B')).toBe(colorOf(measuredOnly, 'B'))
  })

  it('태그가 없으면 빈 상태를 보여준다', () => {
    const wrapper = mount(TagCloud, { props: { tags: [] }, global: { plugins: [i18n] } })

    expect(wrapper.text()).toContain('태그 데이터가 없습니다')
  })
})
