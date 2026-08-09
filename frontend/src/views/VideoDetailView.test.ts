import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import VideoDetailView from './VideoDetailView.vue'
import { videoApi } from '@/api/video'
import { analyticsApi } from '@/api/analytics'
import { useNotificationStore } from '@/stores/notification'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/video', () => ({
  videoApi: {
    get: vi.fn(),
    recheckUpload: vi.fn(),
    retryUpload: vi.fn(),
  },
}))
vi.mock('@/api/analytics', () => ({ analyticsApi: { videoAnalytics: vi.fn() } }))

const video = (uploadStatus: 'PUBLISHED' | 'UNCONFIRMED' = 'UNCONFIRMED') => ({
  id: 42,
  userId: 7,
  title: '게시 확인 테스트',
  description: null,
  tags: [],
  category: null,
  mediaType: 'VIDEO',
  fileUrl: 'https://storage.test/video.mp4',
  thumbnailUrl: null,
  thumbnailCandidates: [],
  fileSize: 4,
  status: uploadStatus,
  visibility: 'PUBLIC',
  createdAt: '2026-08-10T00:00:00Z',
  updatedAt: '2026-08-10T00:00:00Z',
  uploads: [{
    id: 101,
    videoId: 42,
    platform: 'YOUTUBE',
    channelId: 9,
    channelName: '테스트 채널',
    status: uploadStatus,
    platformVideoId: uploadStatus === 'PUBLISHED' ? 'yt-1' : 'publish-1',
    platformUrl: uploadStatus === 'PUBLISHED' ? 'https://youtube.test/watch/yt-1' : null,
    description: null,
    tags: [],
    errorMessage: uploadStatus === 'UNCONFIRMED' ? '응답을 확인하지 못했습니다.' : null,
    publishedAt: uploadStatus === 'PUBLISHED' ? '2026-08-10T00:01:00Z' : null,
    createdAt: '2026-08-10T00:00:00Z',
    meta: null,
  }],
})

async function renderDetail() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/videos/:id', component: { template: '<div />' } }],
  })
  await router.push('/videos/42')
  await router.isReady()
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(VideoDetailView, {
    props: { id: '42' },
    shallow: true,
    global: { plugins: [pinia, router, i18n] },
  })
  await flushPromises()
  return { wrapper, pinia }
}

describe('VideoDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(videoApi.get).mockResolvedValue(video() as never)
    vi.mocked(videoApi.recheckUpload).mockRejectedValue(new Error('게시 상태 API 장애'))
    vi.mocked(videoApi.retryUpload).mockResolvedValue(undefined as never)
    vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([] as never)
  })

  it('shows a toast when publish recovery fails instead of swallowing the error', async () => {
    const { wrapper, pinia } = await renderDetail()
    const recover = wrapper.findAll('button').find((button) => button.text() === '게시 결과 재확인')
    expect(recover).toBeDefined()

    await recover!.trigger('click')
    await flushPromises()

    const notification = useNotificationStore(pinia)
    expect(notification.toasts.some((toast) => toast.message === '게시 상태 API 장애')).toBe(true)
  })

  it('renders a usable provider link only when the server returns one', async () => {
    vi.mocked(videoApi.get).mockResolvedValue(video('PUBLISHED') as never)
    const { wrapper } = await renderDetail()

    const link = wrapper.get('a[href="https://youtube.test/watch/yt-1"]')
    expect(link.text()).toBe('플랫폼에서 열기')
  })
})
