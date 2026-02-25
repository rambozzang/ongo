import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  ThumbnailTemplate,
  GeneratedThumbnail,
  ThumbnailGenerateRequest,
  ThumbnailHistory,
  ThumbnailStyle,
} from '@/types/thumbnailGenerator'
import { thumbnailGeneratorApi } from '@/api/thumbnailGenerator'

function generateMockTemplates(): ThumbnailTemplate[] {
  return [
    { id: 1, name: '볼드 텍스트', style: 'BOLD_TEXT', previewUrl: '', popularity: 95 },
    { id: 2, name: '미니멀리스트', style: 'MINIMALIST', previewUrl: '', popularity: 88 },
    { id: 3, name: '콜라주', style: 'COLLAGE', previewUrl: '', popularity: 72 },
    { id: 4, name: '얼굴 포커스', style: 'FACE_FOCUS', previewUrl: '', popularity: 91 },
    { id: 5, name: '시네마틱', style: 'CINEMATIC', previewUrl: '', popularity: 85 },
    { id: 6, name: '클릭베이트', style: 'CLICKBAIT', previewUrl: '', popularity: 78 },
  ]
}

function generateMockThumbnails(style: ThumbnailStyle): GeneratedThumbnail[] {
  return [
    { id: 1, imageUrl: '', style, ctrPrediction: 12.5, prompt: 'AI 생성 썸네일 A', createdAt: new Date().toISOString() },
    { id: 2, imageUrl: '', style, ctrPrediction: 10.8, prompt: 'AI 생성 썸네일 B', createdAt: new Date().toISOString() },
    { id: 3, imageUrl: '', style, ctrPrediction: 9.2, prompt: 'AI 생성 썸네일 C', createdAt: new Date().toISOString() },
    { id: 4, imageUrl: '', style, ctrPrediction: 11.3, prompt: 'AI 생성 썸네일 D', createdAt: new Date().toISOString() },
  ]
}

function generateMockHistory(): ThumbnailHistory[] {
  return [
    { id: 1, videoTitle: '서울 맛집 TOP 5', thumbnails: generateMockThumbnails('BOLD_TEXT'), selectedThumbnailId: 1, createdAt: new Date(Date.now() - 86400000 * 2).toISOString() },
    { id: 2, videoTitle: '주말 브이로그', thumbnails: generateMockThumbnails('MINIMALIST'), createdAt: new Date(Date.now() - 86400000 * 5).toISOString() },
  ]
}

export const useThumbnailGeneratorStore = defineStore('thumbnailGenerator', () => {
  const templates = ref<ThumbnailTemplate[]>([])
  const generatedThumbnails = ref<GeneratedThumbnail[]>([])
  const history = ref<ThumbnailHistory[]>([])
  const loading = ref(false)
  const generating = ref(false)

  async function fetchTemplates() {
    loading.value = true
    try {
      templates.value = await thumbnailGeneratorApi.getTemplates()
    } catch {
      templates.value = generateMockTemplates()
    } finally {
      loading.value = false
    }
  }

  async function generate(request: ThumbnailGenerateRequest) {
    generating.value = true
    try {
      const response = await thumbnailGeneratorApi.generate(request)
      generatedThumbnails.value = response.thumbnails
    } catch {
      generatedThumbnails.value = generateMockThumbnails(request.style)
    } finally {
      generating.value = false
    }
  }

  async function fetchHistory() {
    loading.value = true
    try {
      history.value = await thumbnailGeneratorApi.getHistory()
    } catch {
      history.value = generateMockHistory()
    } finally {
      loading.value = false
    }
  }

  async function selectThumbnail(historyId: number, thumbnailId: number) {
    try {
      await thumbnailGeneratorApi.selectThumbnail(historyId, thumbnailId)
    } catch {
      // 로컬 업데이트
    }
    const item = history.value.find((h) => h.id === historyId)
    if (item) item.selectedThumbnailId = thumbnailId
  }

  return {
    templates,
    generatedThumbnails,
    history,
    loading,
    generating,
    fetchTemplates,
    generate,
    fetchHistory,
    selectThumbnail,
  }
})
