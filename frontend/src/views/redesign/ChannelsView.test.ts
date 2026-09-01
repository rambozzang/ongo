import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ChannelsView from './ChannelsView.vue'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

vi.mock('@/api/channel', () => ({
  channelApi: { list: vi.fn(), sync: vi.fn(), disconnect: vi.fn(), connect: vi.fn(), authorizationUrl: vi.fn() },
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

async function renderChannels(locale: 'ko' | 'en' = 'ko') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/channels', component: { template: '<div />' } },
      { path: '/automation', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'ko',
    messages: { ko: koMessages, en: enMessages },
  })
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

  it('does not present a legacy Naver Clip row as healthy or syncable', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel({ platform: 'NAVER_CLIP', channelName: '이전 네이버 채널' })],
      maxAllowed: 7,
      currentCount: 1,
    } as never)
    const wrapper = await renderChannels()
    await flushPromises()

    expect(wrapper.text()).toContain('현재 미지원')
    expect(wrapper.text()).toContain('공개 업로드·분석 API가 없어')
    expect(wrapper.find('article button').exists()).toBe(false)
    expect(channelApi.sync).not.toHaveBeenCalled()
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
      configurationAvailable: true,
    }, {
      platform: 'TIKTOK',
      directVideoUpload: true,
      cloudVideoUpload: false,
      scheduling: false,
      maxFileSizeBytes: 2_000_000_000,
      maxTitleLength: 150,
      maxDescriptionLength: 2_200,
      maxTagCount: 5,
      acceptedExtensions: ['mp4'],
      unavailableReason: null,
      configurationAvailable: false,
    }] as never)
    const wrapper = await renderChannels()
    await flushPromises()

    const addButton = wrapper.findAll('button').find((button) => button.text().includes('새 채널 연결'))
    expect(addButton).toBeDefined()
    await addButton!.trigger('click')
    await flushPromises()
    const dialog = document.body.querySelector('[role="dialog"]')
    expect(dialog?.textContent).toContain('YouTube')
    expect(dialog?.textContent).toContain('현재 연결할 수 없는 플랫폼')
    expect(dialog?.textContent).toContain('TikTok')
    expect(Array.from(dialog?.querySelectorAll('button') ?? []).map((button) => button.textContent).join(' ')).not.toContain('TikTok')
  })

  /*
   * ── 구독자 수 미측정 표시 ──────────────────────────────────────────────
   *
   * Threads·LinkedIn 어댑터는 팔로워 수를 **묻지도 않고** `subscriberCount = 0` 을
   * 저장하고, Naver Clip 은 채널 조회 자체가 없다. 서버가 이제 그 자리를 `null` 로
   * 준다 — 예전에는 저장된 `0` 이 그대로 내려와 화면이 **"0"** 을 그렸고, 구독자가
   * 정말 없는 채널과 구분되지 않았다.
   */

  async function renderWithSubscribers(subscriberCount: number | null) {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel({ subscriberCount })],
      maxAllowed: 7,
      currentCount: 1,
    } as never)
    const wrapper = await renderChannels()
    await flushPromises()
    return wrapper
  }

  /*
   * 구독자 칸은 바로 뒤 칸이 "조회수" 라 값과 그 라벨을 붙여서 본다. 카드 요약줄에
   * "0 주의 / 0 오류" 가 있어 그냥 '0' 을 찾으면 어느 칸이든 통과해 버린다.
   */
  const subscriberCell = (text: string) => `${text}${koMessages.analyticsView.table.views}`

  /** **이 케이스가 미측정을 "0" 으로 그리던 자리다.** */
  it('구독자 수가 미측정이면 0 을 그리지 않는다', async () => {
    const wrapper = await renderWithSubscribers(null)

    expect(wrapper.text()).toContain(subscriberCell(koMessages.analyticsView.notMeasured))
    expect(wrapper.text()).not.toContain(subscriberCell('0'))
  })

  /** **측정된 0 은 관측이다.** 갓 만든 채널의 구독자 0 명. */
  it('측정된 0 구독자는 숫자 0 으로 그린다', async () => {
    const wrapper = await renderWithSubscribers(0)

    expect(wrapper.text()).toContain(subscriberCell('0'))
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  /** 측정된 값은 그대로 그린다 — 과도한 차단 회귀를 막는다. */
  it('측정된 구독자 수는 그대로 그린다', async () => {
    const wrapper = await renderWithSubscribers(1234)

    // 한국어 compact 표기는 "1.2천" 이다.
    expect(wrapper.text()).toContain(subscriberCell('1.2천'))
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  /*
   * ── 지표 라벨의 의미 ───────────────────────────────────────────────────
   *
   * 이 칸의 값은 **구독자 수**인데 라벨이 `channels.connected`("연결됨")였다. 숫자와
   * 라벨의 뜻이 달라, 사용자는 "1.2천"을 연결 상태나 연동 횟수로 읽을 수 있었다.
   *
   * 같은 화면에서 `channels.connected` 는 채널 URL 자리표시자와 토큰 상태 배지에도
   * 쓰인다 — 그 두 곳은 뜻이 맞으므로 그대로 두고, 이 칸만 고쳤다.
   */

  /** 라벨은 값 바로 앞에 온다 — 붙여서 봐야 다른 칸의 라벨과 섞이지 않는다. */
  const labelledCell = (label: string, value: string) => `${label}${value}`

  /** **이 케이스가 구독자 수에 "연결됨" 라벨을 달던 자리다.** */
  it('구독자 칸의 라벨이 구독자를 뜻한다 (ko)', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel({ subscriberCount: 1234 })],
      maxAllowed: 7,
      currentCount: 1,
    } as never)
    const wrapper = await renderChannels('ko')
    await flushPromises()

    expect(wrapper.text()).toContain(labelledCell(koMessages.channels.subscribers, '1.2천'))
    expect(wrapper.text()).not.toContain(labelledCell(koMessages.channels.connected, '1.2천'))
  })

  it('구독자 칸의 라벨이 구독자를 뜻한다 (en)', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel({ subscriberCount: 1234 })],
      maxAllowed: 7,
      currentCount: 1,
    } as never)
    const wrapper = await renderChannels('en')
    await flushPromises()

    // 영어 compact 표기는 "1.2K" 다.
    expect(wrapper.text()).toContain(labelledCell(enMessages.channels.subscribers, '1.2K'))
    expect(wrapper.text()).not.toContain(labelledCell(enMessages.channels.connected, '1.2K'))
  })

  /** 두 로케일 모두 키가 있어야 한다 — 한쪽만 넣으면 다른 쪽이 키 문자열을 그대로 그린다. */
  it('subscribers 키가 두 로케일에 모두 있고 연결 상태 문구와 다르다', () => {
    for (const messages of [koMessages, enMessages]) {
      expect(messages.channels.subscribers.trim().length).toBeGreaterThan(0)
      expect(messages.channels.subscribers).not.toBe(messages.channels.connected)
    }
  })

  /** 라벨을 고쳐도 미측정·실측 0 동작은 그대로다. */
  it('라벨이 바뀌어도 미측정 표시는 그대로다 (en)', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel({ subscriberCount: null })],
      maxAllowed: 7,
      currentCount: 1,
    } as never)
    const wrapper = await renderChannels('en')
    await flushPromises()

    expect(wrapper.text()).toContain(
      labelledCell(enMessages.channels.subscribers, enMessages.analyticsView.notMeasured),
    )
    expect(wrapper.text()).not.toContain(labelledCell(enMessages.channels.subscribers, '0'))
  })
})
