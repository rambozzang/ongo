import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ShortsPipelineView from './ShortsPipelineView.vue'
import { ugcShortsPipelineApi } from '@/api/ugcShortsPipeline'
import { ugcShortsTemplateApi } from '@/api/ugcShortsTemplate'
import { videoApi } from '@/api/video'
import { useWorkspaceStore } from '@/stores/workspace'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcShortsPipeline', () => ({ ugcShortsPipelineApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('@/api/ugcShortsTemplate', () => ({ ugcShortsTemplateApi: { list: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { list: vi.fn() } }))
vi.mock('@/api/workspace', () => ({ workspaceApi: { list: vi.fn() } }))

const run = (overrides: Record<string, unknown> = {}) => ({
  id: 17,
  sourceVideoId: 4,
  sourceVideoTitle: '롱폼 원본 영상',
  templateId: null,
  status: 'PARTIALLY_COMPLETED',
  currentStage: 'SCHEDULE',
  clipCount: 3,
  errorMessage: '한 클립은 확인이 필요합니다',
  createdAt: '2026-08-09T10:20:00Z',
  updatedAt: '2026-08-09T10:20:00Z',
  ...overrides,
})

async function renderPipeline() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.workspaces = [{ id: 2, ownerId: 1, name: '내 작업공간', slug: 'mine', description: null, logoUrl: null, memberCount: 1, createdAt: null }]
  workspace.activeWorkspaceId = 2
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/ugc/shorts/runs', component: { template: '<div />' } },
      { path: '/ugc/shorts/runs/:id', component: { template: '<div />' } },
      { path: '/ugc/shorts/prompts', component: { template: '<div />' } },
      { path: '/ugc/shorts/templates', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/ugc/shorts/runs')
  await router.isReady()
  const wrapper = mount(ShortsPipelineView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PageHeader: { template: '<header><h1>{{ title }}</h1><slot name="actions" /></header>', props: ['title'] },
        BaseModal: { template: '<div v-if="modelValue" role="dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
        EmptyState: true,
        LoadingSpinner: true,
        ChatBubbleLeftRightIcon: true,
        ChevronRightIcon: true,
        FilmIcon: true,
        PlusIcon: true,
        Square2StackIcon: true,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('ShortsPipelineView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ugcShortsPipelineApi.list).mockResolvedValue({ content: [run()], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false } as never)
    vi.mocked(ugcShortsTemplateApi.list).mockResolvedValue([{ id: 5, name: '세로 기본', description: null, aspectRatio: '9:16', width: 1080, height: 1920, backgroundStyle: 'BLACK_BARS', hookFontFamily: null, hookFontSize: null, hookFontColor: null, hookStrokeColor: null, hookPosition: 'TOP', captionFontFamily: null, captionFontSize: null, captionFontColor: null, captionStrokeColor: null, captionPosition: 'BOTTOM', safeAreaTop: 0, safeAreaBottom: 0, referenceImageUrl: null, isDefault: true, createdAt: null, updatedAt: null }] as never)
    vi.mocked(videoApi.list).mockResolvedValue({ content: [{ id: 4, title: '롱폼 원본 영상', mediaType: 'VIDEO' }, { id: 5, title: '이미지', mediaType: 'IMAGE' }] } as never)
  })

  it('renders a partial result with its error and opens its detail route', async () => {
    const { wrapper, router } = await renderPipeline()
    expect(wrapper.text()).toContain('롱폼 원본 영상')
    expect(wrapper.text()).toContain('한 클립은 확인이 필요합니다')
    await wrapper.find('button.card').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/ugc/shorts/runs/17')
  })

  it('creates a pipeline only after selecting a real video and routes to the new run', async () => {
    vi.mocked(ugcShortsPipelineApi.create).mockResolvedValue(run({ id: 18, status: 'RUNNING' }) as never)
    const { wrapper, router } = await renderPipeline()
    const newRun = wrapper.findAll('button').find((button) => button.text().includes('새 실행'))
    expect(newRun).toBeDefined()
    await newRun!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('롱폼 원본 영상')
    await wrapper.get('#shorts-run-video').setValue('4')
    const submit = wrapper.findAll('button').find((button) => button.text() === '실행 시작')
    expect(submit).toBeDefined()
    await submit!.trigger('click')
    await flushPromises()
    expect(ugcShortsPipelineApi.create).toHaveBeenCalledWith(2, { sourceVideoId: 4, templateId: null })
    expect(router.currentRoute.value.fullPath).toBe('/ugc/shorts/runs/18')
  })
})
