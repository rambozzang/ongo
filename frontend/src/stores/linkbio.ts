import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { linkBioApi } from '@/api/linkbio'
import type { BioPage, BioBlock, ThemeStyle, BlockType, LinkBlock } from '@/types/linkbio'

const STORAGE_KEY = 'ongo_linkbio_page'

const saveToStorage = (page: BioPage) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(page))
  } catch (error) {
    console.error('Failed to save to localStorage:', error)
  }
}

export const useLinkBioStore = defineStore('linkbio', () => {
  const bioPage = ref<BioPage | null>(null)
  const isDirty = ref(false)
  const loading = ref(false)

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
          buttonColor: bioPage.value?.buttonColor ?? '#000000',
          buttonTextColor: bioPage.value?.buttonTextColor ?? '#ffffff',
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
      console.error('Failed to fetch link bio page:', e)
      bioPage.value = null
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
    const newId = Math.max(0, ...bioPage.value.blocks.map(b => b.id)) + 1

    let newBlock: BioBlock

    switch (type) {
      case 'link':
        newBlock = {
          id: newId,
          type: 'link',
          title: '새 링크',
          url: 'https://',
          isVisible: true,
          clickCount: 0,
        }
        break
      case 'header':
        newBlock = {
          id: newId,
          type: 'header',
          text: '새 헤더',
          isVisible: true,
        }
        break
      case 'social':
        newBlock = {
          id: newId,
          type: 'social',
          platform: 'instagram',
          url: 'https://instagram.com/',
          isVisible: true,
        }
        break
      case 'video':
        newBlock = {
          id: newId,
          type: 'video',
          title: '새 영상',
          videoUrl: 'https://',
          thumbnailUrl: 'https://picsum.photos/400/225',
          isVisible: true,
        }
        break
      case 'divider':
        newBlock = {
          id: newId,
          type: 'divider',
          isVisible: true,
        }
        break
      case 'text':
        newBlock = {
          id: newId,
          type: 'text',
          content: '새 텍스트',
          isVisible: true,
        }
        break
      default:
        return
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
    try {
      const linkBlocks = bioPage.value.blocks.filter((b): b is LinkBlock => b.type === 'link')
      await linkBioApi.updatePage({
        slug: bioPage.value.username,
        title: bioPage.value.displayName,
        bio: bioPage.value.bio,
        avatarUrl: bioPage.value.avatarUrl,
        theme: bioPage.value.theme,
        backgroundColor: bioPage.value.backgroundColor,
        textColor: bioPage.value.textColor,
      })
      if (linkBlocks.length > 0) {
        await linkBioApi.updateLinks(linkBlocks.map((b, idx) => ({
          title: b.title,
          url: b.url,
          icon: b.icon,
          sortOrder: idx,
          isActive: b.isVisible,
        })))
      }
    } catch (e) {
      console.error('Failed to save link bio page:', e)
    }
    saveToStorage(bioPage.value)
    isDirty.value = false
  }

  return {
    bioPage,
    isDirty,
    loading,
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
