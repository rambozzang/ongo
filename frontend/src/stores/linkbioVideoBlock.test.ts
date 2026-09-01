import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useLinkBioStore } from './linkbio'
import { useNotificationStore } from './notification'
import BioPreview from '@/components/linkbio/BioPreview.vue'
import type { BioPage, VideoBlock } from '@/types/linkbio'

/**
 * 링크인바이오 **영상 블록의 가짜 초기 콘텐츠**를 제거한 계약을 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 새 영상 블록의 `thumbnailUrl` 초기값이 **외부 랜덤 이미지 서비스 URL** 이었다.
 * 사용자가 영상을 지정하기도 전에 **남의 사진이 그 영상의 썸네일처럼** 미리보기에
 * 떴고, 공개 페이지에도 그대로 나갈 수 있었다.
 *
 * `videoUrl` 도 `'https://'` 라 값이 채워진 것처럼 보였지만 어떤 영상도 가리키지
 * 않았고, `<a href>` 에 들어가면 **현재 페이지로 이동**했다.
 *
 * 이제 둘 다 비어 있고, 화면은 로컬 placeholder 를 그린다.
 */
describe('링크인바이오 영상 블록', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  // ── 스토어: 저장되지 않는 종류는 추가되지 않는다 ────────────────────────
  //
  // 영상 블록은 서버가 저장하지 않는다(`link_bio_links` 한 테이블뿐). 예전에는 추가가
  // 되고 저장 버튼도 성공했지만 새로고침하면 사라졌다 — **되는 것처럼 보이는 기능**.
  // 이제 만들기 전에 막는다. 아래 미리보기 계약은 서버가 저장을 지원하게 됐을 때를
  // 위한 렌더링 규칙이므로 그대로 유지한다.

  function storeWithEmptyPage() {
    const store = useLinkBioStore()
    store.bioPage = page(videoBlock({ id: 0, isVisible: false }))
    store.bioPage.blocks = []
    return store
  }

  /** **이 케이스가 저장되지 않는 블록을 만들어 유실시키던 자리다.** */
  it('저장되지 않는 영상 블록은 추가되지 않는다', () => {
    const store = storeWithEmptyPage()

    store.addBlock('video')

    expect(store.bioPage!.blocks).toHaveLength(0)
  })

  /** 조용히 무시하면 사용자는 버튼이 고장 난 것으로 읽는다. 이유를 알려야 한다. */
  it('추가할 수 없는 이유를 사용자에게 알린다', () => {
    const notifications = useNotificationStore()
    const spy = vi.spyOn(notifications, 'error')
    const store = storeWithEmptyPage()

    store.addBlock('video')

    expect(spy).toHaveBeenCalledOnce()
    expect(spy.mock.calls[0][0]).toContain('링크')
  })

  /** 막았으므로 저장 대상도 늘지 않는다 — 더러워졌다고 표시하지 않는다. */
  it('추가가 막히면 저장 대기 상태로 만들지 않는다', () => {
    const store = storeWithEmptyPage()
    store.isDirty = false

    store.addBlock('video')

    expect(store.isDirty).toBe(false)
  })

  /** **링크 블록은 그대로 추가된다.** 과도한 차단 회귀를 막는다. */
  it('링크 블록은 정상적으로 추가된다', () => {
    const store = storeWithEmptyPage()

    store.addBlock('link')

    const added = store.bioPage!.blocks
    expect(added).toHaveLength(1)
    expect(added[0].type).toBe('link')
    expect(store.isDirty).toBe(true)
  })

  // ── 미리보기 ────────────────────────────────────────────────────────────

  const page = (block: VideoBlock): BioPage => ({
    id: '1',
    username: 'creator',
    displayName: '크리에이터',
    bio: '',
    avatarUrl: 'https://cdn.example.com/avatar.png',
    theme: 'minimal',
    backgroundColor: '#ffffff',
    textColor: '#000000',
    buttonColor: '#000000',
    buttonTextColor: '#ffffff',
    totalViews: 0,
    totalClicks: 0,
    blocks: [block],
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  })

  const videoBlock = (overrides: Partial<VideoBlock> = {}): VideoBlock => ({
    id: 1,
    type: 'video',
    title: '새 영상',
    videoUrl: '',
    thumbnailUrl: null,
    isVisible: true,
    ...overrides,
  })

  function mountPreview(block: VideoBlock) {
    return mount(BioPreview, { props: { page: page(block) } })
  }

  /** 썸네일이 없으면 img 를 그리지 않는다 — 아바타 img 만 남는다. */
  it('미리보기: 썸네일이 없으면 영상 img 를 그리지 않는다', () => {
    const wrapper = mountPreview(videoBlock())

    const srcs = wrapper.findAll('img').map(img => img.attributes('src'))
    expect(srcs).toEqual(['https://cdn.example.com/avatar.png'])
  })

  /** `src=''` 는 브라우저가 현재 페이지를 다시 내려받게 만든다. */
  it('미리보기: 빈 src 를 가진 img 를 남기지 않는다', () => {
    const imgs = mountPreview(videoBlock()).findAll('img')

    expect(imgs.every(img => (img.attributes('src') ?? '') !== '')).toBe(true)
  })

  it('미리보기: 썸네일이 없으면 로컬 placeholder 를 그린다', () => {
    const placeholder = mountPreview(videoBlock()).find('[role="img"]')

    expect(placeholder.exists()).toBe(true)
    expect(placeholder.attributes('aria-label')).toBe('새 영상')
    // 로컬 SVG — 외부 요청이 없다.
    expect(placeholder.find('svg').exists()).toBe(true)
  })

  /** 빈 `href` 는 현재 페이지로 이동한다 — 앵커 자체를 만들지 않는다. */
  it('미리보기: 영상 링크가 없으면 빈 앵커를 만들지 않다', () => {
    const hrefs = mountPreview(videoBlock())
      .findAll('a')
      .map(a => a.attributes('href') ?? '')

    expect(hrefs.every(href => href !== '')).toBe(true)
    expect(hrefs).not.toContain('https://')
  })

  // ── 실제 값은 그대로 ────────────────────────────────────────────────────

  /** **실제 썸네일·영상 URL 은 그대로 렌더링된다.** 과도한 차단 회귀를 막는다. */
  it('미리보기: 실제 썸네일과 영상 링크를 그대로 렌더링한다', () => {
    const thumb = 'https://cdn.example.com/thumb.jpg'
    const url = 'https://youtube.com/watch?v=abc'
    const wrapper = mountPreview(videoBlock({ thumbnailUrl: thumb, videoUrl: url }))

    const srcs = wrapper.findAll('img').map(img => img.attributes('src'))
    expect(srcs).toContain(thumb)

    const anchor = wrapper.findAll('a').find(a => a.attributes('href') === url)
    expect(anchor).toBeDefined()
    expect(anchor!.attributes('target')).toBe('_blank')
    expect(anchor!.attributes('rel')).toBe('noopener noreferrer')
  })

  /** 썸네일만 있고 링크가 아직 없는 중간 상태도 깨지지 않는다. */
  it('미리보기: 썸네일만 있으면 이미지는 그리되 앵커는 만들지 않는다', () => {
    const thumb = 'https://cdn.example.com/thumb.jpg'
    const wrapper = mountPreview(videoBlock({ thumbnailUrl: thumb }))

    expect(wrapper.findAll('img').map(img => img.attributes('src'))).toContain(thumb)
    expect(wrapper.findAll('a').every(a => (a.attributes('href') ?? '') !== '')).toBe(true)
  })
})
