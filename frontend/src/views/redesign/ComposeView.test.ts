import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import ComposeView from './ComposeView.vue'
import { aiApi } from '@/api/ai'
import { analyticsApi } from '@/api/analytics'
import { channelApi } from '@/api/channel'
import { recurringApi } from '@/api/recurring'
import { subtitleEditorApi } from '@/api/subtitleEditor'
import { videoApi } from '@/api/video'
import { settingsApi } from '@/api/settings'
import { ugcShortsPipelineApi } from '@/api/ugcShortsPipeline'
import { useUploadStore } from '@/stores/upload'
import { useWorkspaceStore } from '@/stores/workspace'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ai', () => ({
  aiApi: { stt: vi.fn(), generateMeta: vi.fn() },
}))
vi.mock('@/api/analytics', () => ({ analyticsApi: { getOptimalTimes: vi.fn() } }))
vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn() } }))
vi.mock('@/api/recurring', () => ({ recurringApi: { create: vi.fn() } }))
vi.mock('@/api/subtitleEditor', () => ({
  parseCues: (json: string) => JSON.parse(json),
  serializeCues: (cues: unknown[]) => JSON.stringify(cues),
  countWords: (cues: Array<{ text: string }>) => cues.length,
  totalDurationOf: (cues: Array<{ end: number }>) => Math.max(...cues.map((cue) => cue.end), 0),
  subtitleEditorApi: { listTracksByVideo: vi.fn(), createTrack: vi.fn() },
}))
vi.mock('@/api/ugcShortsPipeline', () => ({
  ugcShortsPipelineApi: {
    getRenderAvailability: vi.fn(), create: vi.fn(), get: vi.fn(), selectHooks: vi.fn(),
    startRender: vi.fn(), getRenderStatus: vi.fn(), confirmSchedule: vi.fn(),
  },
}))
vi.mock('@/api/video', () => ({
  videoApi: {
    getUploadCapabilities: vi.fn(), getImportAvailability: vi.fn(), update: vi.fn(), publish: vi.fn(),
    create: vi.fn(), importUrl: vi.fn(), generate: vi.fn(),
  },
}))
vi.mock('@/api/settings', () => ({
  settingsApi: { getSettings: vi.fn() },
}))
vi.mock('@/api/templates', () => ({ templatesApi: { get: vi.fn() } }))

const channel = (platform: 'YOUTUBE' | 'INSTAGRAM', id: number) => ({
  id,
  userId: 1,
  platform,
  platformChannelId: `channel-${id}`,
  channelName: platform === 'YOUTUBE' ? '유튜브 채널' : '인스타 채널',
  channelUrl: null,
  subscriberCount: 100,
  tokenStatus: 'ACTIVE',
  lastSyncedAt: null,
})

const capabilities = [
  { platform: 'YOUTUBE', maxTitleLength: 100, maxDescriptionLength: 5000, maxTagCount: 30 },
  { platform: 'INSTAGRAM', maxTitleLength: 2200, maxDescriptionLength: 2200, maxTagCount: 30 },
]

function renderCompose() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/compose', component: { template: '<div />' } },
      { path: '/today', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  return router.push('/compose').then(async () => {
    await router.isReady()
    const wrapper = mount(ComposeView, {
      global: {
        plugins: [pinia, router, i18n],
        stubs: {
          ThumbPlaceholder: { template: '<div />' },
          PlatformPreviewPanel: {
            name: 'PlatformPreviewPanel',
            props: ['targets'],
            template: '<div data-testid="preview" />',
          },
        },
      },
    })
    await flushPromises()
    return { wrapper, router }
  })
}

describe('ComposeView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel('YOUTUBE', 1), channel('INSTAGRAM', 2)],
    } as never)
    vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue(capabilities as never)
    vi.mocked(videoApi.getImportAvailability).mockResolvedValue({ available: false, reason: '가져오기 비활성' } as never)
    vi.mocked(settingsApi.getSettings).mockResolvedValue({
      defaultVisibility: 'PUBLIC', defaultPlatforms: [], defaultAiTone: 'FRIENDLY', defaultAiProvider: 'OPENAI',
      notificationUpload: true, notificationComment: 'none', notificationCreditThreshold: 10, notificationScheduleReminder: 24,
    } as never)
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({
      slots: [{ dayOfWeek: 1, dayLabel: '월', hour: 9, timeLabel: '09:00', expectedViews: 10, engagementRate: 1, confidenceScore: 1, score: 1 }],
    } as never)
    vi.mocked(subtitleEditorApi.listTracksByVideo).mockResolvedValue([{
      id: 1, videoId: 101, videoTitle: null, language: 'ko', status: 'COMPLETED',
      cues: JSON.stringify([{ start: 0, end: 2, text: '기존 자막' }]), totalDuration: 2, wordCount: 2,
      createdAt: null, updatedAt: null,
    }] as never)
    vi.mocked(aiApi.generateMeta).mockResolvedValue({
      platforms: [
        { platform: 'YOUTUBE', titleCandidates: ['유튜브 자동 제목'], description: '유튜브 설명', hashtags: ['유튜브'] },
        { platform: 'INSTAGRAM', titleCandidates: ['인스타 자동 제목'], description: '인스타 설명', hashtags: ['인스타'] },
      ],
    } as never)
    vi.mocked(videoApi.update).mockResolvedValue(undefined as never)
    vi.mocked(videoApi.publish).mockResolvedValue(undefined as never)
    vi.mocked(recurringApi.create).mockResolvedValue(undefined as never)
    vi.mocked(videoApi.generate).mockResolvedValue([{ id: '202', path: 'https://cdn.example/generated.mp4' }] as never)
  })

  it('keeps common and channel-specific metadata independently editable', async () => {
    const { wrapper } = await renderCompose()
    const title = wrapper.get('#compose-title')
    await title.setValue('공통 제목')
    const instagramTab = wrapper.get('[role="tab"]:nth-child(3)')
    await instagramTab.trigger('click')
    await wrapper.get('#compose-title').setValue('인스타 전용 제목')
    await wrapper.get('[role="tab"]:first-child').trigger('click')
    await wrapper.get('#compose-title').setValue('공통 수정 제목')
    await instagramTab.trigger('click')

    expect((wrapper.get('#compose-title').element as HTMLInputElement).value).toBe('인스타 전용 제목')
  })

  it('passes one preview target per selected account', async () => {
    const { wrapper } = await renderCompose()
    const preview = wrapper.findComponent({ name: 'PlatformPreviewPanel' })

    expect(preview.props('targets')).toEqual([
      expect.objectContaining({ key: 'YOUTUBE#1', channelName: '유튜브 채널' }),
      expect.objectContaining({ key: 'INSTAGRAM#2', channelName: '인스타 채널' }),
    ])
  })

  it('sends common and platform-specific visibility in the same publish request', async () => {
    const { wrapper } = await renderCompose()
    await wrapper.get('#compose-visibility').setValue('PRIVATE')
    await wrapper.get('[role="tab"]:nth-child(3)').trigger('click')
    await wrapper.get('#compose-visibility').setValue('UNLISTED')

    const uploadStore = useUploadStore()
    uploadStore.file = new File(['video'], 'source.mp4', { type: 'video/mp4' })
    uploadStore.videoId = 101
    await wrapper.findAll('input[type="checkbox"]')[0].setValue(false)

    const schedule = wrapper.findAll('button').find((button) => button.text().includes('2개 채널 예약'))
    await schedule!.trigger('click')
    await flushPromises()

    expect(videoApi.publish).toHaveBeenCalledWith(101, expect.objectContaining({
      platforms: expect.arrayContaining([
        expect.objectContaining({ platform: 'YOUTUBE', visibility: 'PRIVATE' }),
        expect.objectContaining({ platform: 'INSTAGRAM', visibility: 'UNLISTED' }),
      ]),
    }))
  })

  it('shows a server loading error instead of enabling a fake publish flow', async () => {
    vi.mocked(channelApi.list).mockRejectedValue(new Error('채널 API 장애'))
    const { wrapper } = await renderCompose()
    expect(wrapper.get('[role="alert"]').text()).toContain('채널 API 장애')
    expect(wrapper.text()).toContain('연결된 채널이 없습니다')
    const schedule = wrapper.findAll('button').find((button) => button.text().includes('예약'))
    expect(schedule?.attributes('disabled')).toBeDefined()
  })

  it('generates metadata from saved subtitles and publishes all selected channels in one submit', async () => {
    const { wrapper, router } = await renderCompose()
    const uploadStore = useUploadStore()
    uploadStore.file = new File(['video'], 'source.mp4', { type: 'video/mp4' })
    uploadStore.videoId = 101
    await wrapper.findAll('input[type="checkbox"]')[0].setValue(false)

    const schedule = wrapper.findAll('button').find((button) => button.text().includes('2개 채널 예약'))
    expect(schedule).toBeDefined()
    await schedule!.trigger('click')
    await flushPromises()

    expect(subtitleEditorApi.listTracksByVideo).toHaveBeenCalledWith(101)
    expect(aiApi.generateMeta).toHaveBeenCalledWith(expect.objectContaining({ videoId: 101, targetPlatforms: ['YOUTUBE', 'INSTAGRAM'] }))
    expect(videoApi.update).toHaveBeenCalledWith(101, expect.objectContaining({
      title: '유튜브 자동 제목',
      platforms: expect.arrayContaining([
        expect.objectContaining({ platform: 'YOUTUBE', title: '유튜브 자동 제목' }),
        expect.objectContaining({ platform: 'INSTAGRAM', title: '인스타 자동 제목' }),
      ]),
    }))
    expect(videoApi.publish).toHaveBeenCalledWith(101, expect.objectContaining({
      platforms: expect.arrayContaining([
        expect.objectContaining({ platform: 'YOUTUBE', title: '유튜브 자동 제목' }),
        expect.objectContaining({ platform: 'INSTAGRAM', title: '인스타 자동 제목' }),
      ]),
    }))
    expect(router.currentRoute.value.fullPath).toBe('/today')
  })

  it('does not overwrite a common field that the creator edited before auto-generation', async () => {
    const { wrapper } = await renderCompose()
    await wrapper.get('#compose-title').setValue('내가 직접 정한 제목')

    const uploadStore = useUploadStore()
    uploadStore.file = new File(['video'], 'source.mp4', { type: 'video/mp4' })
    uploadStore.videoId = 101
    await wrapper.findAll('input[type="checkbox"]')[0].setValue(false)

    const schedule = wrapper.findAll('button').find((button) => button.text().includes('2개 채널 예약'))
    await schedule!.trigger('click')
    await flushPromises()

    expect(videoApi.publish).toHaveBeenCalledWith(101, expect.objectContaining({
      platforms: expect.arrayContaining([
        expect.objectContaining({ platform: 'YOUTUBE', title: '내가 직접 정한 제목' }),
        expect.objectContaining({ platform: 'INSTAGRAM', title: '내가 직접 정한 제목' }),
      ]),
    }))
  })

  it('does not publish the original when automatic Shorts rendering is unavailable', async () => {
    vi.mocked(ugcShortsPipelineApi.getRenderAvailability).mockResolvedValue({
      available: false,
      reason: '서버 렌더러가 준비되지 않았습니다.',
    })
    const { wrapper } = await renderCompose()
    const uploadStore = useUploadStore()
    uploadStore.file = new File(['video'], 'source.mp4', { type: 'video/mp4' })
    uploadStore.videoId = 101

    const schedule = wrapper.findAll('button').find((button) => button.text().includes('2개 채널 예약'))
    expect(schedule).toBeDefined()
    await schedule!.trigger('click')
    await flushPromises()

    expect(videoApi.publish).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('서버 렌더러가 준비되지 않았습니다.')
  })

  it('saves metadata into the already uploaded server video instead of creating an orphan draft', async () => {
    const { wrapper } = await renderCompose()
    const uploadStore = useUploadStore()
    uploadStore.videoId = 101

    await wrapper.get('#compose-title').setValue('저장할 제목')
    const save = wrapper.findAll('button').find((button) => button.text().includes('임시 저장'))
    expect(save).toBeDefined()
    await save!.trigger('click')
    await flushPromises()

    expect(videoApi.update).toHaveBeenCalledWith(101, expect.objectContaining({ title: '저장할 제목' }))
    expect(videoApi.create).not.toHaveBeenCalled()
  })

  it('creates a source video in the composer and keeps it in the publish path', async () => {
    const { wrapper } = await renderCompose()
    const createSource = wrapper.findAll('button').find((button) => button.text() === '새 영상 만들기')
    expect(createSource).toBeDefined()
    await createSource!.trigger('click')
    await wrapper.get('#generate-video-prompt').setValue('아침 루틴을 소개하는 영상')

    const generate = wrapper.findAll('button').find((button) => button.text() === '영상 만들기')
    expect(generate).toBeDefined()
    await generate!.trigger('click')
    await flushPromises()

    expect(videoApi.generate).toHaveBeenCalledWith(expect.objectContaining({
      type: 'image-text-slides',
      output: 'vertical',
      customParams: expect.objectContaining({ prompt: '아침 루틴을 소개하는 영상' }),
    }))
    expect(useUploadStore().videoId).toBe(202)
    expect(aiApi.generateMeta).toHaveBeenCalledWith(expect.objectContaining({ videoId: 202 }))
  })

  it('keeps automatic captions available after importing a source URL', async () => {
    vi.mocked(videoApi.getImportAvailability).mockResolvedValue({ available: true, reason: null } as never)
    vi.mocked(videoApi.importUrl).mockResolvedValue({
      videoId: 303,
      title: '가져온 영상',
      provider: 'YouTube',
    } as never)
    const { wrapper } = await renderCompose()

    const urlSource = wrapper.findAll('button').find((button) => button.text() === 'URL 가져오기')
    await urlSource!.trigger('click')
    await wrapper.get('#source-video-url').setValue('https://youtu.be/example')
    await wrapper.findAll('button').find((button) => button.text() === '가져오기')!.trigger('click')
    await flushPromises()

    const caption = wrapper.findAll('button').find((button) => button.text() === '자동 자막')
    expect(caption).toBeDefined()
    expect(caption!.attributes('disabled')).toBeUndefined()
  })

  it('does not reuse a previous server video after switching source modes', async () => {
    const { wrapper } = await renderCompose()
    useUploadStore().videoId = 101

    const urlSource = wrapper.findAll('button').find((button) => button.text() === 'URL 가져오기')
    await urlSource!.trigger('click')

    expect(useUploadStore().videoId).toBeNull()
    expect(wrapper.get('#source-video-url').element).toBeTruthy()
  })

  it('does not allow saving while the server upload is still running', async () => {
    const { wrapper } = await renderCompose()
    const uploadStore = useUploadStore()
    uploadStore.uploading = true
    await wrapper.vm.$nextTick()

    const save = wrapper.findAll('button').find((button) => button.text().includes('임시 저장'))
    expect(save?.attributes('disabled')).toBeDefined()
  })

  it('runs the automatic Shorts pipeline after the original multi-channel publish', async () => {
    vi.mocked(ugcShortsPipelineApi.getRenderAvailability).mockResolvedValue({ available: true, reason: null })
    vi.mocked(ugcShortsPipelineApi.create).mockResolvedValue({ id: 77 } as never)
    vi.mocked(ugcShortsPipelineApi.get).mockResolvedValueOnce({
        run: { id: 77, status: 'COMPLETED', currentStage: null },
        clips: [{ id: 501, status: 'PUBLISHED', hooks: [] }],
      } as never)

    const { wrapper } = await renderCompose()
    const uploadStore = useUploadStore()
    uploadStore.file = new File(['video'], 'source.mp4', { type: 'video/mp4' })
    uploadStore.videoId = 101
    const workspaceStore = useWorkspaceStore()
    workspaceStore.workspaces = [{ id: 7 } as never]
    workspaceStore.activeWorkspaceId = 7

    const submit = wrapper.findAll('button').find((button) => button.text().includes('2개 채널 예약'))
    expect(submit).toBeDefined()
    await submit!.trigger('click')
    await flushPromises()

    expect(videoApi.publish).toHaveBeenCalledOnce()
    expect(ugcShortsPipelineApi.create).toHaveBeenCalledWith(7, expect.objectContaining({
      sourceVideoId: 101,
      templateId: null,
      autoSchedule: true,
      scheduleStartAt: expect.any(String),
      scheduleIntervalHours: 2,
      platforms: ['YOUTUBE#1', 'INSTAGRAM#2'],
    }))
    expect(ugcShortsPipelineApi.selectHooks).not.toHaveBeenCalled()
    expect(ugcShortsPipelineApi.startRender).not.toHaveBeenCalled()
    expect(ugcShortsPipelineApi.confirmSchedule).not.toHaveBeenCalled()
  })
})
