import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import CreatorCampaignsView from './CreatorCampaignsView.vue'
import { ugcParticipationApi } from '@/api/ugcParticipation'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcParticipation', () => ({ ugcParticipationApi: { myApplications: vi.fn() } }))

async function renderCampaigns() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/creator/campaigns', component: { template: '<div />' } }, { path: '/creator/campaigns/:id/submit', component: { template: '<div />' } }] })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/creator/campaigns')
  await router.isReady()
  const wrapper = mount(CreatorCampaignsView, {
    global: {
      plugins: [createPinia(), router, i18n],
      stubs: { PageHeader: { template: '<header><h1>{{ title }}</h1></header>', props: ['title'] } },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('CreatorCampaignsView', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders accepted and rejected campaign applications and opens submission', async () => {
    vi.mocked(ugcParticipationApi.myApplications).mockResolvedValue({
      items: [
        { application: { id: 1, campaignId: 9, creatorId: 3, message: null, portfolioUrl: null, status: 'ACCEPTED', decidedBy: null, decidedAt: null, createdAt: '2026-08-09T00:00:00Z' }, campaignName: '여름 캠페인', campaignStatus: 'ACTIVE', startAt: '2026-08-01T00:00:00Z', endAt: '2026-08-31T00:00:00Z' },
        { application: { id: 2, campaignId: 10, creatorId: 3, message: null, portfolioUrl: null, status: 'REJECTED', decidedBy: null, decidedAt: null, createdAt: null }, campaignName: '보류 캠페인', campaignStatus: 'CLOSED', startAt: null, endAt: null },
      ],
      totalElements: 2,
      page: 0,
      size: 20,
    } as never)
    const { wrapper, router } = await renderCampaigns()
    expect(wrapper.text()).toContain('여름 캠페인')
    expect(wrapper.text()).toContain('보류 캠페인')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/creator/campaigns/9/submit')
  })

  it('shows the honest empty state returned from the server', async () => {
    vi.mocked(ugcParticipationApi.myApplications).mockResolvedValue({ items: [], totalElements: 0, page: 0, size: 20 } as never)
    const { wrapper } = await renderCampaigns()
    expect(wrapper.text()).toContain('아직 지원한 캠페인이 없습니다')
  })
})
