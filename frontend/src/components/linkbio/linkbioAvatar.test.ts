import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BioPreview from './BioPreview.vue'
import BioEditor from './BioEditor.vue'
import PublicLinkBioView from '@/views/PublicLinkBioView.vue'
import { linkBioApi } from '@/api/linkbio'
import type { BioPage } from '@/types/linkbio'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { slug: 'creator' } }),
}))

vi.mock('@/api/linkbio', () => ({
  linkBioApi: { getPublicPage: vi.fn(), trackClick: vi.fn() },
}))

/**
 * 링크인바이오 **프로필 이미지가 없을 때** 빈 `img` 를 남기지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 스토어는 서버에 아바타가 없으면 빈 문자열을 넣는데, 편집 화면과 전화 미리보기는
 * 그것을 그대로 `<img :src>` 에 넣었다. **빈 `src` 는 브라우저가 현재 페이지를 다시
 * 요청**하게 만들고(불필요한 트래픽·깨진 이미지 아이콘), 화면에는 깨진 자리가 남았다.
 *
 * 공개 페이지는 이미 `v-if` 로 막고 있었지만 **아무것도 그리지 않아** 세 화면의 모양이
 * 서로 달랐다. 이제 셋 다 로컬 아이콘 placeholder 를 그린다.
 */
describe('링크인바이오 프로필 이미지', () => {
  const page = (avatarUrl: string): BioPage => ({
    id: '1',
    username: 'creator',
    displayName: '크리에이터',
    bio: '',
    avatarUrl,
    theme: 'minimal',
    backgroundColor: '#ffffff',
    textColor: '#000000',
    buttonColor: '#000000',
    buttonTextColor: '#ffffff',
    totalViews: 0,
    totalClicks: 0,
    blocks: [],
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  })

  const REAL_AVATAR = 'https://cdn.example.com/avatar.png'

  // ── 전화 미리보기 (BioPreview) ──────────────────────────────────────────

  /** **이 케이스가 빈 src 로 현재 페이지를 다시 요청하게 만들던 자리다.** */
  it('전화 미리보기: 아바타가 없으면 img 를 그리지 않는다', () => {
    const wrapper = mount(BioPreview, { props: { page: page('') } })

    expect(wrapper.findAll('img')).toHaveLength(0)
  })

  it('전화 미리보기: 아바타가 없으면 접근 가능한 placeholder 를 그린다', () => {
    const placeholder = mount(BioPreview, { props: { page: page('') } }).find('[role="img"]')

    expect(placeholder.exists()).toBe(true)
    expect(placeholder.attributes('aria-label')).toBe('크리에이터')
    // 로컬 SVG — 외부 요청이 없다.
    expect(placeholder.find('svg').exists()).toBe(true)
  })

  /** **실제 URL 은 그대로 렌더링된다.** 과도한 차단 회귀를 막는다. */
  it('전화 미리보기: 실제 아바타 URL 은 그대로 렌더링한다', () => {
    const img = mount(BioPreview, { props: { page: page(REAL_AVATAR) } }).find('img')

    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(REAL_AVATAR)
    expect(img.attributes('alt')).toBe('크리에이터')
  })

  // ── 편집 화면 (BioEditor) ───────────────────────────────────────────────

  function mountEditor(avatarUrl: string) {
    return mount(BioEditor, {
      props: { page: page(avatarUrl) },
      global: { stubs: { ThemeSelector: true, BlockTypeSelector: true, BioBlockItem: true } },
    })
  }

  it('편집 화면: 아바타가 없으면 img 를 그리지 않는다', () => {
    expect(mountEditor('').findAll('img')).toHaveLength(0)
  })

  it('편집 화면: 아바타가 없으면 접근 가능한 placeholder 를 그린다', () => {
    const placeholder = mountEditor('').find('[role="img"]')

    expect(placeholder.exists()).toBe(true)
    expect(placeholder.attributes('aria-label')).toBe('크리에이터')
    expect(placeholder.find('svg').exists()).toBe(true)
  })

  it('편집 화면: 실제 아바타 URL 은 그대로 렌더링한다', () => {
    const img = mountEditor(REAL_AVATAR).find('img')

    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(REAL_AVATAR)
  })

  // ── 공개 페이지 (PublicLinkBioView) ─────────────────────────────────────

  async function mountPublic(avatarUrl: string | null) {
    vi.mocked(linkBioApi.getPublicPage).mockResolvedValue({
      slug: 'creator',
      title: '크리에이터',
      bio: null,
      avatarUrl,
      theme: 'minimal',
      backgroundColor: '#ffffff',
      textColor: '#000000',
      buttonColor: '#000000',
      buttonTextColor: '#ffffff',
      links: [],
    } as never)
    const wrapper = mount(PublicLinkBioView)
    await flushPromises()
    return wrapper
  }

  it('공개 페이지: 아바타가 없으면 img 를 그리지 않는다', async () => {
    const wrapper = await mountPublic(null)

    expect(wrapper.findAll('img')).toHaveLength(0)
  })

  /** 공개 페이지도 편집·전화 미리보기와 같은 placeholder 를 그린다. */
  it('공개 페이지: 아바타가 없으면 접근 가능한 placeholder 를 그린다', async () => {
    const placeholder = (await mountPublic(null)).find('[role="img"]')

    expect(placeholder.exists()).toBe(true)
    expect(placeholder.attributes('aria-label')).toBe('크리에이터')
    expect(placeholder.find('svg').exists()).toBe(true)
  })

  it('공개 페이지: 실제 아바타 URL 은 그대로 렌더링한다', async () => {
    const img = (await mountPublic(REAL_AVATAR)).find('img')

    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(REAL_AVATAR)
  })

  // ── 공통 ────────────────────────────────────────────────────────────────

  /** 세 화면 어디에도 빈 `src` 가 남으면 안 된다. */
  it('세 화면 모두 빈 src 를 가진 img 를 남기지 않는다', async () => {
    const wrappers = [
      mount(BioPreview, { props: { page: page('') } }),
      mountEditor(''),
      await mountPublic(null),
    ]

    for (const wrapper of wrappers) {
      const imgs = wrapper.findAll('img')
      expect(imgs.every(img => (img.attributes('src') ?? '') !== '')).toBe(true)
    }
  })
})
