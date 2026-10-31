import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ChannelAuditView from './ChannelAuditView.vue'
import { channelAuditApi } from '@/api/channelAudit'
import type { ChannelAuditReport } from '@/types/channelAudit'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/channelAudit', () => ({
  channelAuditApi: { generateAudit: vi.fn(), getAudits: vi.fn(), getAuditDetail: vi.fn() },
}))
vi.mock('@/api/credit', () => ({
  creditApi: { getBalance: vi.fn(), getTransactions: vi.fn(), getPackages: vi.fn().mockResolvedValue([]), list: vi.fn(), purchase: vi.fn() },
}))

const base = {
  strengths: ['강점'],
  weaknesses: [],
  actionItems: [],
  outlierVideos: [],
  growthForecast: '전망 문장',
  forecastEstimate: { estimated: true, basis: '최근 30일 실측만 보고 AI 가 쓴 전망' },
  createdAt: '2026-11-01T00:00:00',
}

/** 새 보고서: AI 점수 없음, 실측 있음(댓글은 수집 안 함). */
const fresh: ChannelAuditReport = {
  ...base,
  id: 2,
  overallScore: null,
  overallScoreEstimate: null,
  measured: { periodDays: 30, totalViews: 1200, totalViewsChange: 12.5, totalLikes: 80, totalComments: null, subscriberCount: 50, analyzedVideoCount: 4 },
}

/** 예전 보고서: AI 점수만 있음. */
const legacy: ChannelAuditReport = {
  ...base,
  id: 1,
  overallScore: 72,
  overallScoreEstimate: { estimated: true, basis: '예전 보고서에서 AI 가 매긴 점수입니다.' },
  measured: null,
}

async function render(reports: ChannelAuditReport[]) {
  vi.mocked(channelAuditApi.getAudits).mockResolvedValue({ audits: reports, totalCount: reports.length } as never)
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(ChannelAuditView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: { BaseModal: { template: '<div v-if="modelValue" role="dialog"><slot /></div>', props: ['modelValue'] } },
    },
  })
  await flushPromises()
  return wrapper
}

describe('ChannelAuditView — 지어낸 점수 대신 실측', () => {
  beforeEach(() => vi.clearAllMocks())

  it('새 보고서 목록은 AI 점수 대신 실측 조회수를 보인다', async () => {
    const wrapper = await render([fresh])

    expect(wrapper.get('[data-testid="audit-list-views"]').text()).toBe('1,200')
    expect(wrapper.text()).not.toContain('/ 100')
  })

  it('예전 보고서의 AI 점수는 AI 추정 배지와 함께만 보인다', async () => {
    const wrapper = await render([legacy])

    expect(wrapper.text()).toContain('72')
    expect(wrapper.get('[data-testid="ai-estimate-badge"]').text()).toBe('AI 추정')
  })

  it('상세: 실측 지표를 보이고 수집하지 않은 값은 0 이 아니라 "측정 안 됨", 전망에는 추정 배지', async () => {
    const wrapper = await render([fresh])

    await wrapper.find('.card.cursor-pointer').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="audit-measured-views"]').text()).toBe('1,200')
    expect(wrapper.get('[data-testid="audit-measured-viewsChange"]').text()).toBe('+12.5%')
    expect(wrapper.get('[data-testid="audit-measured-comments"]').text()).toBe('측정 안 됨')
    expect(wrapper.find('[data-testid="audit-legacy-score"]').exists()).toBe(false)
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.findAll('[data-testid="ai-estimate-badge"]').length).toBe(1) // 전망 하나
  })
})
