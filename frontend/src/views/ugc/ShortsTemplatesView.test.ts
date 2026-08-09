import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ShortsTemplatesView from './ShortsTemplatesView.vue'
import { ugcShortsTemplateApi } from '@/api/ugcShortsTemplate'
import { useWorkspaceStore } from '@/stores/workspace'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcShortsTemplate', () => ({
  ugcShortsTemplateApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    uploadReferenceImage: vi.fn(),
  },
}))

const template = (overrides: Record<string, unknown> = {}) => ({
  id: 5,
  name: '세로 기본',
  description: '쇼츠용 기본 템플릿',
  aspectRatio: '9:16',
  width: 1080,
  height: 1920,
  backgroundStyle: 'BLACK_BARS',
  hookFontFamily: 'Pretendard',
  hookFontSize: 52,
  hookFontColor: '#FFFFFF',
  hookStrokeColor: '#000000',
  hookPosition: 'TOP',
  captionFontFamily: 'Pretendard',
  captionFontSize: 36,
  captionFontColor: '#FFFFFF',
  captionStrokeColor: '#000000',
  captionPosition: 'BOTTOM',
  safeAreaTop: 80,
  safeAreaBottom: 160,
  referenceImageUrl: null,
  isDefault: true,
  createdAt: null,
  updatedAt: null,
  ...overrides,
})

async function renderTemplates() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.workspaces = [{ id: 2, ownerId: 1, name: '내 작업공간', slug: 'mine', description: null, logoUrl: null, memberCount: 1, createdAt: null }]
  workspace.activeWorkspaceId = 2
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/ugc/shorts/templates', component: { template: '<div />' } }, { path: '/ugc/shorts/prompts', component: { template: '<div />' } }] })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/ugc/shorts/templates')
  await router.isReady()
  const wrapper = mount(ShortsTemplatesView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PageHeader: { template: '<header><h1>{{ title }}</h1><slot name="actions" /></header>', props: ['title'] },
        BaseModal: { template: '<div v-if="modelValue" role="dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
        ConfirmModal: { template: '<div v-if="modelValue" role="dialog" />', props: ['modelValue'] },
        EmptyState: true,
        LoadingSpinner: true,
        ArrowUpTrayIcon: true,
        ChatBubbleLeftRightIcon: true,
        PlusIcon: true,
        RectangleGroupIcon: true,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('ShortsTemplatesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ugcShortsTemplateApi.list).mockResolvedValue([template()] as never)
    vi.mocked(ugcShortsTemplateApi.update).mockResolvedValue(template({ name: '수정된 템플릿' }) as never)
    vi.mocked(ugcShortsTemplateApi.create).mockResolvedValue(template({ id: 6, name: '새 템플릿' }) as never)
  })

  it('renders a server template, edits it and sends normalized settings', async () => {
    const wrapper = await renderTemplates()
    expect(wrapper.text()).toContain('세로 기본')
    const edit = wrapper.findAll('button').find((button) => button.text() === '수정')
    expect(edit).toBeDefined()
    await edit!.trigger('click')
    await wrapper.get('#shorts-tpl-name').setValue(' 수정된 템플릿 ')
    await wrapper.findAll('button').find((button) => button.text() === '저장')!.trigger('click')
    await flushPromises()
    expect(ugcShortsTemplateApi.update).toHaveBeenCalledWith(2, 5, expect.objectContaining({ name: '수정된 템플릿', description: '쇼츠용 기본 템플릿' }))
  })

  it('opens create form and rejects a blank template name before calling the server', async () => {
    const wrapper = await renderTemplates()
    const create = wrapper.findAll('button').find((button) => button.text() === '새 템플릿')
    expect(create).toBeDefined()
    await create!.trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '만들기')!.trigger('click')
    await flushPromises()
    expect(ugcShortsTemplateApi.create).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  })
})
