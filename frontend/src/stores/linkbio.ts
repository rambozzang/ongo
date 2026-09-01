import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { linkBioApi } from '@/api/linkbio'
import { useNotificationStore } from '@/stores/notification'
import { isPersistedBlockType, isValidLinkUrl } from '@/types/linkbio'
import type { BioPage, BioBlock, ThemeStyle, BlockType, LinkBlock } from '@/types/linkbio'

export const useLinkBioStore = defineStore('linkbio', () => {
  const bioPage = ref<BioPage | null>(null)
  const isDirty = ref(false)
  const loading = ref(false)
  const loadError = ref<string | null>(null)

  const visibleBlocks = computed(() => {
    if (!bioPage.value) return []
    return bioPage.value.blocks.filter(block => block.isVisible)
  })

  const totalClicks = computed(() => {
    if (!bioPage.value) return 0
    return bioPage.value.blocks
      .filter((block): block is LinkBlock => block.type === 'link')
      .reduce((sum, block) => sum + block.clickCount, 0)
  })

  const publishUrl = computed(() => {
    if (!bioPage.value) return ''
    return `${window.location.origin}/bio/${bioPage.value.username}`
  })

  async function fetchPage() {
    loading.value = true
    loadError.value = null
    try {
      const data = await linkBioApi.getPage()
      if (data) {
        bioPage.value = {
          id: String(data.id),
          username: data.slug,
          displayName: data.title || '',
          bio: data.bio || '',
          avatarUrl: data.avatarUrl || '',
          theme: (data.theme as ThemeStyle) || 'minimal',
          backgroundColor: data.backgroundColor,
          textColor: data.textColor,
          buttonColor: data.buttonColor || '#000000',
          buttonTextColor: data.buttonTextColor || '#ffffff',
          totalViews: data.viewCount,
          totalClicks: 0,
          blocks: data.links.map((link) => ({
            id: link.id,
            type: 'link' as const,
            title: link.title,
            url: link.url,
            icon: link.icon || undefined,
            isVisible: link.isActive,
            clickCount: link.clickCount,
          })),
          createdAt: data.createdAt,
          updatedAt: data.updatedAt,
        }
      } else {
        // No page exists — keep null so UI can show "create page"
        bioPage.value = null
      }
    } catch (e) {
      loadError.value = e instanceof Error ? e.message : '링크 바이오를 불러오지 못했습니다.'
      useNotificationStore().error('링크 바이오 처리 중 오류가 발생했습니다')
    } finally {
      loading.value = false
    }
  }

  const updateProfile = (updates: Partial<Pick<BioPage, 'displayName' | 'bio' | 'avatarUrl'>>) => {
    if (!bioPage.value) return
    bioPage.value = { ...bioPage.value, ...updates, updatedAt: new Date().toISOString() }
    isDirty.value = true
  }

  const addBlock = (type: BlockType) => {
    if (!bioPage.value) return

    /*
     * **저장되지 않는 블록은 아예 만들지 않는다.**
     *
     * `savePage()` 는 링크 블록만 서버로 보내고(`b.type === 'link'`), 서버에도 나머지
     * 타입의 개념이 없다. 예전에는 여기서 블록을 만들어 화면에 붙였고, 저장 버튼도
     * 성공했다 — 그런데 새로고침하면 사라졌다. **되는 것처럼 보이는 기능**이었다.
     *
     * 만들기 전에 막고 이유를 알린다. 조용히 무시하면(예전 `image` 타입처럼 `default:
     * return`) 사용자는 버튼이 고장 난 것으로 읽는다.
     */
    if (!isPersistedBlockType(type)) {
      useNotificationStore().error(
        '이 블록 종류는 아직 저장할 수 없어 추가하지 않았습니다. 지금은 링크 블록만 지원합니다.',
      )
      return
    }

    const newId = Math.max(0, ...bioPage.value.blocks.map(b => b.id)) + 1

    /*
     * 위 가드가 `type` 을 `'link'` 로 좁히므로 여기서 만들 수 있는 블록은 하나뿐이다
     * (TypeScript 가 그것을 증명한다). 서버가 다른 종류를 저장하게 되면
     * `PERSISTED_BLOCK_TYPES` 를 늘리고 그때 분기를 추가한다.
     *
     * 저장되지 않는 종류의 생성 코드를 미리 남겨 두지 않는다 — 가드가 느슨해지는 순간
     * "저장된 것처럼 보이다 사라지는" 흐름이 조용히 되살아난다.
     */
    const newBlock: BioBlock = {
      id: newId,
      type: 'link',
      title: '새 링크',
      /*
       * **비어 있는 상태로 시작한다.** 예전 기본값 `'https://'` 는 프로토콜 문자열일
       * 뿐 어디도 가리키지 않는데, 백엔드가 URL 을 검증하지 않아 그대로 저장됐고
       * 공개 페이지가 그것을 링크로 그렸다. 입력 필드는 빈 문자열이 곧 미입력이다.
       */
      url: '',
      isVisible: true,
      clickCount: 0,
    }

    bioPage.value.blocks.push(newBlock)
    bioPage.value.updatedAt = new Date().toISOString()
    isDirty.value = true
  }

  const removeBlock = (blockId: number) => {
    if (!bioPage.value) return
    bioPage.value.blocks = bioPage.value.blocks.filter(b => b.id !== blockId)
    bioPage.value.updatedAt = new Date().toISOString()
    isDirty.value = true
  }

  const reorderBlock = (fromIndex: number, toIndex: number) => {
    if (!bioPage.value) return
    const blocks = [...bioPage.value.blocks]
    const [movedBlock] = blocks.splice(fromIndex, 1)
    blocks.splice(toIndex, 0, movedBlock)
    bioPage.value.blocks = blocks
    bioPage.value.updatedAt = new Date().toISOString()
    isDirty.value = true
  }

  const updateBlock = (blockId: number, updates: Partial<BioBlock>) => {
    if (!bioPage.value) return
    const index = bioPage.value.blocks.findIndex(b => b.id === blockId)
    if (index !== -1) {
      bioPage.value.blocks[index] = { ...bioPage.value.blocks[index], ...updates } as BioBlock
      bioPage.value.updatedAt = new Date().toISOString()
      isDirty.value = true
    }
  }

  const toggleBlockVisibility = (blockId: number) => {
    if (!bioPage.value) return
    const block = bioPage.value.blocks.find(b => b.id === blockId)
    if (block) {
      block.isVisible = !block.isVisible
      bioPage.value.updatedAt = new Date().toISOString()
      isDirty.value = true
    }
  }

  const changeTheme = (theme: ThemeStyle) => {
    if (!bioPage.value) return
    bioPage.value.theme = theme

    switch (theme) {
      case 'minimal':
        bioPage.value.backgroundColor = '#ffffff'
        bioPage.value.textColor = '#000000'
        bioPage.value.buttonColor = '#000000'
        bioPage.value.buttonTextColor = '#ffffff'
        break
      case 'rounded':
        bioPage.value.backgroundColor = '#f8f9fa'
        bioPage.value.textColor = '#212529'
        bioPage.value.buttonColor = '#0d6efd'
        bioPage.value.buttonTextColor = '#ffffff'
        break
      case 'gradient':
        bioPage.value.backgroundColor = '#667eea'
        bioPage.value.textColor = '#ffffff'
        bioPage.value.buttonColor = '#764ba2'
        bioPage.value.buttonTextColor = '#ffffff'
        break
      case 'dark':
        bioPage.value.backgroundColor = '#1a1a1a'
        bioPage.value.textColor = '#ffffff'
        bioPage.value.buttonColor = '#ffffff'
        bioPage.value.buttonTextColor = '#000000'
        break
      case 'colorful':
        bioPage.value.backgroundColor = '#fef3c7'
        bioPage.value.textColor = '#92400e'
        bioPage.value.buttonColor = '#f59e0b'
        bioPage.value.buttonTextColor = '#ffffff'
        break
    }

    bioPage.value.updatedAt = new Date().toISOString()
    isDirty.value = true
  }

  const updateColors = (colors: Partial<Pick<BioPage, 'backgroundColor' | 'textColor' | 'buttonColor' | 'buttonTextColor'>>) => {
    if (!bioPage.value) return
    bioPage.value = { ...bioPage.value, ...colors, updatedAt: new Date().toISOString() }
    isDirty.value = true
  }

  const savePage = async () => {
    if (!bioPage.value) return

    const linkBlocks = bioPage.value.blocks.filter((b): b is LinkBlock => b.type === 'link')

    /*
     * **유효하지 않은 링크는 저장하지 않는다.**
     *
     * 백엔드는 `url: String` 을 그대로 받아 저장한다 — 검증이 없다. 그래서 미입력
     * 상태나 `'https://'` 같은 값이 그대로 공개 페이지의 `<a href>` 가 됐다.
     *
     * 어느 링크가 문제인지 제목으로 짚어 준다. "저장 실패" 만 알리면 링크가 여러 개일 때
     * 사용자가 어디를 고쳐야 할지 알 수 없다.
     */
    const invalid = linkBlocks.filter(b => !isValidLinkUrl(b.url))
    if (invalid.length > 0) {
      const names = invalid.map(b => `'${b.title}'`).join(', ')
      useNotificationStore().error(
        `${names} 링크의 주소를 확인해주세요. http:// 또는 https:// 로 시작하는 주소가 필요합니다.`,
      )
      throw new Error('INVALID_LINK_URL')
    }

    try {
      await linkBioApi.updatePage({
        slug: bioPage.value.username,
        title: bioPage.value.displayName,
        bio: bioPage.value.bio,
        avatarUrl: bioPage.value.avatarUrl,
        theme: bioPage.value.theme,
        backgroundColor: bioPage.value.backgroundColor,
        textColor: bioPage.value.textColor,
        buttonColor: bioPage.value.buttonColor,
        buttonTextColor: bioPage.value.buttonTextColor,
      })
      await linkBioApi.updateLinks(linkBlocks.map((b, idx) => ({
        title: b.title,
        url: b.url,
        icon: b.icon,
        sortOrder: idx,
        isActive: b.isVisible,
      })))
    } catch (e) {
      useNotificationStore().error('링크 바이오 처리 중 오류가 발생했습니다')
      throw e
    }
    isDirty.value = false
  }

  return {
    bioPage,
    isDirty,
    loading,
    loadError,
    visibleBlocks,
    totalClicks,
    publishUrl,
    fetchPage,
    updateProfile,
    addBlock,
    removeBlock,
    reorderBlock,
    updateBlock,
    toggleBlockVisibility,
    changeTheme,
    updateColors,
    savePage,
  }
})
