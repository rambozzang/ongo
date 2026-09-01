import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import RevenueTable from './RevenueTable.vue'
import type { RevenueData } from '@/stores/revenue'

const data = (items: Array<Pick<RevenueData, 'period' | 'total' | 'platforms'>>): RevenueData[] => items.map((item) => item)

function mountTable(items: Array<Pick<RevenueData, 'period' | 'total' | 'platforms'>>) {
  return mount(RevenueTable, {
    props: { data: data(items) },
    global: {
      plugins: [createI18n({
        legacy: false,
        locale: 'ko',
        messages: {
          ko: { revenue: { table: { period: '기간', total: '합계', change: '변화' } } },
        },
      })],
    },
  })
}

describe('RevenueTable 성장률 측정 상태', () => {
  it('직전 수익이 0이면 Infinity 대신 비교 불가를 표시한다', () => {
    const wrapper = mountTable([
      { period: '2026-06', platforms: { YOUTUBE: 0 }, total: 0 },
      { period: '2026-07', platforms: { YOUTUBE: 1000 }, total: 1000 },
    ])

    expect(wrapper.text()).toContain('-')
    expect(wrapper.text()).not.toContain('Infinity')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('비교 가능한 수익은 실제 증감률을 표시한다', () => {
    const wrapper = mountTable([
      { period: '2026-06', platforms: { YOUTUBE: 1000 }, total: 1000 },
      { period: '2026-07', platforms: { YOUTUBE: 1250 }, total: 1250 },
    ])

    expect(wrapper.text()).toContain('+25.0%')
  })
})
