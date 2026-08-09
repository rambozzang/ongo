import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ChannelsView from './ChannelsView.vue'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/channel', () => ({
  channelApi: { list: vi.fn(), sync: vi.fn(), disconnect: vi.fn(), connect: vi.fn() },
}))
vi.mock('@/api/video', () => ({ videoApi: { getUploadCapabilities: vi.fn() } }))

const channel = (overrides: Record<string, unknown> = {}) => ({
  id: 7,
  userId: 1,
  platform: 'YOUTUBE',
  platformChannelId: 'channel-7',
  channelName: '내 채널',
  channelUrl: 'https://youtube.com/@me',
  subscriberCount: 1234,
  tokenStatus: 'ACTIVE',
  lastSyncedAt: '2026-08-09T10:00:00Z',
  ...overrides,
})

async function renderChannels() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/channels', component: { template: '<div />' } },
      { path: '/automation', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/channels')
  await router.isReady()
  return mount(ChannelsView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PlatformChip: { template: '<span class="platform-chip"><slot /></span>' },
        SectionCard: { template: '<section><slot /></section>' },
        StatusPill: { template: '<span><slot /></span>' },
        AdjustmentsHorizontalIcon: true,
        ExclamationTriangleIcon: true,
        LinkIcon: true,
        PlusIcon: true,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('ChannelsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders server-owned channels and syncs an individual channel', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel()], maxAllowed: 7, currentCount: 1 } as never)
    vi.mocked(channelApi.sync).mockResolvedValue(channel({ channelName: '동기화된 채널' }) as never)
    const wrapper = await renderChannels()
    await flushPromises()

    expect(wrapper.text()).toContain('내 채널')
    expect(wrapper.text()).toContain('1')
    expect(channelApi.list).toHaveBeenCalledOnce()

    const syncButton = wrapper.find('article button')
    expect(syncButton).toBeDefined()
    await syncButton!.trigger('click')
    await flushPromises()
    expect(channelApi.sync).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('동기화된 채널')
  })

  it('shows an actionable error instead of an empty fake state when the server fails', async () => {
    vi.mocked(channelApi.list).mockRejectedValue(new Error('채널 서버 장애'))
    const wrapper = await renderChannels()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('채널 정보를 불러오지 못했습니다.')
    expect(wrapper.text()).not.toContain('채널 서버 장애')
    const retry = wrapper.findAll('button').find((button) => button.text().includes('다시 시도'))
    expect(retry).toBeDefined()
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel({ tokenStatus: 'EXPIRED' })], maxAllowed: 7, currentCount: 1 } as never)
    await retry!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('내 채널')
  })

  it('surfaces a channel sync failure instead of reporting a false success', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel()], maxAllowed: 7, currentCount: 1 } as never)
    vi.mocked(channelApi.sync).mockRejectedValueOnce(new Error('동기화 장애'))
    const wrapper = await renderChannels()
    await flushPromises()
    const syncButton = wrapper.find('article button')
    expect(syncButton.exists()).toBe(true)
    await syncButton!.trigger('click')
    await flushPromises()
    expect(channelApi.sync).toHaveBeenCalledWith(7)
  })

  it('opens the real OAuth channel picker from the primary add action', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [], maxAllowed: 7, currentCount: 0 } as never)
    vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue([{
      platform: 'YOUTUBE',
      directVideoUpload: true,
      cloudVideoUpload: true,
      scheduling: true,
      maxFileSizeBytes: 2_000_000_000,
      maxTitleLength: 100,
      maxDescriptionLength: 5_000,
      maxTagCount: 30,
      acceptedExtensions: ['mp4'],
      unavailableReason: null,
    }] as never)
    const wrapper = await renderChannels()
    await flushPromises()

    const addButton = wrapper.findAll('button').find((button) => button.text().includes('새 채널 연결'))
    expect(addButton).toBeDefined()
    await addButton!.trigger('click')
    await flushPromises()
    expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain('YouTube')
  })
})
