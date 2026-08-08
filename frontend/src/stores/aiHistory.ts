import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AiUsageRecord {
  id: string
  toolType: string
  prompt: string
  result: string
  creditsUsed: number
  createdAt: string
  isFavorite: boolean
}

export interface AiPreset {
  id: string
  name: string
  toolType: string
  prompt: string
  description: string
  isDefault: boolean
}

export const useAiHistoryStore = defineStore('aiHistory', () => {
  const history = ref<AiUsageRecord[]>([])
  const presets = ref<AiPreset[]>([])

  // Initialize with default presets
  function initializeDefaultPresets() {
    const defaultPresets: AiPreset[] = [
      {
        id: 'preset-1',
        name: '유튜브 SEO 최적화 제목',
        toolType: 'meta',
        prompt: '유튜브 알고리즘을 고려한 클릭 유도형 제목을 생성해주세요. 검색 키워드를 포함하고 15초 이내에 핵심을 전달할 수 있는 제목이 필요합니다.',
        description: '유튜브 SEO에 최적화된 클릭 유도형 제목',
        isDefault: true,
      },
      {
        id: 'preset-2',
        name: '틱톡 트렌드 설명',
        toolType: 'meta',
        prompt: '틱톡 트렌드를 반영한 짧고 임팩트 있는 설명을 작성해주세요. 해시태그 챌린지와 연결할 수 있는 내용으로 부탁합니다.',
        description: '틱톡 트렌드 기반 임팩트 있는 설명',
        isDefault: true,
      },
      {
        id: 'preset-3',
        name: '해시태그 추천',
        toolType: 'hashtags',
        prompt: '인기 브이로그 콘텐츠',
        description: '일상 브이로그용 인기 해시태그 추천',
        isDefault: true,
      },
      {
        id: 'preset-6',
        name: '주간 성과 분석',
        toolType: 'report',
        prompt: '최근 7일간의 채널 성과를 분석하고 개선 방향을 제안해주세요.',
        description: '주간 채널 성과 분석 및 개선안',
        isDefault: true,
      },
    ]

    presets.value = defaultPresets
  }

  // History actions
  function addRecord(record: Omit<AiUsageRecord, 'id' | 'createdAt' | 'isFavorite'>) {
    const newRecord: AiUsageRecord = {
      ...record,
      id: `history-${Date.now()}`,
      createdAt: new Date().toISOString(),
      isFavorite: false,
    }
    history.value.unshift(newRecord) // Add to the beginning
  }

  function removeRecord(id: string) {
    history.value = history.value.filter(r => r.id !== id)
  }

  function toggleFavorite(id: string) {
    const record = history.value.find(r => r.id === id)
    if (record) {
      record.isFavorite = !record.isFavorite
    }
  }

  function clearHistory() {
    history.value = []
  }

  // Preset actions
  function addPreset(preset: Omit<AiPreset, 'id' | 'isDefault'>) {
    const newPreset: AiPreset = {
      ...preset,
      id: `preset-${Date.now()}`,
      isDefault: false,
    }
    presets.value.push(newPreset)
  }

  function updatePreset(id: string, updates: Partial<Omit<AiPreset, 'id' | 'isDefault'>>) {
    const preset = presets.value.find(p => p.id === id)
    if (preset && !preset.isDefault) {
      Object.assign(preset, updates)
    }
  }

  function removePreset(id: string) {
    const preset = presets.value.find(p => p.id === id)
    if (preset && !preset.isDefault) {
      presets.value = presets.value.filter(p => p.id !== id)
    }
  }

  // History and presets are server-owned features. Keep only the current session
  // until the corresponding API endpoints are connected; never fake persistence.
  initializeDefaultPresets()

  return {
    history,
    presets,
    addRecord,
    removeRecord,
    toggleFavorite,
    clearHistory,
    addPreset,
    updatePreset,
    removePreset,
  }
})
