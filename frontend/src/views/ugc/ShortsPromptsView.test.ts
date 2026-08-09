import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ShortsPromptsView from './ShortsPromptsView.vue'
import { ugcShortsPromptApi } from '@/api/ugcShortsPrompt'
import { useWorkspaceStore } from '@/stores/workspace'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcShortsPrompt', () => ({
  ugcShortsPromptApi: { list: vi.fn(), update: vi.fn(), revisions: vi.fn(), resetToDefault: vi.fn(), restoreRevision: vi.fn() },
}))

const prompt = (overrides: Record<string, unknown> = {}) => ({
  id: 1,
  stage: 'HOOK',
  name: '후킹 문구 생성',
  description: '쇼츠 시작 문구를 생성합니다',
  systemPrompt: '시스템 지시',
  userPrompt: '영상에서 강한 후킹을 만들어 주세요',
  executable: true,
  revision: 2,
  customized: true,
  defaultSystemPrompt: '기본 시스템 지시',
  defaultUserPrompt: '기본 후킹 지시',
  updatedAt: '2026-08-09T10:00:00Z',
  ...overrides,
})

async function renderPrompts() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.workspaces = [{ id: 2, ownerId: 1, name: '내 작업공간', slug: 'mine', description: null, logoUrl: null, memberCount: 1, createdAt: null }]
  workspace.activeWorkspaceId = 2
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/ugc/shorts/prompts', component: { template: '<div />' } }, { path: '/ugc/shorts/templates', component: { template: '<div />' } }] })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/ugc/shorts/prompts')
  await router.isReady()
  const wrapper = mount(ShortsPromptsView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PageHeader: { template: '<header><h1>{{ title }}</h1><slot name="actions" /></header>', props: ['title'] },
        BaseModal: { template: '<div v-if="modelValue" role="dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
        ConfirmModal: { template: '<div v-if="modelValue" role="dialog" />', props: ['modelValue'] },
        LoadingSpinner: true,
        OTabs: { template: '<div />' },
        ChevronRightIcon: true,
        Square2StackIcon: true,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('ShortsPromptsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ugcShortsPromptApi.list).mockResolvedValue([prompt()] as never)
    vi.mocked(ugcShortsPromptApi.revisions).mockResolvedValue([] as never)
    vi.mocked(ugcShortsPromptApi.update).mockResolvedValue(prompt({ userPrompt: '새 후킹 지시', revision: 3 }) as never)
  })

  it('loads an overridden prompt and saves the edited user instruction', async () => {
    const wrapper = await renderPrompts()
    expect(wrapper.text()).toContain('후킹 문구 생성')
    await wrapper.find('button.card').trigger('click')
    await flushPromises()
    await wrapper.get('#shorts-user-prompt').setValue('새 후킹 지시')
    await wrapper.get('#shorts-change-note').setValue('전환율 개선')
    await wrapper.findAll('button').find((button) => button.text() === '저장')!.trigger('click')
    await flushPromises()
    expect(ugcShortsPromptApi.update).toHaveBeenCalledWith(2, 'HOOK', { systemPrompt: '시스템 지시', userPrompt: '새 후킹 지시', changeNote: '전환율 개선' })
  })
})
