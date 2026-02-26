import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { scriptWriterApi } from '@/api/scriptWriter'
import type { Script, ScriptTemplate, ScriptSummary, GenerateScriptRequest } from '@/types/scriptWriter'

function generateMockScripts(): Script[] {
  return [
    { id: 1, title: '유튜브 성장 비법 TOP 5', topic: '유튜브 성장', format: 'LONG_FORM', tone: 'EDUCATIONAL', status: 'FINAL', targetDuration: 600, estimatedWordCount: 1500, content: [{ id: 1, type: 'HOOK', title: '오프닝 훅', content: '여러분, 구독자 1만 달성하는데 평균 몇 년이 걸리는지 아시나요?', duration: 15, orderNumber: 1 }, { id: 2, type: 'INTRO', title: '인트로', content: '안녕하세요, 오늘은 제가 직접 테스트한 유튜브 성장 전략을 공유합니다.', duration: 30, orderNumber: 2 }], keywords: ['유튜브', '성장', '구독자'], targetAudience: '초보 유튜버', creditCost: 3, createdAt: '2025-02-20', updatedAt: '2025-02-20' },
    { id: 2, title: '아이폰 16 리뷰 스크립트', topic: '아이폰 16 리뷰', format: 'REVIEW', tone: 'PROFESSIONAL', status: 'DRAFT', targetDuration: 480, estimatedWordCount: 1200, content: [{ id: 3, type: 'HOOK', title: '오프닝', content: '아이폰 16, 정말 살 가치가 있을까요?', duration: 10, orderNumber: 1 }], keywords: ['아이폰', '리뷰', '테크'], targetAudience: '테크 관심자', creditCost: 3, createdAt: '2025-02-22', updatedAt: '2025-02-23' },
    { id: 3, title: '일주일 브이로그', topic: '일상 브이로그', format: 'VLOG', tone: 'CASUAL', status: 'REVIEWING', targetDuration: 300, estimatedWordCount: 800, content: [], keywords: ['브이로그', '일상'], targetAudience: '10-20대', creditCost: 2, createdAt: '2025-02-24', updatedAt: '2025-02-25' },
  ]
}

function generateMockSummary(): ScriptSummary {
  return { totalScripts: 12, drafts: 3, finals: 8, totalCreditsUsed: 36, avgDuration: 450, favoriteFormat: 'LONG_FORM' }
}

export const useScriptWriterStore = defineStore('scriptWriter', () => {
  const scripts = ref<Script[]>([])
  const templates = ref<ScriptTemplate[]>([])
  const summary = ref<ScriptSummary | null>(null)
  const selectedScript = ref<Script | null>(null)
  const isLoading = ref(false)
  const isGenerating = ref(false)

  const draftScripts = computed(() => scripts.value.filter((s) => s.status === 'DRAFT'))
  const finalScripts = computed(() => scripts.value.filter((s) => s.status === 'FINAL'))

  async function fetchScripts(status?: string) {
    isLoading.value = true
    try {
      scripts.value = await scriptWriterApi.getScripts(status)
    } catch {
      scripts.value = generateMockScripts()
      if (status) scripts.value = scripts.value.filter((s) => s.status === status)
    } finally {
      isLoading.value = false
    }
  }

  async function fetchSummary() {
    try { summary.value = await scriptWriterApi.getSummary() } catch { summary.value = generateMockSummary() }
  }

  async function generateScript(request: GenerateScriptRequest) {
    isGenerating.value = true
    try {
      const created = await scriptWriterApi.generateScript(request)
      scripts.value.unshift(created)
      return created
    } catch {
      const mock: Script = { id: Date.now(), title: request.topic, topic: request.topic, format: request.format, tone: request.tone, status: 'DRAFT', targetDuration: request.targetDuration, estimatedWordCount: Math.round(request.targetDuration * 2.5), content: [{ id: Date.now(), type: 'HOOK', title: '오프닝 훅', content: 'AI가 생성한 오프닝 문구입니다.', duration: 15, orderNumber: 1 }, { id: Date.now() + 1, type: 'BODY', title: '본문', content: '여기에 본문 내용이 들어갑니다.', duration: request.targetDuration - 30, orderNumber: 2 }, { id: Date.now() + 2, type: 'OUTRO', title: '아웃트로', content: '시청해 주셔서 감사합니다!', duration: 15, orderNumber: 3 }], keywords: request.keywords ?? [], targetAudience: request.targetAudience ?? '', creditCost: 3, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
      scripts.value.unshift(mock)
      return mock
    } finally {
      isGenerating.value = false
    }
  }

  async function deleteScript(id: number) {
    try { await scriptWriterApi.deleteScript(id) } catch { /* fallback */ }
    scripts.value = scripts.value.filter((s) => s.id !== id)
  }

  return { scripts, templates, summary, selectedScript, isLoading, isGenerating, draftScripts, finalScripts, fetchScripts, fetchSummary, generateScript, deleteScript }
})
