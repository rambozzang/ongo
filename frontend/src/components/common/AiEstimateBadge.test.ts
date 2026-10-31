import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import AiEstimateBadge from './AiEstimateBadge.vue'
import koMessages from '@/locales/ko/common.json'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
const mountBadge = (props: Record<string, unknown>) => mount(AiEstimateBadge, { props, global: { plugins: [i18n] } })

describe('AiEstimateBadge', () => {
  it('배지만 보이다가 누르면 근거를 펼친다', async () => {
    const wrapper = mountBadge({ basis: '검색 데이터 없이 추정' })

    expect(wrapper.get('[data-testid="ai-estimate-badge"]').text()).toBe('AI 추정')
    expect(wrapper.find('[data-testid="ai-estimate-basis"]').exists()).toBe(false)
    await wrapper.get('[data-testid="ai-estimate-badge"]').trigger('click')
    expect(wrapper.get('[data-testid="ai-estimate-basis"]').text()).toBe('근거: 검색 데이터 없이 추정')
  })

  it('showBasis 면 처음부터 근거가 보이고, 라벨을 바꿀 수 있다', () => {
    const wrapper = mountBadge({ basis: '근거 문장', showBasis: true, label: 'AI 제안' })

    expect(wrapper.get('[data-testid="ai-estimate-badge"]').text()).toBe('AI 제안')
    expect(wrapper.get('[data-testid="ai-estimate-basis"]').text()).toContain('근거 문장')
  })

  it('근거가 없으면 펼칠 것이 없다 — 배지는 그래도 보인다', async () => {
    const wrapper = mountBadge({ basis: '  ' })

    const badge = wrapper.get('[data-testid="ai-estimate-badge"]')
    expect(badge.attributes('disabled')).toBeDefined()
    await badge.trigger('click')
    expect(wrapper.find('[data-testid="ai-estimate-basis"]').exists()).toBe(false)
  })
})
