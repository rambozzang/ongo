import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreatorSubmitView from './CreatorSubmitView.vue'
import { ugcPublishingApi } from '@/api/ugcPublishing'
import { ugcSubmissionApi } from '@/api/ugcSubmission'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcSubmission', () => ({
  ugcSubmissionApi: { listMine: vi.fn(), saveDraft: vi.fn(), submit: vi.fn() },
}))
vi.mock('@/api/ugcPublishing', () => ({
  ugcPublishingApi: { myPosts: vi.fn(), registerExternal: vi.fn() },
}))
vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => ({ success: vi.fn(), error: vi.fn() }),
}))

describe('CreatorSubmitView', () => {
  it('shows an actionable error when external post loading fails', async () => {
    vi.mocked(ugcSubmissionApi.listMine).mockResolvedValue({
      items: [{
        id: 11,
        campaignId: 3,
        creatorId: 7,
        revision: 1,
        caption: '캡션',
        status: 'APPROVED',
        submittedAt: null,
        approvedAt: null,
        assets: [],
        createdAt: null,
        updatedAt: null,
      }],
      totalElements: 1,
      page: 0,
      size: 20,
    })
    vi.mocked(ugcPublishingApi.myPosts).mockRejectedValue(new Error('게시물 API 장애'))

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/creator/campaigns/:id/submit', component: CreatorSubmitView }],
    })
    await router.push('/creator/campaigns/3/submit')
    await router.isReady()

    const wrapper = mount(CreatorSubmitView, {
      global: {
        plugins: [router, createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })],
        stubs: { PageHeader: { template: '<header><slot name="title-suffix" /></header>' } },
      },
    })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('게시물 API 장애')
    expect(wrapper.get('[role="alert"] button').text()).toContain('다시 시도')
  })
})
