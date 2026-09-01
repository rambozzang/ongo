import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { linkBioApi } from '@/api/linkbio'
import { useLinkBioStore } from './linkbio'
import { useNotificationStore } from './notification'
import BioPreview from '@/components/linkbio/BioPreview.vue'
import PublicLinkBioView from '@/views/PublicLinkBioView.vue'
import { isValidLinkUrl } from '@/types/linkbio'
import type { BioPage, LinkBlock } from '@/types/linkbio'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { slug: 'creator' } }),
}))

vi.mock('@/api/linkbio', () => ({
  linkBioApi: {
    getPage: vi.fn(),
    updatePage: vi.fn(),
    updateLinks: vi.fn(),
    getPublicPage: vi.fn(),
    recordClick: vi.fn(),
  },
}))

/**
 * 링크 블록의 **유효하지 않은 URL 이 실제 링크처럼 저장·표시되는 것**을 막는 계약.
 *
 * ## 무엇이 거짓이었나
 *
 * 새 링크 블록의 기본값이 `'https://'` 였다. 프로토콜 문자열일 뿐 어디도 가리키지 않는데,
 * **백엔드는 `url: String` 을 그대로 저장한다 — 검증이 전혀 없다.** 그래서 사용자가
 * 주소를 입력하지 않고 저장해도 성공했고, 공개 페이지가 그 값을 `<a href>` 로 그려
 * 방문자를 아무 데도 아닌 곳으로 보냈다. 클릭 집계에도 그 클릭이 쌓였다.
 *
 * 지금은 빈 상태로 시작하고, 저장 전에 막고, 화면은 유효한 링크만 앵커로 만든다.
 */
describe('링크 바이오 URL 검증', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // `recordClick` 결과에 `.catch()` 를 붙이므로 Promise 를 돌려줘야 한다.
    vi.mocked(linkBioApi.recordClick).mockResolvedValue(undefined as never)
  })

  // ── 판정 함수 ───────────────────────────────────────────────────────────

  it('http/https 절대 URL 만 유효하다', () => {
    expect(isValidLinkUrl('https://example.com')).toBe(true)
    expect(isValidLinkUrl('http://example.com/path?q=1')).toBe(true)
  })

  /** **이 값들이 실제 링크처럼 저장되던 자리다.** */
  it('프로토콜만 있거나 비어 있으면 유효하지 않다', () => {
    for (const invalid of ['', '   ', 'https://', 'http://', null, undefined]) {
      expect(isValidLinkUrl(invalid)).toBe(false)
    }
  })

  /** 공개 페이지의 `href` 가 되므로 실행 스킴·상대 경로를 막는다. */
  it('http/https 가 아닌 스킴과 상대 경로는 유효하지 않다', () => {
    for (const invalid of ['javascript:alert(1)', 'ftp://example.com', '/내-페이지', 'example.com']) {
      expect(isValidLinkUrl(invalid)).toBe(false)
    }
  })

  // ── 스토어: 생성 기본값 ─────────────────────────────────────────────────

  const page = (blocks: LinkBlock[] = []): BioPage => ({
    id: '1',
    username: 'creator',
    displayName: '크리에이터',
    bio: '',
    avatarUrl: '',
    theme: 'minimal',
    backgroundColor: '#ffffff',
    textColor: '#000000',
    buttonColor: '#000000',
    buttonTextColor: '#ffffff',
    totalViews: 0,
    totalClicks: 0,
    blocks,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  })

  const linkBlock = (overrides: Partial<LinkBlock> = {}): LinkBlock => ({
    id: 1,
    type: 'link',
    title: '내 채널',
    url: 'https://example.com',
    isVisible: true,
    clickCount: 0,
    ...overrides,
  })

  function storeWith(blocks: LinkBlock[]) {
    const store = useLinkBioStore()
    store.bioPage = page(blocks)
    return store
  }

  /** **이 케이스가 `'https://'` 를 실제 주소처럼 넣던 자리다.** */
  it('새 링크 블록의 주소는 비어 있다', () => {
    const store = storeWith([])

    store.addBlock('link')

    const added = store.bioPage!.blocks[0] as LinkBlock
    expect(added.url).toBe('')
    expect(added.url).not.toBe('https://')
  })

  // ── 스토어: 저장 검증 ───────────────────────────────────────────────────

  it('주소가 비어 있으면 저장하지 않는다', async () => {
    const store = storeWith([linkBlock({ url: '' })])

    await expect(store.savePage()).rejects.toThrow()

    expect(linkBioApi.updateLinks).not.toHaveBeenCalled()
    expect(linkBioApi.updatePage).not.toHaveBeenCalled()
  })

  it('프로토콜만 있는 주소도 저장하지 않는다', async () => {
    const store = storeWith([linkBlock({ url: 'https://' })])

    await expect(store.savePage()).rejects.toThrow()

    expect(linkBioApi.updateLinks).not.toHaveBeenCalled()
  })

  /** 링크가 여러 개일 때 어디를 고쳐야 하는지 알 수 있어야 한다. */
  it('어느 링크가 문제인지 제목으로 알려준다', async () => {
    const spy = vi.spyOn(useNotificationStore(), 'error')
    const store = storeWith([
      linkBlock({ id: 1, title: '정상 링크', url: 'https://example.com' }),
      linkBlock({ id: 2, title: '주소 없는 링크', url: '' }),
    ])

    await expect(store.savePage()).rejects.toThrow()

    const message = spy.mock.calls[0][0]
    expect(message).toContain('주소 없는 링크')
    expect(message).not.toContain('정상 링크')
    expect(message).toContain('https://')
  })

  /** **기존 유효 URL 은 그대로 저장된다.** 과도한 차단 회귀를 막는다. */
  it('유효한 주소는 그대로 저장한다', async () => {
    vi.mocked(linkBioApi.updatePage).mockResolvedValue({} as never)
    vi.mocked(linkBioApi.updateLinks).mockResolvedValue({} as never)
    const store = storeWith([linkBlock({ url: 'https://example.com/page' })])

    await store.savePage()

    expect(linkBioApi.updateLinks).toHaveBeenCalledOnce()
    const sent = vi.mocked(linkBioApi.updateLinks).mock.calls[0][0]
    expect(sent[0].url).toBe('https://example.com/page')
    expect(store.isDirty).toBe(false)
  })

  /** 링크 블록이 하나도 없으면 검증할 것도 없다 — 프로필만 저장된다. */
  it('링크가 없으면 저장을 막지 않는다', async () => {
    vi.mocked(linkBioApi.updatePage).mockResolvedValue({} as never)
    vi.mocked(linkBioApi.updateLinks).mockResolvedValue({} as never)
    const store = storeWith([])

    await store.savePage()

    expect(linkBioApi.updatePage).toHaveBeenCalledOnce()
  })

  // ── 전화 미리보기 ───────────────────────────────────────────────────────

  function mountPreview(url: string) {
    return mount(BioPreview, { props: { page: page([linkBlock({ url })]) } })
  }

  /** 빈 href 는 현재 페이지로 이동하고, `https://` 는 아무 데도 아닌 곳으로 보낸다. */
  it('미리보기: 주소가 유효하지 않으면 앵커를 만들지 않는다', () => {
    for (const invalid of ['', 'https://']) {
      const wrapper = mountPreview(invalid)
      expect(wrapper.findAll('a')).toHaveLength(0)
      expect(wrapper.text()).toContain('주소 미입력')
    }
  })

  it('미리보기: 유효한 주소는 앵커로 그린다', () => {
    const wrapper = mountPreview('https://example.com')
    const anchor = wrapper.find('a')

    expect(anchor.exists()).toBe(true)
    expect(anchor.attributes('href')).toBe('https://example.com')
    expect(wrapper.text()).not.toContain('주소 미입력')
  })

  // ── 공개 페이지 (서버 데이터) ───────────────────────────────────────────

  async function mountPublic(links: { id: number; title: string; url: string }[]) {
    vi.mocked(linkBioApi.getPublicPage).mockResolvedValue({
      slug: 'creator',
      title: '크리에이터',
      bio: null,
      avatarUrl: null,
      theme: 'minimal',
      backgroundColor: '#ffffff',
      textColor: '#000000',
      buttonColor: '#000000',
      buttonTextColor: '#ffffff',
      links: links.map(l => ({ ...l, icon: null, sortOrder: 0 })),
    } as never)
    const wrapper = mount(PublicLinkBioView)
    await new Promise(resolve => setTimeout(resolve, 0))
    return wrapper
  }

  /** 검증 없이 저장된 옛 행이 남아 있을 수 있다 — 방어적으로 거른다. */
  it('공개 페이지: 유효하지 않은 주소의 링크는 그리지 않는다', async () => {
    const wrapper = await mountPublic([
      { id: 1, title: '깨진 링크', url: 'https://' },
      { id: 2, title: '정상 링크', url: 'https://example.com' },
    ])

    const anchors = wrapper.findAll('a')
    expect(anchors).toHaveLength(1)
    expect(anchors[0].attributes('href')).toBe('https://example.com')
    expect(wrapper.text()).not.toContain('깨진 링크')
  })

  /** **유효한 링크의 클릭 집계는 그대로 유지된다.** */
  it('공개 페이지: 유효한 링크의 클릭 집계는 그대로 동작한다', async () => {
    const wrapper = await mountPublic([{ id: 2, title: '정상 링크', url: 'https://example.com' }])

    await wrapper.find('a').trigger('click')

    expect(linkBioApi.recordClick).toHaveBeenCalledWith('creator', 2)
  })

  /** 그리지 않은 링크는 클릭될 수 없으므로 집계도 일어나지 않는다. */
  it('공개 페이지: 유효하지 않은 링크는 클릭 집계 대상이 아니다', async () => {
    const wrapper = await mountPublic([{ id: 1, title: '깨진 링크', url: '' }])

    expect(wrapper.findAll('a')).toHaveLength(0)
    expect(linkBioApi.recordClick).not.toHaveBeenCalled()
  })
})
