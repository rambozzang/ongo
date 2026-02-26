import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { contentLibraryApi } from '@/api/contentLibrary'
import type { LibraryItem, LibraryFolder, ContentLibrarySummary } from '@/types/contentLibrary'

export const useContentLibraryStore = defineStore('contentLibrary', () => {
  const items = ref<LibraryItem[]>([])
  const folders = ref<LibraryFolder[]>([])
  const summary = ref<ContentLibrarySummary | null>(null)
  const loading = ref(false)

  const videoItems = computed(() => items.value.filter(i => i.type === 'VIDEO'))
  const imageItems = computed(() => items.value.filter(i => i.type === 'IMAGE'))

  async function fetchItems(folderId?: number, type?: string) {
    loading.value = true
    try {
      items.value = await contentLibraryApi.getItems(folderId, type)
    } catch {
      items.value = [
        { id: 1, title: '봄 여행 브이로그.mp4', type: 'VIDEO', platform: 'YOUTUBE', thumbnailUrl: '', fileSize: 524288000, tags: ['여행', '브이로그'], folderId: 1, folderName: '여행 콘텐츠', uploadedAt: '2025-03-01T09:00:00Z', updatedAt: '2025-03-01T09:00:00Z' },
        { id: 2, title: '썸네일_디자인_v2.png', type: 'IMAGE', platform: 'YOUTUBE', thumbnailUrl: '', fileSize: 2048000, tags: ['썸네일', '디자인'], folderId: 2, folderName: '디자인 에셋', uploadedAt: '2025-03-02T10:00:00Z', updatedAt: '2025-03-02T10:00:00Z' },
        { id: 3, title: '배경음악_spring.mp3', type: 'AUDIO', platform: '', thumbnailUrl: '', fileSize: 8192000, tags: ['BGM', '봄'], folderId: null, folderName: null, uploadedAt: '2025-03-03T11:00:00Z', updatedAt: '2025-03-03T11:00:00Z' },
        { id: 4, title: '촬영 대본.pdf', type: 'DOCUMENT', platform: '', thumbnailUrl: '', fileSize: 512000, tags: ['대본'], folderId: 1, folderName: '여행 콘텐츠', uploadedAt: '2025-03-04T12:00:00Z', updatedAt: '2025-03-04T12:00:00Z' },
        { id: 5, title: 'Morning Routine 쇼츠.mp4', type: 'VIDEO', platform: 'TIKTOK', thumbnailUrl: '', fileSize: 104857600, tags: ['쇼츠', '루틴'], folderId: null, folderName: null, uploadedAt: '2025-03-05T13:00:00Z', updatedAt: '2025-03-05T13:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchFolders() {
    try {
      folders.value = await contentLibraryApi.getFolders()
    } catch {
      folders.value = [
        { id: 1, name: '여행 콘텐츠', parentId: null, itemCount: 12, color: '#3B82F6', createdAt: '2025-01-15T09:00:00Z' },
        { id: 2, name: '디자인 에셋', parentId: null, itemCount: 8, color: '#8B5CF6', createdAt: '2025-02-01T10:00:00Z' },
        { id: 3, name: '튜토리얼', parentId: null, itemCount: 15, color: '#10B981', createdAt: '2025-02-10T11:00:00Z' },
        { id: 4, name: '협찬 콘텐츠', parentId: null, itemCount: 5, color: '#F59E0B', createdAt: '2025-03-01T12:00:00Z' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentLibraryApi.getSummary()
    } catch {
      summary.value = { totalItems: 156, totalFolders: 12, totalSize: 15728640000, videoCount: 89, imageCount: 45 }
    }
  }

  async function deleteItem(id: number) {
    try {
      await contentLibraryApi.deleteItem(id)
      items.value = items.value.filter(i => i.id !== id)
    } catch {
      items.value = items.value.filter(i => i.id !== id)
    }
  }

  return { items, folders, summary, loading, videoItems, imageItems, fetchItems, fetchFolders, fetchSummary, deleteItem }
})
