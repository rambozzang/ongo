import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
import { useCreditStore } from '@/stores/credit'
import { useUploadStore } from '@/stores/upload'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
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
    getRenderAvailability: vi.fn(),
    create: vi.fn(),
    get: vi.fn(),
    selectHooks: vi.fn(),
    startRender: vi.fn(),
    getRenderStatus: vi.fn(),
    confirmSchedule: vi.fn(),
  },
}))
vi.mock('@/api/video', () => ({
  videoApi: {
    getUploadCapabilities: vi.fn(),
    getImportAvailability: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
    publish: vi.fn(),
    create: vi.fn(),
    uploadImages: vi.fn(),
    importUrl: vi.fn(),
    generate: vi.fn(),
  },
}))
vi.mock('@/api/settings', () => ({
  settingsApi: { getSettings: vi.fn() },
}))
vi.mock('@/api/templates', () => ({ templatesApi: { get: vi.fn() } }))
vi.mock('@/api/credit', () => ({
  creditApi: {
    getBalance: vi.fn(),
    getTransactions: vi.fn(),
    getPackages: vi.fn().mockResolvedValue([]),
    list: vi.fn(),
    purchase: vi.fn(),
  },
}))

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

function renderCompose(initialPath = '/compose') {
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
  return router.push(initialPath).then(async () => {
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

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

describe('ComposeView 크레딧 차단 CTA (META_GENERATION / STT)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [channel('YOUTUBE', 1), channel('INSTAGRAM', 2)],
    } as never)
    vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue(capabilities as never)
    vi.mocked(videoApi.getImportAvailability).mockResolvedValue({
      available: false,
      reason: '가져오기 비활성',
    } as never)
    vi.mocked(settingsApi.getSettings).mockResolvedValue({
      defaultVisibility: 'PUBLIC',
      defaultPlatforms: [],
      defaultAiTone: 'FRIENDLY',
      defaultAiProvider: 'OPENAI',
      notificationUpload: true,
      notificationComment: 'none',
      notificationCreditThreshold: 10,
      notificationScheduleReminder: 24,
    } as never)
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({
      slots: [
        {
          dayOfWeek: 1,
          dayLabel: '월',
          hour: 9,
          timeLabel: '09:00',
          expectedViews: 10,
          engagementRate: 1,
          confidenceScore: 1,
          score: 1,
        },
      ],
    } as never)
    vi.mocked(subtitleEditorApi.listTracksByVideo).mockResolvedValue([
      {
        id: 1,
        videoId: 101,
        videoTitle: null,
        language: 'ko',
        status: 'COMPLETED',
        cues: JSON.stringify([{ start: 0, end: 2, text: '기존 자막' }]),
        totalDuration: 2,
        wordCount: 2,
        createdAt: null,
        updatedAt: null,
      },
    ] as never)
    vi.mocked(aiApi.generateMeta).mockResolvedValue({
      platforms: [
        {
          platform: 'YOUTUBE',
          titleCandidates: ['유튜브 자동 제목'],
          description: '유튜브 설명',
          hashtags: ['유튜브'],
        },
      ],
    } as never)
    vi.mocked(videoApi.update).mockResolvedValue(undefined as never)
    vi.mocked(videoApi.publish).mockResolvedValue(undefined as never)
    vi.mocked(recurringApi.create).mockResolvedValue(undefined as never)
    vi.mocked(videoApi.generate).mockResolvedValue([
      { id: '202', path: 'https://cdn.example/generated.mp4' },
    ] as never)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('META_GENERATION 안정 코드면 충전 CTA가 노출되고 클릭하면 실제 구매 모달이 열린다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      metaCreditBlocked: boolean
    }
    // 사전 transcriptFor(STT) 를 건너뛰어 META_GENERATION 차단만 격리한다.
    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()

    expect(vm.metaCreditBlocked).toBe(true)
    const cta = wrapper.find('[data-testid="compose-credit-cta"]')
    expect(cta.exists()).toBe(true)
    expect(cta.text()).toContain('크레딧 충전하기')
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('메타 생성 사전 transcriptFor STT 크레딧 부족도 충전 CTA로 이어진다', async () => {
    vi.mocked(aiApi.stt).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string; title: string }
      uploadStore: { isImage: boolean }
      generateMetadataFor: (id: number) => Promise<void>
      metaCreditBlocked: boolean
    }
    // 텍스트가 비어 있어 스크립트 확보를 위해 transcriptFor(STT) 가 실제로 호출되게 한다.
    vm.form.description = ''
    vm.form.title = ''
    vm.uploadStore.isImage = false
    // 기존 자막 트랙이 없어야 STT 를 호출한다.
    vi.mocked(subtitleEditorApi.listTracksByVideo).mockResolvedValue([])
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()

    expect(vm.metaCreditBlocked).toBe(true)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(true)
  })

  it('일반 오류(비 Error 객체)면 호출부 기존 메타 생성 실패 안내가 유지되고 CTA는 없다', async () => {
    // 실제 경로: import 성공 → generateMetadataFor 실패를 importFromUrl 이 잡아 기존 안내 노출.
    vi.mocked(videoApi.importUrl).mockResolvedValue({
      videoId: 101,
      title: '가져온 영상',
      provider: 'YOUTUBE',
      fileUrl: null,
      durationSec: 30,
      thumbnailUrl: null,
    } as never)
    vi.mocked(aiApi.generateMeta).mockRejectedValue({ notAnError: true })
    vi.mocked(videoApi.getImportAvailability).mockResolvedValue({ available: true } as never)

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      importUrl: string
      importFromUrl: () => Promise<void>
      metaCreditBlocked: boolean
      notice: string
    }
    vm.importUrl = 'http://example.com/x.mp4'
    await vm.importFromUrl()
    await flushPromises()

    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
    expect(vm.metaCreditBlocked).toBe(false)
    // 실제 호출부가 기존 안내(metadataFailed)를 그대로 보여준다.
    expect(vm.notice).toContain('메타데이터 자동 생성에 실패했습니다')
  })

  it('수동 자동자막(STT) 안정 코드면 sttCreditBlocked + 충전 CTA가 노출된다', async () => {
    vi.mocked(aiApi.stt).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      importedVideoId: number
      requestCaptions: () => Promise<void>
      sttCreditBlocked: boolean
    }
    vm.importedVideoId = 999
    await vm.requestCaptions()
    await flushPromises()

    expect(vm.sttCreditBlocked).toBe(true)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(true)
  })

  it('정상 메타 생성은 결과를 적용하고 CTA가 없다', async () => {
    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      metaCreditBlocked: boolean
      notice: string
    }
    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()

    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
    expect(vm.metaCreditBlocked).toBe(false)
    expect(vm.notice).toContain('자동 생성했습니다')
  })

  it('purchase 이벤트 뒤 creditStore.fetchBalance 호출 + 차단 상태 정리(자동 재실행 없음)', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      metaCreditBlocked: boolean
    }
    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()
    expect(vm.metaCreditBlocked).toBe(true)

    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi.spyOn(creditStore, 'fetchBalance').mockResolvedValue(undefined as never)
    const callsAfterBlock = vi.mocked(aiApi.generateMeta).mock.calls.length

    await wrapper.findComponent(CreditPurchaseModal).vm.$emit('purchase', {
      key: 'STARTER',
      name: 'Starter',
      pricePerCredit: 100,
      validDays: 30,
    })
    await flushPromises()

    expect(fetchBalanceSpy).toHaveBeenCalled()
    expect(vm.metaCreditBlocked).toBe(false)
    // 자동 재실행 없음 — 구매 이벤트 전후 generateMeta 호출 횟수 동일.
    expect(vi.mocked(aiApi.generateMeta).mock.calls.length).toBe(callsAfterBlock)
  })

  it('PLAN_LIMIT_EXCEEDED는 크레딧 CTA를 띄우지 않고 기존 안내를 유지한다', async () => {
    vi.mocked(aiApi.stt).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      importedVideoId: number
      requestCaptions: () => Promise<void>
      sttCreditBlocked: boolean
      notice: string
    }
    vm.importedVideoId = 999
    await vm.requestCaptions()
    await flushPromises()

    expect(vm.sttCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
    expect(vm.notice).toContain('자막 생성에 실패했습니다')
  })

  it('기존 PLAN 업그레이드 배너가 여전히 렌더되고 크레딧 CTA와 공존한다', async () => {
    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as { showPlanUpgrade: boolean }
    vm.showPlanUpgrade = true
    await flushPromises()

    expect(wrapper.text()).toContain('플랜 업그레이드하기')
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
  })

  it('차단 CTA 노출 중 selectSourceMode(새 소스 시작) 시 이전 CTA가 사라진다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      metaCreditBlocked: boolean
      sttCreditBlocked: boolean
      selectSourceMode: (m: 'file' | 'url' | 'generate') => void
    }
    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()
    expect(vm.metaCreditBlocked).toBe(true)

    vm.selectSourceMode('url')
    await flushPromises()

    expect(vm.metaCreditBlocked).toBe(false)
    expect(vm.sttCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
  })

  it('차단 CTA 노출 중 URL 가져오기 실패(업로드 단계)에도 이전 CTA가 사라진다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))
    // importFromUrl 은 importAvailable 이 true 여야 진입하므로 가용 상태로 바꾼다.
    vi.mocked(videoApi.getImportAvailability).mockResolvedValue({ available: true } as never)

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      importUrl: string
      importFromUrl: () => Promise<void>
      metaCreditBlocked: boolean
      notice: string
    }
    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()
    expect(vm.metaCreditBlocked).toBe(true)

    // 가져오기 단계 자체가 실패한다(메타 생성 도달 전).
    vi.mocked(videoApi.importUrl).mockRejectedValue(new Error('import failed'))
    vm.importUrl = 'http://example.com/x.mp4'
    await vm.importFromUrl()
    await flushPromises()

    expect(vm.metaCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
    // 실패 안내는 여전히 남는다(기존 오류 동작 보존).
    expect(vm.notice.length).toBeGreaterThan(0)
  })

  it('차단 CTA 노출 중 영상 생성 실패에도 이전 CTA가 사라진다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      generationPrompt: string
      generateVideo: () => Promise<void>
      metaCreditBlocked: boolean
      notice: string
    }
    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()
    expect(vm.metaCreditBlocked).toBe(true)

    vi.mocked(videoApi.generate).mockRejectedValue(new Error('gen failed'))
    vm.generationPrompt = 'a prompt'
    await vm.generateVideo()
    await flushPromises()

    expect(vm.metaCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
    expect(vm.notice.length).toBeGreaterThan(0)
  })

  it('차단 CTA 노출 중 새 파일 선택(onFileChosen) 시작 시 이전 CTA가 사라진다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = await renderCompose()
    const vm = wrapper.vm as unknown as {
      form: { description: string }
      generateMetadataFor: (id: number) => Promise<void>
      metaCreditBlocked: boolean
      onFileChosen: (e: Event) => Promise<void>
    }
    const uploadStore = useUploadStore()
    vi.spyOn(uploadStore, 'startUpload').mockRejectedValue(new Error('upload failed'))

    vm.form.description = '스크립트'
    await vm.generateMetadataFor(101).catch(() => undefined)
    await flushPromises()
    expect(vm.metaCreditBlocked).toBe(true)

    const event = {
      target: { files: [new File(['x'], 'x.mp4', { type: 'video/mp4' })] },
    } as unknown as Event
    await vm.onFileChosen(event)
    await flushPromises()

    expect(vm.metaCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="compose-credit-cta"]').exists()).toBe(false)
  })
})
