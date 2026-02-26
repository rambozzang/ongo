import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SmartReplyRule, SmartReplySuggestion, SmartReplyStats, SmartReplyConfig } from '@/types/smartReply'
import { smartReplyApi } from '@/api/smartReply'

function generateMockSuggestions(): SmartReplySuggestion[] {
  return [
    {
      id: 's1', originalText: '영상 퀄리티가 점점 좋아지네요! 항상 응원합니다', originalAuthor: '팬_열혈',
      platform: 'youtube', context: 'COMMENT', sentiment: 'POSITIVE',
      suggestions: [
        { text: '감사합니다! 항상 응원해주셔서 힘이 나요 💪', tone: 'GRATEFUL', confidence: 0.95 },
        { text: '칭찬 감사합니다! 더 좋은 콘텐츠로 보답할게요 😊', tone: 'FRIENDLY', confidence: 0.88 },
      ],
      videoTitle: '일주일 브이로그', createdAt: new Date(Date.now() - 3600000).toISOString(),
    },
    {
      id: 's2', originalText: '음질이 좀 안좋은것 같아요', originalAuthor: 'viewer_22',
      platform: 'youtube', context: 'COMMENT', sentiment: 'NEGATIVE',
      suggestions: [
        { text: '피드백 감사합니다! 다음 영상에서는 음질 개선해보겠습니다 🎤', tone: 'PROFESSIONAL', confidence: 0.92 },
        { text: '알려주셔서 감사해요. 마이크 업그레이드 예정입니다!', tone: 'FRIENDLY', confidence: 0.85 },
      ],
      videoTitle: '새로운 도전 영상', createdAt: new Date(Date.now() - 7200000).toISOString(),
    },
    {
      id: 's3', originalText: '다음 영상은 언제 올라오나요?', originalAuthor: 'curious_fan',
      platform: 'instagram', context: 'DM', sentiment: 'NEUTRAL',
      suggestions: [
        { text: '이번 주 금요일에 새 영상 올라갑니다! 기대해주세요 ✨', tone: 'FRIENDLY', confidence: 0.90 },
        { text: '곧 업로드 예정입니다. 알림 설정 해주세요!', tone: 'CASUAL', confidence: 0.82 },
      ],
      createdAt: new Date(Date.now() - 14400000).toISOString(),
    },
    {
      id: 's4', originalText: '콜라보 제의드립니다. 관심 있으시면 연락 부탁드려요.', originalAuthor: 'brand_manager',
      platform: 'instagram', context: 'DM', sentiment: 'POSITIVE',
      suggestions: [
        { text: '관심 가져주셔서 감사합니다! 자세한 내용을 이메일로 보내주시면 검토하겠습니다.', tone: 'PROFESSIONAL', confidence: 0.93 },
        { text: '제안 감사합니다. 이메일 creator@example.com으로 상세 내용 보내주세요.', tone: 'PROFESSIONAL', confidence: 0.88 },
      ],
      createdAt: new Date(Date.now() - 28800000).toISOString(),
    },
  ]
}

function generateMockStats(): SmartReplyStats {
  return {
    totalRepliesSent: 1842,
    avgResponseTime: 12,
    sentimentBreakdown: { positive: 68, negative: 12, neutral: 20 },
    topKeywords: [
      { keyword: '감사', count: 245 },
      { keyword: '응원', count: 189 },
      { keyword: '다음영상', count: 132 },
      { keyword: '콜라보', count: 78 },
      { keyword: '음질', count: 45 },
    ],
    repliesByPlatform: [
      { platform: 'youtube', count: 1240 },
      { platform: 'instagram', count: 420 },
      { platform: 'tiktok', count: 182 },
    ],
    automatedPercentage: 35,
    satisfactionScore: 4.2,
  }
}

export const useSmartReplyStore = defineStore('smartReply', () => {
  const suggestions = ref<SmartReplySuggestion[]>([])
  const rules = ref<SmartReplyRule[]>([])
  const stats = ref<SmartReplyStats | null>(null)
  const config = ref<SmartReplyConfig | null>(null)
  const loading = ref(false)

  const pendingCount = computed(() => suggestions.value.length)
  const positiveRatio = computed(() => stats.value?.sentimentBreakdown.positive ?? 0)

  async function fetchSuggestions() {
    loading.value = true
    try {
      suggestions.value = await smartReplyApi.getSuggestions()
    } catch {
      suggestions.value = generateMockSuggestions()
    } finally {
      loading.value = false
    }
  }

  async function sendReply(suggestionId: string, text: string) {
    try { await smartReplyApi.sendReply(suggestionId, text) } catch { /* 로컬 */ }
    suggestions.value = suggestions.value.filter((s) => s.id !== suggestionId)
  }

  async function dismissSuggestion(id: string) {
    try { await smartReplyApi.dismissSuggestion(id) } catch { /* 로컬 */ }
    suggestions.value = suggestions.value.filter((s) => s.id !== id)
  }

  async function fetchRules() {
    try {
      rules.value = await smartReplyApi.getRules()
    } catch {
      rules.value = [
        { id: 1, name: '감사 인사 자동응답', isActive: true, context: 'COMMENT', triggerKeywords: ['감사', '고마워', '최고'], sentiment: 'POSITIVE', tone: 'GRATEFUL', templateText: '감사합니다! 항상 응원해주셔서 힘이 납니다 😊', useAi: false, autoSend: false, replyCount: 342 },
        { id: 2, name: '업로드 문의 응답', isActive: true, context: 'COMMENT', triggerKeywords: ['다음영상', '언제', '올라오'], tone: 'FRIENDLY', templateText: '', useAi: true, autoSend: false, replyCount: 128 },
        { id: 3, name: '비즈니스 제안 응답', isActive: true, context: 'DM', triggerKeywords: ['콜라보', '제안', '광고', '협업'], tone: 'PROFESSIONAL', templateText: '', useAi: true, autoSend: false, replyCount: 56 },
      ]
    }
  }

  async function fetchStats() {
    try {
      stats.value = await smartReplyApi.getStats()
    } catch {
      stats.value = generateMockStats()
    }
  }

  async function fetchConfig() {
    try {
      config.value = await smartReplyApi.getConfig()
    } catch {
      config.value = { defaultTone: 'FRIENDLY', enableAutoReply: false, maxAutoRepliesPerDay: 50, excludeKeywords: [], replyDelay: 5, platforms: ['youtube', 'instagram', 'tiktok'] }
    }
  }

  async function createRule(rule: Omit<SmartReplyRule, 'id' | 'replyCount' | 'lastUsed'>) {
    try {
      const created = await smartReplyApi.createRule(rule)
      rules.value.push(created)
    } catch {
      rules.value.push({ ...rule, id: Date.now(), replyCount: 0 })
    }
  }

  async function deleteRule(id: number) {
    try { await smartReplyApi.deleteRule(id) } catch { /* 로컬 */ }
    rules.value = rules.value.filter((r) => r.id !== id)
  }

  async function toggleRule(id: number) {
    const rule = rules.value.find((r) => r.id === id)
    if (!rule) return
    rule.isActive = !rule.isActive
    try { await smartReplyApi.updateRule(id, { isActive: rule.isActive }) } catch { /* 로컬 */ }
  }

  return { suggestions, rules, stats, config, loading, pendingCount, positiveRatio, fetchSuggestions, sendReply, dismissSuggestion, fetchRules, fetchStats, fetchConfig, createRule, deleteRule, toggleRule }
})
