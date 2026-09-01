import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { competitorApi } from '@/api/competitor'
import { useCompetitorStore } from '@/stores/competitor'
import AddCompetitorModal from './AddCompetitorModal.vue'
import CompetitorCard from './CompetitorCard.vue'
import koMessages from '@/locales/ko/common.json'
import type { Competitor } from '@/types/competitor'

vi.mock('@/api/competitor', () => ({
  competitorApi: {
    list: vi.fn(),
    benchmark: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
    sync: vi.fn(),
    trends: vi.fn(),
    lookup: vi.fn(),
  },
}))

/**
 * 경쟁자 추가 검색이 **재지 못한 구독자 수를 0 처럼 보여주지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버 어댑터가 `item.statistics?.subscriberCount?.toLongOrNull() ?: 0` 이었다. YouTube
 * 채널은 구독자 수를 **숨길 수 있고** 그때 그 필드가 응답에서 빠지는데, `?: 0` 이 자리를
 * 채워 미리보기에 **"구독자 0"** 이 떴다. 사용자가 그대로 추가하면 그 0 이 저장돼
 * 순위·평균·비교표에 관측값처럼 섞였다.
 *
 * 이제 서버가 `null` 을 주고, 화면은 "측정 불가"를 보여준다.
 */
const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'ko',
  messages: { ko: koMessages },
})

const notMeasured = koMessages.analyticsView.notMeasured

describe('경쟁자 추가 검색의 구독자 미측정', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // ── 추가 모달 미리보기 ──────────────────────────────────────────────────

  /**
   * 모달은 Teleport 로 `document.body` 에 그려진다 — `wrapper` 안에서는 찾을 수 없다.
   * 그래서 실제 DOM 을 직접 조작한다.
   */
  async function lookupPreview(
    subscriberCount: number | null,
    totalViews: number | null = 1000,
  ): Promise<string> {
    vi.mocked(competitorApi.lookup).mockResolvedValue({
      found: true,
      platformChannelId: 'UC_test',
      channelName: '경쟁 채널',
      channelUrl: 'https://youtube.com/@rival',
      subscriberCount,
      totalViews,
      videoCount: 12,
      requiresManualInput: false,
    } as never)

    mount(AddCompetitorModal, { props: { isOpen: true }, global: { plugins: [i18n] } })

    const dialog = document.body.querySelector('[role="dialog"]')!
    const input = dialog.querySelector('input') as HTMLInputElement
    input.value = 'https://youtube.com/@rival'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const button = Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.includes('채널 정보 불러오기'))!
    button.click()
    await flushPromises()

    return document.body.querySelector('[role="dialog"]')!.textContent ?? ''
  }

  /** **이 케이스가 "구독자 0" 을 그리던 자리다.** */
  it('구독자 수를 숨긴 채널은 미리보기에 0 을 그리지 않는다', async () => {
    const text = await lookupPreview(null)

    expect(text).toContain(notMeasured)
  })

  /** **응답이 실제로 0 을 주면 그것은 관측이다.** */
  it('측정된 0 구독자는 미리보기에 0 으로 그린다', async () => {
    const text = await lookupPreview(0)

    expect(text).not.toContain(notMeasured)
  })

  it('측정된 구독자 수는 미리보기에 그대로 그린다', async () => {
    const text = await lookupPreview(8000)

    expect(text).toContain('8.0K')
    expect(text).not.toContain(notMeasured)
  })

  // ── 총 조회수 미측정 ────────────────────────────────────────────────────
  //
  // 총 조회수는 **평균 조회수의 분자**다. 조회 응답에 `viewCount` 가 없을 때 0 으로
  // 채우면 영상 수가 있는 채널에서 `0 / n = 0` 이 계산돼 "평균 0회" 가 만들어진다.

  /*
   * 총 조회수는 경쟁자 화면에 **직접 표시되지 않는다** — 미리보기 카드는 구독자·영상 수만
   * 그리고, 프론트의 `Competitor` 타입에는 이 필드 자체가 없다. 사용자에게 드러나는 결과는
   * 여기서 파생되는 **평균 조회수**이므로 그쪽을 고정한다.
   */

  /** **분자를 모르면 평균도 만들지 않는다.** */
  it('총 조회수를 재지 못하면 평균 조회수를 만들지 않는다', async () => {
    vi.mocked(competitorApi.lookup).mockResolvedValue({
      found: true,
      platformChannelId: 'UC_test',
      channelName: '경쟁 채널',
      channelUrl: 'https://youtube.com/@rival',
      subscriberCount: 8000,
      totalViews: null,
      videoCount: 12,
      requiresManualInput: false,
    } as never)

    const wrapper = mount(AddCompetitorModal, { props: { isOpen: true }, global: { plugins: [i18n] } })
    const dialog = document.body.querySelector('[role="dialog"]')!
    const urlInput = dialog.querySelector('input') as HTMLInputElement
    urlInput.value = 'https://youtube.com/@rival'
    urlInput.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.includes('채널 정보 불러오기')) as HTMLButtonElement).click()
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.trim() === '추가') as HTMLButtonElement).click()
    await flushPromises()

    const added = wrapper.emitted('add')?.[0]?.[0] as { avgViews: number | null }

    expect(added.avgViews).toBeNull()
    expect(added.avgViews).not.toBe(0)
  })

  /** **응답이 실제로 0 을 주면 그것은 관측이다.** */
  it('측정된 0 총 조회수는 평균 0 으로 계산한다', async () => {
    vi.mocked(competitorApi.lookup).mockResolvedValue({
      found: true,
      platformChannelId: 'UC_test',
      channelName: '경쟁 채널',
      channelUrl: 'https://youtube.com/@rival',
      subscriberCount: 8000,
      totalViews: 0,
      videoCount: 12,
      requiresManualInput: false,
    } as never)

    const wrapper = mount(AddCompetitorModal, { props: { isOpen: true }, global: { plugins: [i18n] } })
    const dialog = document.body.querySelector('[role="dialog"]')!
    const urlInput = dialog.querySelector('input') as HTMLInputElement
    urlInput.value = 'https://youtube.com/@rival'
    urlInput.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.includes('채널 정보 불러오기')) as HTMLButtonElement).click()
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.trim() === '추가') as HTMLButtonElement).click()
    await flushPromises()

    const added = wrapper.emitted('add')?.[0]?.[0] as { avgViews: number | null }

    expect(added.avgViews).toBe(0)
  })

  // ── 수동 입력 ───────────────────────────────────────────────────────────
  //
  // 자동 조회를 지원하지 않는 플랫폼은 사용자가 직접 적는다. 예전에는 구독자 수 칸이
  // `ref<number>(0)` 이라 **0 이 채워진 채로 열렸고**, 모르는 사람이 그대로 저장하면
  // 실제로 0 명인 채널과 구분되지 않는 0 이 저장돼 순위·평균을 오염시켰다.

  /** 수동 입력 모드로 진입시킨 뒤, 구독자 칸에 [subscriberInput] 을 넣고 추가한다. */
  async function manualAdd(subscriberInput: string | null, videoInput: string | null = null) {
    vi.mocked(competitorApi.lookup).mockResolvedValue({
      found: false,
      requiresManualInput: true,
      message: '이 플랫폼은 채널 정보를 직접 입력해주세요.',
      subscriberCount: null,
      totalViews: 0,
      videoCount: 0,
    } as never)

    const wrapper = mount(AddCompetitorModal, {
      props: { isOpen: true },
      global: { plugins: [i18n] },
    })
    const dialog = document.body.querySelector('[role="dialog"]')!

    const setInput = (el: HTMLInputElement, value: string) => {
      el.value = value
      el.dispatchEvent(new Event('input'))
    }

    setInput(dialog.querySelector('input') as HTMLInputElement, 'https://example.com/@rival')
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.includes('채널 정보 불러오기')) as HTMLButtonElement).click()
    await flushPromises()

    // 수동 입력 칸: [0] 채널 URL, [1] 채널명, [2] 구독자 수, [3] 영상 수
    const inputs = Array.from(dialog.querySelectorAll('input')) as HTMLInputElement[]
    setInput(inputs[1], '수동 채널')
    if (subscriberInput !== null) setInput(inputs[2], subscriberInput)
    if (videoInput !== null) setInput(inputs[3], videoInput)
    await flushPromises()

    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.trim() === '추가') as HTMLButtonElement).click()
    await flushPromises()

    return wrapper.emitted('add')?.[0]?.[0] as { subscriberCount: number | null; videoCount: number | null; name: string }
  }

  /** **이 케이스가 "모른다" 를 0 으로 저장하던 자리다.** */
  it('구독자 수를 비워 두면 null 을 보낸다', async () => {
    const added = await manualAdd(null)

    expect(added.subscriberCount).toBeNull()
    expect(added.subscriberCount).not.toBe(0)
  })

  it('구독자 수에 공백만 넣어도 null 을 보낸다', async () => {
    const added = await manualAdd('   ')

    expect(added.subscriberCount).toBeNull()
  })

  /** **직접 적은 0 은 사용자의 주장이다.** 그대로 0 으로 보낸다. */
  it('구독자 수에 직접 0 을 적으면 0 을 보낸다', async () => {
    const added = await manualAdd('0')

    expect(added.subscriberCount).toBe(0)
  })

  /** 기존 정상 수동 추가 동작은 그대로다. */
  it('구독자 수를 적으면 그 숫자를 보낸다', async () => {
    const added = await manualAdd('1234')

    expect(added.subscriberCount).toBe(1234)
    expect(added.name).toBe('수동 채널')
  })

  /** **입력칸이 0 으로 채워진 채 열리면 안 된다** — 그것이 기본값 저장을 유도했다. */
  it('수동 입력 구독자 칸은 비어 있는 채로 열린다', async () => {
    vi.mocked(competitorApi.lookup).mockResolvedValue({
      found: false,
      requiresManualInput: true,
      message: '이 플랫폼은 채널 정보를 직접 입력해주세요.',
      subscriberCount: null,
      totalViews: 0,
      videoCount: 0,
    } as never)

    mount(AddCompetitorModal, { props: { isOpen: true }, global: { plugins: [i18n] } })
    const dialog = document.body.querySelector('[role="dialog"]')!
    const urlInput = dialog.querySelector('input') as HTMLInputElement
    urlInput.value = 'https://example.com/@rival'
    urlInput.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.includes('채널 정보 불러오기')) as HTMLButtonElement).click()
    await flushPromises()

    const subscriberInput = (Array.from(dialog.querySelectorAll('input')) as HTMLInputElement[])[2]

    expect(subscriberInput.value).toBe('')
    expect(subscriberInput.value).not.toBe('0')
  })

  // ── 수동 입력: 영상 수 ──────────────────────────────────────────────────
  //
  // 영상 수는 **평균 조회수의 분모**다. 모르는 값을 0 으로 저장하면 "영상 0개" 라는
  // 관측이 되고, 그 0 때문에 평균 조회수까지 "계산 불가" 로 바뀐다.

  /** **이 케이스가 "모른다" 를 0 으로 저장하던 자리다.** */
  it('영상 수를 비워 두면 null 을 보낸다', async () => {
    const added = await manualAdd(null, null)

    expect(added.videoCount).toBeNull()
    expect(added.videoCount).not.toBe(0)
  })

  /** **직접 적은 0 은 "영상이 없다" 는 주장이다.** */
  it('영상 수에 직접 0 을 적으면 0 을 보낸다', async () => {
    const added = await manualAdd(null, '0')

    expect(added.videoCount).toBe(0)
  })

  it('영상 수를 적으면 그 숫자를 보낸다', async () => {
    const added = await manualAdd('1234', '12')

    expect(added.videoCount).toBe(12)
    expect(added.subscriberCount).toBe(1234)
  })

  /** 두 칸 모두 0 으로 채워진 채 열리면 안 된다. */
  it('수동 입력 영상 수 칸은 비어 있는 채로 열린다', async () => {
    vi.mocked(competitorApi.lookup).mockResolvedValue({
      found: false,
      requiresManualInput: true,
      message: '이 플랫폼은 채널 정보를 직접 입력해주세요.',
      subscriberCount: null,
      totalViews: 0,
      videoCount: null,
    } as never)

    mount(AddCompetitorModal, { props: { isOpen: true }, global: { plugins: [i18n] } })
    const dialog = document.body.querySelector('[role="dialog"]')!
    const urlInput = dialog.querySelector('input') as HTMLInputElement
    urlInput.value = 'https://example.com/@rival'
    urlInput.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent?.includes('채널 정보 불러오기')) as HTMLButtonElement).click()
    await flushPromises()

    const videoInput = (Array.from(dialog.querySelectorAll('input')) as HTMLInputElement[])[3]

    expect(videoInput.value).toBe('')
    expect(videoInput.value).not.toBe('0')
  })

  // ── 카드 표시 ───────────────────────────────────────────────────────────

  const competitor = (overrides: Partial<Competitor> = {}): Competitor => ({
    id: 1,
    name: '경쟁 채널',
    channelUrl: '',
    platform: 'YOUTUBE',
    avatarUrl: null,
    subscriberCount: 10000,
    videoCount: 12,
    avgViews: 1500,
    avgEngagement: null,
    growthRate: null,
    lastVideoAt: '',
    addedAt: '',
    isTracking: true,
    ...overrides,
  })

  const mountCard = (data: Competitor) =>
    mount(CompetitorCard, { props: { competitor: data }, global: { plugins: [i18n] } })

  it('카드가 미측정 구독자 수를 0 으로 그리지 않는다', () => {
    const text = mountCard(competitor({ subscriberCount: null })).text()

    expect(text).toContain(`구독자${notMeasured}`)
    expect(text).not.toContain('구독자0')
  })

  /** 측정된 0 은 관측이므로 숫자로 그린다. */
  it('카드가 측정된 0 구독자를 숫자로 그린다', () => {
    const text = mountCard(competitor({ subscriberCount: 0 })).text()

    expect(text).toContain('구독자0')
    expect(text).not.toContain(`구독자${notMeasured}`)
  })

  // ── 스토어: 순위·평균 ───────────────────────────────────────────────────

  function storeWith(counts: (number | null)[]) {
    const store = useCompetitorStore()
    store.competitors = counts.map((subscriberCount, i) =>
      competitor({ id: i + 1, subscriberCount }),
    )
    return store
  }

  /** 모르는 값은 줄을 세울 수 없다 — 0 으로 취급하면 항상 꼴찌가 된다. */
  it('구독자 수를 모르는 경쟁사는 상위 목록에 넣지 않는다', () => {
    const store = storeWith([5000, null, 9000])

    expect(store.topCompetitors.map(c => c.subscriberCount)).toEqual([9000, 5000])
  })

  /** **측정된 값만 평균에 넣는다.** null 을 0 으로 더하면 평균이 낮아진다. */
  it('미측정 경쟁사를 구독자 평균에 넣지 않는다', () => {
    const store = storeWith([1000, null])

    expect(store.averageMetrics.avgSubscribers).toBe(1000)
  })

  it('구독자 수를 아무도 모르면 평균을 만들지 않는다', () => {
    const store = storeWith([null, null])

    expect(store.averageMetrics.avgSubscribers).toBeNull()
  })

  /** 순위 계산도 측정된 경쟁사만 상대한다. */
  it('내 순위는 측정된 경쟁사만 상대로 매긴다', async () => {
    const store = storeWith([9000, null])
    store.myStats.subscriberCount = 5000

    // 9000 > 5000 이므로 2위. null 경쟁사는 줄에 들어오지 않는다.
    expect(store.myRanking).toBe(2)
  })
})
