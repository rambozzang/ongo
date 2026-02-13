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

const STORAGE_KEY_HISTORY = 'ongo_ai_history'
const STORAGE_KEY_PRESETS = 'ongo_ai_presets'

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
        id: 'preset-4',
        name: '썸네일 아이디어',
        toolType: 'ideas',
        prompt: '썸네일 디자인 아이디어를 제안해주세요. 클릭률을 높일 수 있는 구도와 텍스트 배치를 포함해주세요.',
        description: '클릭률 높은 썸네일 디자인 아이디어',
        isDefault: true,
      },
      {
        id: 'preset-5',
        name: '영상 스크립트 아웃라인',
        toolType: 'ideas',
        prompt: '10분 분량의 영상 스크립트 아웃라인을 작성해주세요. 도입-전개-결말 구조로 시청자 몰입도를 고려해주세요.',
        description: '영상 스크립트 구조 아웃라인',
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
    savePresetsToStorage()
  }

  // Load from localStorage
  function loadFromStorage() {
    try {
      const storedHistory = localStorage.getItem(STORAGE_KEY_HISTORY)
      const storedPresets = localStorage.getItem(STORAGE_KEY_PRESETS)

      if (storedHistory) {
        history.value = JSON.parse(storedHistory)
      }

      if (storedPresets) {
        presets.value = JSON.parse(storedPresets)
      } else {
        initializeDefaultPresets()
      }
    } catch (e) {
      console.error('Failed to load AI history from storage:', e)
      initializeDefaultPresets()
    }
  }

  // Save to localStorage
  function saveHistoryToStorage() {
    try {
      localStorage.setItem(STORAGE_KEY_HISTORY, JSON.stringify(history.value))
    } catch (e) {
      console.error('Failed to save AI history to storage:', e)
    }
  }

  function savePresetsToStorage() {
    try {
      localStorage.setItem(STORAGE_KEY_PRESETS, JSON.stringify(presets.value))
    } catch (e) {
      console.error('Failed to save AI presets to storage:', e)
    }
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
    saveHistoryToStorage()
  }

  function removeRecord(id: string) {
    history.value = history.value.filter(r => r.id !== id)
    saveHistoryToStorage()
  }

  function toggleFavorite(id: string) {
    const record = history.value.find(r => r.id === id)
    if (record) {
      record.isFavorite = !record.isFavorite
      saveHistoryToStorage()
    }
  }

  function clearHistory() {
    history.value = []
    saveHistoryToStorage()
  }

  // Preset actions
  function addPreset(preset: Omit<AiPreset, 'id' | 'isDefault'>) {
    const newPreset: AiPreset = {
      ...preset,
      id: `preset-${Date.now()}`,
      isDefault: false,
    }
    presets.value.push(newPreset)
    savePresetsToStorage()
  }

  function updatePreset(id: string, updates: Partial<Omit<AiPreset, 'id' | 'isDefault'>>) {
    const preset = presets.value.find(p => p.id === id)
    if (preset && !preset.isDefault) {
      Object.assign(preset, updates)
      savePresetsToStorage()
    }
  }

  function removePreset(id: string) {
    const preset = presets.value.find(p => p.id === id)
    if (preset && !preset.isDefault) {
      presets.value = presets.value.filter(p => p.id !== id)
      savePresetsToStorage()
    }
  }

  // Initialize on store creation
  loadFromStorage()

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
