import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import SettingsView from './SettingsView.vue'
import { settingsApi } from '@/api/settings'
import { automationApi } from '@/api/automation'
import { subscriptionApi } from '@/api/subscription'
import { workspaceApi } from '@/api/workspace'
import { oauthApi } from '@/api/oauth'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/settings', () => ({
  settingsApi: {
    getSettings: vi.fn(),
    updateDefaults: vi.fn(),
    listApiKeys: vi.fn(),
    createApiKey: vi.fn(),
    revokeApiKey: vi.fn(),
  },
}))
vi.mock('@/api/automation', () => ({ automationApi: { list: vi.fn(), toggle: vi.fn() } }))
vi.mock('@/api/subscription', () => ({ subscriptionApi: { getCurrent: vi.fn(), getPlans: vi.fn() } }))
vi.mock('@/api/workspace', () => ({ workspaceApi: {
  list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
} }))
vi.mock('@/api/oauth', () => ({ oauthApi: {
  listApps: vi.fn(),
  createApp: vi.fn(),
  rotateSecret: vi.fn(),
  deleteApp: vi.fn(),
  listTokens: vi.fn(),
  revokeToken: vi.fn(),
} }))
vi.mock('@/api/auth', () => ({ authApi: {
  deleteAccount: vi.fn(),
  getAccountDeletionStatus: vi.fn(),
} }))

const rule = (active = true) => ({
  id: 8,
  name: '예약 게시 알림',
  description: '게시 결과를 알려줍니다',
  triggerType: 'UPLOAD_COMPLETED',
  triggerConfig: {},
  actionType: 'SEND_NOTIFICATION',
  actionConfig: {},
  isActive: active,
  lastTriggeredAt: null,
  executionCount: 2,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
})

async function renderSettings() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = {
    id: 1,
    email: 'creator@example.com',
    name: '크리에이터',
    nickname: '온고',
    profileImageUrl: null,
    category: 'IT',
    planType: 'PRO',
    role: 'USER',
    onboardingCompleted: true,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings', component: { template: '<div />' } },
      { path: '/settings-v2', component: { template: '<div />' } },
      { path: '/automation', component: { template: '<div />' } },
      { path: '/subscription', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/settings')
  await router.isReady()
  const wrapper = mount(SettingsView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        SectionCard: { template: '<section><h2>{{ title }}</h2><slot /></section>', props: ['title'] },
        StatusPill: { template: '<span><slot /></span>' },
        ConfirmModal: {
          template: '<div v-if="modelValue" role="dialog"><button type="button" @click="$emit(\'confirm\')">확인</button></div>',
          props: ['modelValue'],
          emits: ['confirm', 'update:modelValue'],
        },
        AdjustmentsHorizontalIcon: true,
        Cog6ToothIcon: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('SettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(settingsApi.getSettings).mockResolvedValue({ defaultVisibility: 'PRIVATE', defaultPlatforms: ['YOUTUBE'], defaultAiTone: 'FRIENDLY', defaultAiProvider: 'OPENAI', notificationUpload: true, notificationComment: 'REALTIME', notificationCreditThreshold: 20, notificationScheduleReminder: 60 } as never)
    vi.mocked(settingsApi.listApiKeys).mockResolvedValue([])
    vi.mocked(automationApi.list).mockResolvedValue([rule()] as never)
    vi.mocked(automationApi.toggle).mockResolvedValue(rule(false) as never)
    vi.mocked(subscriptionApi.getCurrent).mockResolvedValue(null as never)
    vi.mocked(subscriptionApi.getPlans).mockResolvedValue({ plans: [], currentPlan: 'FREE' } as never)
    vi.mocked(workspaceApi.list).mockResolvedValue([] as never)
    vi.mocked(oauthApi.listApps).mockResolvedValue([] as never)
    vi.mocked(oauthApi.listTokens).mockResolvedValue([] as never)
    vi.mocked(oauthApi.revokeToken).mockResolvedValue(undefined as never)
    vi.mocked(authApi.getAccountDeletionStatus).mockResolvedValue({
      state: 'ACTIVE', status: null, jobId: null, requestedAt: null, updatedAt: null,
      completedAt: null, lastErrorCode: null, supportReference: null, retryable: false,
    })
  })

  it('loads automation and defaults from the server, then saves changed defaults', async () => {
    vi.mocked(settingsApi.updateDefaults).mockResolvedValue(undefined as never)
    const wrapper = await renderSettings()

    expect(wrapper.text()).toContain('예약 게시 알림')
    const toggle = wrapper.get('[role="switch"]')
    await toggle.trigger('click')
    await flushPromises()
    expect(automationApi.toggle).toHaveBeenCalledWith(8)

    const defaults = wrapper.findAll('button').find((button) => button.text() === '기본 설정')
    expect(defaults).toBeDefined()
    await defaults!.trigger('click')
    await wrapper.find('select').setValue('PUBLIC')
    await wrapper.findAll('button').find((button) => button.text() === '저장')!.trigger('click')
    await flushPromises()
    expect(settingsApi.updateDefaults).toHaveBeenCalledWith({ visibility: 'PUBLIC', platforms: ['YOUTUBE'], aiTone: 'FRIENDLY', aiProvider: 'OPENAI' })
  })

  it('creates an API key and displays the one-time token in the security section', async () => {
    vi.mocked(settingsApi.createApiKey).mockResolvedValue({ id: 4, name: '게시 자동화', keyPrefix: 'ongo_', token: 'ongo_secret_token', lastUsedAt: null, expiresAt: null, revokedAt: null, createdAt: '2026-08-09T00:00:00Z' } as never)
    vi.mocked(settingsApi.listApiKeys).mockResolvedValue([]).mockResolvedValueOnce([]).mockResolvedValueOnce([{ id: 4, name: '게시 자동화', keyPrefix: 'ongo_', token: null, lastUsedAt: null, expiresAt: null, revokedAt: null, createdAt: '2026-08-09T00:00:00Z' }] as never)
    const wrapper = await renderSettings()
    const security = wrapper.findAll('button').find((button) => button.text() === '자동화 API 키')
    expect(security).toBeDefined()
    await security!.trigger('click')
    await wrapper.find('input[placeholder="예: 콘텐츠 자동 게시"]').setValue('게시 자동화')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(settingsApi.createApiKey).toHaveBeenCalledWith({ name: '게시 자동화' })
    expect(wrapper.text()).toContain('ongo_secret_token')
    expect(wrapper.text()).toContain('게시 자동화')
  })

  it('opens workspace management from the settings tab and creates a workspace through the server API', async () => {
    vi.mocked(workspaceApi.create).mockResolvedValue({ id: 7, name: '브랜드팀', slug: 'brand-team', description: null, ownerId: 1, logoUrl: null, memberCount: 1, createdAt: null } as never)
    const wrapper = await renderSettings()
    await wrapper.findAll('button').find((button) => button.text() === '워크스페이스')!.trigger('click')
    await wrapper.find('input[placeholder="예: 브랜드 콘텐츠팀"]').setValue('브랜드팀')
    await wrapper.find('input[placeholder="예: brand-content"]').setValue('brand-team')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(workspaceApi.create).toHaveBeenCalledWith({ name: '브랜드팀', slug: 'brand-team', description: null })
    expect(wrapper.text()).toContain('브랜드팀')
  })

  it('loads approved OAuth tokens from the server and revokes one token', async () => {
    vi.mocked(oauthApi.listApps).mockResolvedValue([{
      id: 3,
      clientId: 'pca_client',
      name: '콘텐츠 자동화',
      description: null,
      profilePictureUrl: null,
      redirectUri: 'https://client.example.com/callback',
      revokedAt: null,
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    }] as never)
    vi.mocked(oauthApi.listTokens).mockResolvedValue([{
      id: 9,
      appId: 3,
      tokenPrefix: 'pos_issued_token',
      createdAt: '2026-08-09T00:00:00Z',
      revokedAt: null,
    }] as never)
    const wrapper = await renderSettings()
    await wrapper.findAll('button').find((button) => button.text() === '개발자 앱')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('콘텐츠 자동화')
    expect(wrapper.text()).toContain('pos_issued_token')
    const revoke = wrapper.findAll('button').find((button) => button.text() === '접근 폐기')
    expect(revoke).toBeDefined()
    await revoke!.trigger('click')
    await wrapper.get('[role="dialog"] button').trigger('click')
    await flushPromises()
    expect(oauthApi.revokeToken).toHaveBeenCalledWith(9)
  })

  it('queues account deletion only after confirmation, then logs the user out', async () => {
    vi.mocked(authApi.deleteAccount).mockResolvedValue(undefined as never)
    const wrapper = await renderSettings()
    const auth = useAuthStore()
    const logout = vi.spyOn(auth, 'logout').mockResolvedValue()

    await wrapper.findAll('button').find((button) => button.text() === '계정')!.trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '탈퇴하기')!.trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    await wrapper.get('[role="dialog"] button').trigger('click')
    await flushPromises()

    expect(authApi.deleteAccount).toHaveBeenCalledTimes(1)
    expect(authApi.getAccountDeletionStatus).toHaveBeenCalledTimes(2)
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('shows a durable deletion status and support reference in the account section', async () => {
    vi.mocked(authApi.getAccountDeletionStatus).mockResolvedValue({
      state: 'DELETION_REQUESTED',
      status: 'BLOCKED_POLICY',
      jobId: 23,
      requestedAt: '2026-08-15T01:00:00Z',
      updatedAt: '2026-08-15T01:01:00Z',
      completedAt: null,
      lastErrorCode: 'POLICY_REVIEW',
      supportReference: 'review-block:payment',
      retryable: true,
    })
    const wrapper = await renderSettings()

    await wrapper.findAll('button').find((button) => button.text() === '계정')!.trigger('click')
    expect(wrapper.text()).toContain('계정 삭제 상태')
    expect(wrapper.text()).toContain('#23')
    expect(wrapper.text()).toContain('review-block:payment')
  })
})
