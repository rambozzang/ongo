import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ShortsPipelineDetailView from './ShortsPipelineDetailView.vue'
import { ugcShortsPipelineApi } from '@/api/ugcShortsPipeline'
import { videoApi } from '@/api/video'
import { channelApi } from '@/api/channel'
import { useWorkspaceStore } from '@/stores/workspace'
import koMessages from '@/locales/ko/common.json'

/**
 * 렌더 전 검수.
 *
 * 이 화면이 없을 때는 렌더가 끝나야 결과를 처음 봤다. 크롭이 얼굴을 자르거나 자막이
 * 어긋나면 되돌릴 방법이 재렌더뿐인데 동시 렌더가 1건이라 재시도 비용이 크다.
 *
 * 검수 화면이 스스로 틀리면 더 나쁘다 — 프레임 크기를 모르는 채 크롭 사각형을 그리면
 * 사용자가 "이렇게 잘린다"고 믿어버린다. 그래서 "모른다"를 정직하게 말하는지까지 본다.
 */
vi.mock('@/api/ugcShortsPipeline', async () => {
  const actual = await vi.importActual<typeof import('@/api/ugcShortsPipeline')>('@/api/ugcShortsPipeline')
  return {
    ...actual,
    ugcShortsPipelineApi: {
      get: vi.fn(),
      downloadRenderSpec: vi.fn(),
      downloadRenderBundle: vi.fn(),
      selectHooks: vi.fn(),
      confirmSchedule: vi.fn(),
      rerunStage: vi.fn(),
      remove: vi.fn(),
      getRenderAvailability: vi.fn(),
      startRender: vi.fn(),
      getRenderStatus: vi.fn(),
      attachRenderedVideo: vi.fn(),
    },
  }
})
vi.mock('@/api/video', () => ({
  videoApi: { get: vi.fn(), list: vi.fn(), getUploadCapabilities: vi.fn() },
}))
vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn() } }))
vi.mock('@/api/workspace', () => ({ workspaceApi: { list: vi.fn() } }))
vi.mock('@/api/ugcShortsSheet', () => ({ ugcShortsSheetApi: { preview: vi.fn(), export: vi.fn() } }))

const REVIEW = koMessages.ugc.shorts.runs.review

const clip = (overrides: Record<string, unknown> = {}) => ({
  id: 31,
  seq: 1,
  startMs: 0,
  endMs: 15_000,
  durationMs: 15_000,
  title: '클립 1',
  caption: null,
  status: 'DRAFT',
  scheduledAt: null,
  hooks: [{ id: 1, variant: 'A', text: '이거 모르면 손해', selected: true }],
  subtitleCount: 4,
  hasRenderSpec: true,
  renderedVideoId: null,
  publications: [],
  ...overrides,
})

/** 대상 하나의 게시 결과. 기본은 예약 성공이다. */
const publication = (
  platform: string,
  status = 'SCHEDULED',
  errorMessage: string | null = null,
  channelName: string | null = null,
) => ({
  platform,
  channelName,
  status,
  errorMessage,
  scheduledAt: '2026-08-14T07:00:00Z',
  publishedAt: null,
})

/**
 * 검수가 실제로 도달 가능한 상태로 만든다.
 *
 * 서버는 훅 생성 직후 AWAITING_HOOK_SELECTION 으로 멈추고 render spec 은 그 뒤
 * RENDER_SPEC 단계에서 만들어진다. 즉 스펙이 있는 클립은 훅 게이트를 이미 지난
 * 상태(AWAITING_SCHEDULE 등)에서만 나타나고, 공통 클립 목록도 그때 렌더된다.
 */
const detail = (clips: ReturnType<typeof clip>[], status = 'AWAITING_SCHEDULE') => ({
  run: {
    id: 17,
    sourceVideoId: 4,
    sourceVideoTitle: '롱폼 원본',
    templateId: null,
    status,
    currentStage: status === 'AWAITING_HOOK_SELECTION' ? 'HOOK' : 'SCHEDULE',
    clipCount: clips.length,
    errorMessage: null,
    createdAt: '2026-08-09T10:20:00Z',
    updatedAt: '2026-08-09T10:20:00Z',
  },
  stages: [],
  clips,
})

const SOURCE_URL = 'https://storage.example/src.mp4'

const SPEC = {
  clipSeq: 1,
  source: { videoId: 4 },
  cut: { startMs: 12_000, endMs: 27_000 },
  reframe: {
    targetWidth: 1080,
    targetHeight: 1920,
    crop: { x: 480, y: 0, width: 1080, height: 1080 },
  },
  hook: { text: '이거 모르면 손해', position: 'TOP' },
  subtitles: [
    { startMs: 0, endMs: 900, text: '첫 번째 자막' },
    { startMs: 900, endMs: 1800, text: '두 번째 자막' },
    { startMs: 1800, endMs: 2700, text: '세 번째 자막' },
    { startMs: 2700, endMs: 3600, text: '네 번째 자막' },
  ],
}

function specBlob(spec: unknown) {
  return { text: async () => JSON.stringify(spec) } as unknown as Blob
}

/** 원본 조회는 fileUrl 만 준다. 해상도는 실제 재생 프레임에서만 읽는다. */
function mockSource(fileUrl: string | null = SOURCE_URL) {
  vi.mocked(videoApi.get).mockResolvedValue({ id: 4, fileUrl } as never)
}

/** 연결 채널 픽스처. tokenStatus 기본값은 정상 연결이다. */
const activeChannel = (overrides: Record<string, unknown> = {}) => ({
  id: 11,
  platform: 'YOUTUBE',
  channelName: '메인 채널',
  channelUrl: null,
  profileImageUrl: null,
  subscriberCount: 0,
  status: 'ACTIVE',
  tokenStatus: 'ACTIVE',
  connectedAt: '2026-08-01T00:00:00Z',
  lastSyncedAt: null,
  tokenExpiresAt: null,
  ...overrides,
})

/** 이 배포가 그 플랫폼을 올릴 수 있는지. 둘 다 false 면 대상이 될 수 없다. */
const capability = (
  platform: string,
  { directVideoUpload = true, cloudVideoUpload = true } = {},
) => ({
  platform,
  directVideoUpload,
  cloudVideoUpload,
  scheduling: false,
  maxFileSizeBytes: 1,
  maxTitleLength: 100,
  maxDescriptionLength: 100,
  maxTagCount: 0,
  acceptedExtensions: ['mp4'],
  unavailableReason: null,
})

interface TargetFixtures {
  channels?: ReturnType<typeof activeChannel>[]
  capabilities?: ReturnType<typeof capability>[]
  channelsFail?: boolean
}

async function renderDetail(
  clips = [clip()],
  status?: string,
  targets: TargetFixtures = {},
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.activeWorkspaceId = 5
  vi.spyOn(workspace, 'ensureActiveWorkspace').mockResolvedValue(5 as never)

  vi.mocked(ugcShortsPipelineApi.get).mockResolvedValue(detail(clips, status) as never)
  vi.mocked(ugcShortsPipelineApi.getRenderAvailability).mockResolvedValue({
    available: false,
    reason: null,
  } as never)
  /*
   * 예약 대상 조회. 기본은 "YouTube 채널 1개가 정상 연결됨"이다.
   *
   * 기본을 빈 목록으로 두면 예약 대상과 무관한 기존 테스트들까지 경고 화면을 보게 돼
   * 무엇을 검증하는지 흐려진다. 대상 자체를 보는 테스트는 targets 를 명시로 넘긴다.
   */
  if (targets.channels !== undefined) {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: targets.channels,
      maxAllowed: 4,
      currentCount: targets.channels.length,
    } as never)
  } else if (targets.channelsFail) {
    vi.mocked(channelApi.list).mockRejectedValue(new Error('채널 조회 장애'))
  } else {
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [activeChannel()],
      maxAllowed: 4,
      currentCount: 1,
    } as never)
  }
  vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue(
    (targets.capabilities ?? [capability('YOUTUBE')]) as never,
  )

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/ugc/shorts/runs/:id', component: ShortsPipelineDetailView },
      { path: '/ugc/shorts/runs', component: { template: '<div />' } },
      // 결과물 1차 행동의 두 이동 지점. 없으면 push 가 거부돼 처리되지 않은 예외가 된다.
      { path: '/videos/:id', name: 'video-detail', component: { template: '<div />' } },
      { path: '/compose', name: 'redesign-compose', component: { template: '<div />' } },
    ],
  })
  await router.push('/ugc/shorts/runs/17')
  await router.isReady()

  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(ShortsPipelineDetailView, {
    global: { plugins: [pinia, router, i18n], stubs: { PageHeader: true, BaseModal: true, ConfirmModal: true } },
  })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof renderDetail>>

const reviewButton = (wrapper: Wrapper) =>
  wrapper.findAll('button').find((b) => b.text().includes(REVIEW.show))

/** 스펙 로드 실패용 재시도와 구분하려고 aria-label 로 찾는다. */
const sourceRetryButton = (wrapper: Wrapper) =>
  wrapper.findAll('button').find((b) => b.attributes('aria-label') === REVIEW.retrySource)

/**
 * jsdom 은 미디어를 디코딩하지 않아 videoWidth/videoHeight 가 0 이고 loadedmetadata 도
 * 발생하지 않는다. 실제 브라우저가 알려주는 값을 흉내 내 이벤트를 손으로 발생시킨다.
 */
async function emitMetadata(
  wrapper: Wrapper,
  { videoWidth = 1920, videoHeight = 1080, duration = 60 } = {},
) {
  const el = wrapper.find('video').element as HTMLVideoElement
  Object.defineProperty(el, 'videoWidth', { value: videoWidth, configurable: true })
  Object.defineProperty(el, 'videoHeight', { value: videoHeight, configurable: true })
  Object.defineProperty(el, 'duration', { value: duration, configurable: true })
  await wrapper.find('video').trigger('loadedmetadata')
  await flushPromises()
  return el
}

async function openReview(wrapper: Wrapper) {
  await reviewButton(wrapper)!.trigger('click')
  await flushPromises()
}

describe('쇼츠 렌더 전 검수', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /*
   * 실행 하나에 클립이 여러 개다. 화면을 열자마자 전부 받아오면 대부분 보지도 않을
   * 스펙을 위해 요청이 그만큼 나간다.
   */
  it('버튼을 누르기 전에는 스펙을 요청하지 않는다', async () => {
    await renderDetail()

    expect(ugcShortsPipelineApi.downloadRenderSpec).not.toHaveBeenCalled()
  })

  /*
   * 훅 게이트에서는 스펙이 아직 없다. 여기에 버튼을 두면 정상 흐름에서 도달할 수 없는
   * 죽은 UI 가 된다 — 검수가 실제로 열리는 지점은 스펙이 만들어진 뒤다.
   */
  it('훅 선택 단계에서는 검수를 노출하지 않고, 스펙이 생긴 뒤에 노출한다', async () => {
    // 같은 클립(hasRenderSpec=true)이라도 훅 게이트에서는 목록 자체가 열리지 않는다.
    const hookStage = await renderDetail([clip()], 'AWAITING_HOOK_SELECTION')
    expect(reviewButton(hookStage)).toBeUndefined()

    const afterSpec = await renderDetail([clip()], 'AWAITING_SCHEDULE')
    expect(reviewButton(afterSpec)).toBeDefined()
  })

  it('스펙이 없는 클립에는 검수 버튼을 두지 않는다', async () => {
    const wrapper = await renderDetail([clip({ hasRenderSpec: false })])

    // 눌러도 아무 일이 없는 버튼은 원인을 찾을 수 없게 만든다.
    expect(reviewButton(wrapper)).toBeUndefined()
  })

  /*
   * 회색 상자로는 얼굴이 잘리는지 알 수 없다. 실제 원본 프레임 위에 겹쳐 봐야 한다.
   */
  it('원본 파일을 실제 video 로 재생하고 클립 시작 지점으로 이동한다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)

    expect(ugcShortsPipelineApi.downloadRenderSpec).toHaveBeenCalledWith(5, 17, 31)
    expect(videoApi.get).toHaveBeenCalledWith(4)
    expect(wrapper.find('video').attributes('src')).toBe(SOURCE_URL)

    // metadata 이전에 seek 하면 무시되거나 예외가 난다. 그래서 그 시점에만 옮긴다.
    const el = await emitMetadata(wrapper)
    expect(el.currentTime).toBe(12) // cut.startMs 12,000ms
  })

  /*
   * 시작점이 영상 길이 밖이면(스펙과 파일이 어긋난 경우) seek 하지 않는다. 범위를 벗어난
   * seek 은 브라우저마다 무시/예외/0 으로 갈려 무엇을 보고 있는지 알 수 없게 된다.
   */
  it('클립 시작점이 영상 길이 밖이면 seek 하지 않는다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)

    // cut.startMs 는 12초인데 파일은 5초짜리다.
    const el = await emitMetadata(wrapper, { duration: 5 })
    expect(el.currentTime).toBe(0)
    // 그래도 크롭 영역 판단은 가능하다.
    expect(wrapper.find('.absolute.border-2').exists()).toBe(true)
  })

  it('구간·자막 첫 3줄·총수·실제 크롭 영역을 보여준다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)
    await emitMetadata(wrapper)

    // 자막은 처음 3줄만, 총수는 4로 알린다.
    expect(wrapper.text()).toContain('첫 번째 자막')
    expect(wrapper.text()).toContain('세 번째 자막')
    expect(wrapper.text()).not.toContain('네 번째 자막')
    expect(wrapper.text()).toContain(REVIEW.subtitleTitle.replace('{count}', '4'))

    // native 해상도를 읽은 뒤에만 좌표를 비율로 환산해 그린다. 1920 폭에서 x=480 → 25%.
    const rect = wrapper.find('.absolute.border-2')
    expect(rect.exists()).toBe(true)
    expect(rect.attributes('style')).toContain('left: 25%')
    expect(wrapper.text()).toContain(
      REVIEW.cropMeasured.replace('{width}', '1920').replace('{height}', '1080'),
    )
  })

  /*
   * metadata 가 오기 전에는 프레임 크기를 모른다. 그 상태에서 사각형을 그리면
   * 사용자가 그 위치를 사실로 믿는다 — 검수 화면이 오히려 오도한다.
   */
  it('프레임 크기를 읽기 전에는 크롭 사각형을 그리지 않는다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)

    expect(wrapper.find('.absolute.border-2').exists()).toBe(false)
    expect(wrapper.text()).toContain(REVIEW.cropUnknownSize)
    // 자막·구간은 프레임 크기와 무관하므로 계속 보여준다.
    expect(wrapper.text()).toContain('첫 번째 자막')
  })

  /*
   * 파일이 깨졌거나 URL 이 만료됐는데도 사각형이 남아 있으면 "확인했다"는 착각을 준다.
   */
  it('원본을 재생할 수 없으면 크롭을 지우고 그렇게 말한다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)
    await emitMetadata(wrapper)
    expect(wrapper.find('.absolute.border-2').exists()).toBe(true)

    await wrapper.find('video').trigger('error')
    await flushPromises()

    expect(wrapper.find('.absolute.border-2').exists()).toBe(false)
    expect(wrapper.text()).toContain(REVIEW.sourceUnavailable)
    expect(wrapper.text()).not.toContain(
      REVIEW.cropMeasured.replace('{width}', '1920').replace('{height}', '1080'),
    )
  })

  /*
   * 원본 조회가 실패하면 미리보기 프레임 자체가 없다. 이때 "해상도 미상"이라고 하면
   * 곧 채워질 것처럼 들리지만 기다려도 아무것도 안 나온다 — 다시 물어봐야 풀린다.
   */
  it.each([
    ['조회 실패', () => vi.mocked(videoApi.get).mockRejectedValue(new Error('404'))],
    ['빈 fileUrl', () => mockSource('')],
    ['fileUrl 없음', () => mockSource(null)],
  ])('원본 %s 는 미리보기 불가로 알리고 재시도를 준다', async (_label, arrange) => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    arrange()

    const wrapper = await renderDetail()
    await openReview(wrapper)

    expect(wrapper.text()).toContain(REVIEW.sourceUnavailable)
    // "해상도 미상"은 원본이 재생될 때만 쓴다. 둘을 섞으면 원인을 못 찾는다.
    expect(wrapper.text()).not.toContain(REVIEW.cropUnknownSize)
    expect(wrapper.find('video').exists()).toBe(false)
    expect(wrapper.find('.absolute.border-2').exists()).toBe(false)
    // 자막·구간은 원본과 무관하므로 계속 검수할 수 있다.
    expect(wrapper.text()).toContain('첫 번째 자막')
  })

  it('원본 재조회 버튼이 새 lookup 을 실제로 일으킨다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    vi.mocked(videoApi.get).mockRejectedValue(new Error('404'))

    const wrapper = await renderDetail()
    await openReview(wrapper)
    expect(videoApi.get).toHaveBeenCalledTimes(1)

    const retry = sourceRetryButton(wrapper)
    expect(retry).toBeDefined()
    expect(retry!.attributes('aria-label')).toBe(REVIEW.retrySource)

    // 서명 만료였다면 재조회가 새 URL 을 준다.
    mockSource()
    await retry!.trigger('click')
    await flushPromises()

    expect(videoApi.get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain(REVIEW.sourceUnavailable)
    expect(wrapper.find('video').attributes('src')).toBe(SOURCE_URL)
    expect(sourceRetryButton(wrapper)).toBeUndefined()
  })

  /* 원본이 멀쩡히 재생 중인데 아직 metadata 가 안 온 상태와 섞이면 안 된다. */
  it('원본은 있는데 metadata 만 미상이면 미리보기 불가라고 하지 않는다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)

    expect(wrapper.text()).toContain(REVIEW.cropUnknownSize)
    expect(wrapper.text()).not.toContain(REVIEW.sourceUnavailable)
    expect(wrapper.find('video').exists()).toBe(true)
    expect(sourceRetryButton(wrapper)).toBeUndefined()
  })

  it('자막이 없고 크롭도 없으면 전체 화면 사용이라고 알린다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(
      specBlob({ ...SPEC, subtitles: [], reframe: { targetWidth: 1080, targetHeight: 1920 } }) as never,
    )
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)
    await emitMetadata(wrapper, { videoWidth: 1080, videoHeight: 1920 })

    expect(wrapper.text()).toContain(REVIEW.subtitleNone)
    expect(wrapper.text()).toContain(REVIEW.cropFullFrame)
    expect(wrapper.find('.absolute.border-2').exists()).toBe(false)
  })

  it.each([
    ['요청 실패', () => vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockRejectedValue(new Error('네트워크'))],
    ['파싱 실패', () => vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue({ text: async () => 'not json' } as never)],
  ])('%s 는 사유를 보여주고 재시도할 수 있다', async (_label, arrange) => {
    arrange()

    const wrapper = await renderDetail()
    await openReview(wrapper)

    expect(wrapper.text()).toContain(REVIEW.loadFailed)
    expect(wrapper.find('video').exists()).toBe(false)
    const retry = wrapper.findAll('button').find((b) => b.text().includes(koMessages.action.retry))
    expect(retry).toBeDefined()

    // 재시도가 성공하면 그때 결과가 나온다.
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()
    await retry!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain(REVIEW.loadFailed)
    expect(wrapper.text()).toContain('첫 번째 자막')
    expect(wrapper.find('video').attributes('src')).toBe(SOURCE_URL)
  })

  /*
   * 같은 클립을 다시 열 때마다 재요청하면 검수를 오갈수록 비용이 는다.
   */
  it('성공한 검수는 다시 열어도 재요청하지 않는다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    await openReview(wrapper)
    expect(ugcShortsPipelineApi.downloadRenderSpec).toHaveBeenCalledTimes(1)

    // 접었다가
    await wrapper.findAll('button').find((b) => b.text().includes(REVIEW.hide))!.trigger('click')
    await flushPromises()
    // 다시 연다
    await openReview(wrapper)

    expect(ugcShortsPipelineApi.downloadRenderSpec).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('첫 번째 자막')
  })

  /*
   * 검수는 기존 클립 목록에 얹는 것이지 대체하는 것이 아니다.
   */
  it('기존 클립 목록 기능을 가리지 않는다', async () => {
    const wrapper = await renderDetail()

    expect(wrapper.text()).toContain(koMessages.ugc.shorts.runs.detail.clips)
    const specButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes(koMessages.ugc.shorts.runs.detail.downloadSpec))
    expect(specButton).toBeDefined()
  })

  it('검수 패널은 버튼과 aria 로 연결된다', async () => {
    vi.mocked(ugcShortsPipelineApi.downloadRenderSpec).mockResolvedValue(specBlob(SPEC) as never)
    mockSource()

    const wrapper = await renderDetail()
    const button = reviewButton(wrapper)!
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(button.attributes('aria-controls')).toBe('clip-review-31')

    await openReview(wrapper)

    const toggled = wrapper.findAll('button').find((b) => b.text().includes(REVIEW.hide))!
    expect(toggled.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('#clip-review-31').exists()).toBe(true)
  })
})

/**
 * 결과물이 붙은 클립의 1차 행동.
 *
 * 렌더 job 상태(`renderJobFor`)는 이 브라우저 세션에만 있다. 렌더를 걸어두고 새로고침하면
 * 사라지므로, 그것에 의존하면 "결과물이 있는데 아무 버튼도 없는" 화면이 된다.
 * 판정 근거는 서버가 매번 돌려주는 `clip.renderedVideoId` 여야 한다.
 *
 * 아래 테스트는 렌더를 **한 번도 시작하지 않은 채** 마운트한다. 즉 세션 상태가 비어 있는
 * 새로고침 직후와 같은 조건이다.
 */
describe('ShortsPipelineDetailView 결과물 1차 행동', () => {
  const DETAIL = koMessages.ugc.shorts.runs.detail

  const primaryButton = (wrapper: Wrapper, label: string) =>
    wrapper.findAll('button').find((b) => b.text().includes(label))

  it('연결된 영상이 있는 RENDERED 클립에 게시 준비 버튼을 보여준다', async () => {
    const wrapper = await renderDetail([
      clip({ status: 'RENDERED', renderedVideoId: 900 }),
    ])

    expect(primaryButton(wrapper, DETAIL.preparePublish)).toBeTruthy()
    expect(primaryButton(wrapper, DETAIL.publishStatus)).toBeFalsy()
  })

  it('예약·게시된 클립에는 게시 현황 문구를 쓴다', async () => {
    const scheduled = await renderDetail([
      clip({ status: 'SCHEDULED', renderedVideoId: 901 }),
    ])
    expect(primaryButton(scheduled, DETAIL.publishStatus)).toBeTruthy()
    expect(primaryButton(scheduled, DETAIL.preparePublish)).toBeFalsy()

    const published = await renderDetail([
      clip({ status: 'PUBLISHED', renderedVideoId: 902 }),
    ])
    expect(primaryButton(published, DETAIL.publishStatus)).toBeTruthy()
  })

  /* 연결된 영상이 없으면 갈 곳이 없다. 버튼을 띄우면 빈 화면으로 보낸다. */
  it('연결된 영상이 없는 클립에는 1차 행동 버튼이 없다', async () => {
    const wrapper = await renderDetail([
      clip({ status: 'RENDER_READY', renderedVideoId: null }),
    ])

    expect(primaryButton(wrapper, DETAIL.preparePublish)).toBeFalsy()
    expect(primaryButton(wrapper, DETAIL.publishStatus)).toBeFalsy()
  })

  /*
   * 렌더가 만든 영상은 DRAFT 로 저장되고, 영상 상세에는 게시·예약 실행 버튼이 없다.
   * 거기로 보내면 "게시 준비"가 실제 게시까지 한 단계를 더 숨긴다. 아직 게시 전인
   * RENDERED 는 게시를 실행할 수 있는 작성 화면으로 곧장 가야 한다.
   */
  it('게시 준비는 작성 화면으로 곧장 이동한다', async () => {
    const wrapper = await renderDetail([
      clip({ status: 'RENDERED', renderedVideoId: 900 }),
    ])
    const push = vi.spyOn(wrapper.vm.$router, 'push')

    await primaryButton(wrapper, DETAIL.preparePublish)!.trigger('click')

    expect(push).toHaveBeenCalledWith({ path: '/compose', query: { videoId: '900' } })
  })

  /* 이미 예약·게시된 뒤라 할 일이 실행이 아니라 확인이다. 그때는 영상 상세가 맞다. */
  it('게시 현황은 연결된 영상 상세로 이동한다', async () => {
    const wrapper = await renderDetail([
      clip({ status: 'SCHEDULED', renderedVideoId: 901 }),
    ])
    const push = vi.spyOn(wrapper.vm.$router, 'push')

    await primaryButton(wrapper, DETAIL.publishStatus)!.trigger('click')

    expect(push).toHaveBeenCalledWith({ name: 'video-detail', params: { id: 901 } })
  })

  /*
   * 서버 렌더가 꺼져 있어도(renderEnabled=false) 이미 만들어진 결과물로는 갈 수 있어야
   * 한다. 이 테스트 하네스의 getRenderAvailability 는 available=false 를 돌려주므로,
   * 위 테스트들이 통과한다는 것 자체가 그 독립성을 보인다. 여기서는 보조 행동인
   * 다운로드가 사라지지 않았는지만 확인한다.
   */
  it('1차 행동이 생겨도 보조 행동은 남는다', async () => {
    const wrapper = await renderDetail([
      clip({ status: 'RENDERED', renderedVideoId: 900 }),
    ])

    expect(primaryButton(wrapper, DETAIL.preparePublish)).toBeTruthy()
    expect(wrapper.text()).toContain(DETAIL.renderedAttached)
  })
})

/**
 * 대상별 게시 결과 표시.
 *
 * 서버의 `clip.status` 는 대상 **하나라도** 성공하면 SCHEDULED 가 된다. 그 원문을 그대로
 * 배지에 쓰면 3개 중 1개만 올라간 클립이 전부 성공한 것처럼 보인다.
 */
describe('ShortsPipelineDetailView 대상별 게시 결과', () => {
  const DETAIL = koMessages.ugc.shorts.runs.detail

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('실패가 섞이면 SCHEDULED 원문 대신 일부 실패로 표시한다', async () => {
    const wrapper = await renderDetail([
      clip({
        status: 'SCHEDULED',
        renderedVideoId: 900,
        publications: [
          publication('YOUTUBE#77'),
          publication('TIKTOK#88', 'FAILED', '채널 인증이 만료되었습니다'),
        ],
      }),
    ])

    expect(wrapper.text()).toContain(DETAIL.partiallyPublished)
    // 대상별 키와 상태가 각각 드러난다.
    expect(wrapper.text()).toContain('YOUTUBE#77')
    expect(wrapper.text()).toContain('TIKTOK#88')
    // 실패 원인이 보여야 무엇을 고칠지 안다.
    expect(wrapper.text()).toContain('채널 인증이 만료되었습니다')
  })

  it('건너뛴 대상도 실패로 취급해 원인을 보여준다', async () => {
    const wrapper = await renderDetail([
      clip({
        status: 'SCHEDULED',
        publications: [publication('YOUTUBE#77', 'SKIPPED', '렌더 영상 미연결')],
      }),
    ])

    expect(wrapper.text()).toContain(DETAIL.partiallyPublished)
    expect(wrapper.text()).toContain('렌더 영상 미연결')
  })

  /* 전부 성공이면 기존 의미가 유지돼야 한다 — 없는 실패를 만들어내면 안 된다. */
  it('모든 대상이 성공하면 기존 상태를 그대로 쓴다', async () => {
    const wrapper = await renderDetail([
      clip({
        status: 'SCHEDULED',
        publications: [publication('YOUTUBE#77'), publication('YOUTUBE#88', 'PUBLISHED')],
      }),
    ])

    expect(wrapper.text()).not.toContain(DETAIL.partiallyPublished)
    expect(wrapper.text()).toContain('SCHEDULED')
  })

  /* 렌더 전·게시 미요청 클립은 판단 근거가 없다. 기존 상태를 그대로 둔다. */
  it('게시 결과가 없으면 기존 상태만 보여준다', async () => {
    const wrapper = await renderDetail([clip({ status: 'RENDER_READY', publications: [] })])

    expect(wrapper.text()).not.toContain(DETAIL.partiallyPublished)
    expect(wrapper.text()).toContain('RENDER_READY')
  })

  /* 저장하는 곳이 없는 값을 만들어 "게시 완료"의 증거처럼 보여주면 안 된다. */
  it('외부 게시물 URL 을 만들어 보여주지 않는다', async () => {
    const wrapper = await renderDetail([
      clip({ status: 'SCHEDULED', publications: [publication('YOUTUBE#77')] }),
    ])

    expect(wrapper.html()).not.toContain('youtube.com/watch')
    expect(wrapper.html()).not.toContain('tiktok.com/@')
  })

  it('채널명이 있으면 기술적인 채널 키 대신 알아보기 쉬운 이름을 보여준다', async () => {
    const wrapper = await renderDetail([
      clip({ publications: [publication('YOUTUBE#77', 'PUBLISHED', null, '메인 채널')] }),
    ])

    expect(wrapper.text()).toContain('YouTube · 메인 채널')
    expect(wrapper.text()).not.toContain('YOUTUBE#77')
  })
})

/**
 * 예약 대상.
 *
 * 예전에는 플랫폼 3종을 하드코딩해 보여줬다. 연결하지 않은 플랫폼도 고를 수 있었고,
 * 차단은 비동기 게시 단계에서야 일어나 사용자는 성공 토스트를 본 **뒤에** 실패를 봤다.
 * 여기서 지키는 것은 "고를 수 없는 것은 보이지 않는다"와 "고른 것이 정확히 전달된다"다.
 */
describe('ShortsPipelineDetailView 예약 대상', () => {
  const SCHEDULE = koMessages.ugc.shorts.runs.schedule

  beforeEach(() => {
    vi.clearAllMocks()
  })

  const scheduleButton = (wrapper: Wrapper) =>
    wrapper.findAll('button').find((b) => b.text().includes(SCHEDULE.submit))

  const targetLabels = (wrapper: Wrapper) => wrapper.findAll('label').map((l) => l.text())

  it('연결되고 게시 가능한 채널만 대상으로 보여준다', async () => {
    const wrapper = await renderDetail([clip()], undefined, {
      channels: [
        activeChannel({ id: 11, platform: 'YOUTUBE', channelName: '메인 채널' }),
        activeChannel({ id: 12, platform: 'YOUTUBE', channelName: '서브 채널' }),
      ],
      capabilities: [capability('YOUTUBE')],
    })

    const labels = targetLabels(wrapper)
    // 같은 플랫폼 두 계정이 이름으로 구분돼 각각 나온다.
    expect(labels.some((t) => t.includes('메인 채널'))).toBe(true)
    expect(labels.some((t) => t.includes('서브 채널'))).toBe(true)
  })

  it('선택한 대상을 PLATFORM#channelId 형식 그대로 보낸다', async () => {
    const wrapper = await renderDetail([clip()], undefined, {
      channels: [activeChannel({ id: 12, platform: 'YOUTUBE', channelName: '서브 채널' })],
      capabilities: [capability('YOUTUBE')],
    })
    vi.mocked(ugcShortsPipelineApi.confirmSchedule).mockResolvedValue({} as never)

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('input[type="datetime-local"]').setValue('2026-09-01T10:00')
    await scheduleButton(wrapper)!.trigger('click')
    await flushPromises()

    expect(ugcShortsPipelineApi.confirmSchedule).toHaveBeenCalledWith(
      5,
      17,
      expect.objectContaining({ platforms: ['YOUTUBE#12'] }),
    )
  })

  /* 만료·해제 토큰은 서버가 게시를 거절한다. 고르게 두면 같은 함정이 남는다. */
  it('만료되거나 해제된 채널은 대상에서 뺀다', async () => {
    const wrapper = await renderDetail([clip()], undefined, {
      channels: [
        activeChannel({ id: 21, channelName: '만료 채널', tokenStatus: 'EXPIRED' }),
        activeChannel({ id: 22, channelName: '해제 채널', tokenStatus: 'DISCONNECTED' }),
      ],
      capabilities: [capability('YOUTUBE')],
    })

    expect(wrapper.text()).toContain(SCHEDULE.noConnectedTarget)
    expect(targetLabels(wrapper).some((t) => t.includes('만료 채널'))).toBe(false)
    expect(targetLabels(wrapper).some((t) => t.includes('해제 채널'))).toBe(false)
  })

  /* 이 배포가 올릴 수 없는 플랫폼은 연결돼 있어도 대상이 아니다. */
  it('업로드 능력이 없는 플랫폼은 대상에서 뺀다', async () => {
    const wrapper = await renderDetail([clip()], undefined, {
      channels: [activeChannel({ id: 31, platform: 'TIKTOK', channelName: '틱톡 계정' })],
      capabilities: [
        capability('TIKTOK', { directVideoUpload: false, cloudVideoUpload: false }),
      ],
    })

    expect(wrapper.text()).toContain(SCHEDULE.noConnectedTarget)
    expect(targetLabels(wrapper).some((t) => t.includes('틱톡 계정'))).toBe(false)
  })

  it('대상이 하나도 없으면 예약을 보내지 않는다', async () => {
    const wrapper = await renderDetail([clip()], undefined, {
      channels: [],
      capabilities: [capability('YOUTUBE')],
    })

    expect(wrapper.text()).toContain(SCHEDULE.noConnectedTarget)
    await scheduleButton(wrapper)!.trigger('click')
    await flushPromises()

    expect(ugcShortsPipelineApi.confirmSchedule).not.toHaveBeenCalled()
  })

  /* 조회 실패를 "채널 없음"으로 말하면, 실제로는 연결된 사용자에게 거짓말이 된다. */
  it('채널 조회에 실패하면 실패로 알리고 예약을 보내지 않는다', async () => {
    const wrapper = await renderDetail([clip()], undefined, { channelsFail: true })

    expect(wrapper.text()).toContain(SCHEDULE.targetsLoadFailed)
    expect(wrapper.text()).not.toContain(SCHEDULE.noConnectedTarget)
    await scheduleButton(wrapper)!.trigger('click')
    await flushPromises()

    expect(ugcShortsPipelineApi.confirmSchedule).not.toHaveBeenCalled()
  })
})
