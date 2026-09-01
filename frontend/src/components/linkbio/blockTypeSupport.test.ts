import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BlockTypeSelector from './BlockTypeSelector.vue'
import BioEditor from './BioEditor.vue'
import { PERSISTED_BLOCK_TYPES, isPersistedBlockType } from '@/types/linkbio'
import type { BioPage, BlockType } from '@/types/linkbio'

/**
 * **지원 범위를 화면이 정확히 말하는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 에디터는 링크·헤더·SNS·영상·구분선·텍스트 블록을 고를 수 있었지만, 서버는
 * `link_bio_links` 한 테이블만 있고 `savePage()` 도 링크 블록만 보낸다. 그래서
 * 비링크 블록은 **추가되고 저장 버튼도 성공한 뒤 새로고침하면 사라졌다** —
 * 되는 것처럼 보이는 기능이었다.
 *
 * 지금은 저장되는 종류만 고를 수 있고, 그 이유를 화면이 설명한다.
 * 규칙의 단일 출처는 `PERSISTED_BLOCK_TYPES` 다.
 */
describe('링크인바이오 블록 지원 범위', () => {
  const ALL_TYPES: BlockType[] = ['link', 'header', 'social', 'video', 'divider', 'text']

  // ── 단일 출처 ───────────────────────────────────────────────────────────

  it('저장되는 블록 종류는 링크뿐이다', () => {
    expect([...PERSISTED_BLOCK_TYPES]).toEqual(['link'])
  })

  it('저장 여부 판정이 목록과 일치한다', () => {
    for (const type of ALL_TYPES) {
      expect(isPersistedBlockType(type)).toBe(type === 'link')
    }
  })

  // ── 선택기 ──────────────────────────────────────────────────────────────

  /** **이 케이스가 유실될 블록을 고르게 하던 자리다.** */
  it('선택기는 저장되지 않는 종류를 노출하지 않는다', () => {
    const wrapper = mount(BlockTypeSelector)
    const labels = wrapper.findAll('button').map(b => b.text())

    expect(labels).toHaveLength(1)
    expect(labels[0]).toContain('링크')
    for (const label of ['헤더', 'SNS', '영상', '구분선', '텍스트']) {
      expect(wrapper.text()).not.toContain(label)
    }
  })

  /** 고를 수 있는 항목은 전부 실제로 저장되는 종류여야 한다. */
  it('선택기가 내보내는 종류는 모두 저장 가능한 종류다', async () => {
    const wrapper = mount(BlockTypeSelector)

    await wrapper.findAll('button')[0].trigger('click')

    const emitted = wrapper.emitted('select') as BlockType[][]
    expect(emitted).toHaveLength(1)
    expect(isPersistedBlockType(emitted[0][0])).toBe(true)
  })

  // ── 에디터 안내 ─────────────────────────────────────────────────────────

  const page = (): BioPage => ({
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
    blocks: [],
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  })

  function mountEditor() {
    return mount(BioEditor, {
      props: { page: page() },
      global: { stubs: { ThemeSelector: true, BlockTypeSelector: true, BioBlockItem: true } },
    })
  }

  /**
   * 선택기에 항목이 하나뿐인 것은 오류가 아니라 **현재 지원 범위**다.
   * 화면이 그것을 말하지 않으면 사용자는 기능이 깨진 것으로 읽는다.
   */
  it('에디터가 지원 범위를 사용자에게 설명한다', async () => {
    const wrapper = mountEditor()

    await wrapper.findAll('button').find(b => b.text().includes('블록 추가'))!.trigger('click')

    const text = wrapper.text()
    expect(text).toContain('링크')
    expect(text).toContain('준비 중')
  })

  /** 안내는 선택기를 열었을 때만 보인다 — 평소 화면을 어지럽히지 않는다. */
  it('선택기를 열기 전에는 안내를 띄우지 않는다', () => {
    expect(mountEditor().text()).not.toContain('준비 중')
  })
})
