import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { VideoScript, ScriptSuggestion, VideoScriptAssistantSummary } from '@/types/videoScriptAssistant'
import { videoScriptAssistantApi } from '@/api/videoScriptAssistant'

export const useVideoScriptAssistantStore = defineStore('videoScriptAssistant', () => {
  const scripts = ref<VideoScript[]>([])
  const suggestions = ref<ScriptSuggestion[]>([])
  const summary = ref<VideoScriptAssistantSummary | null>(null)
  const loading = ref(false)

  const fetchScripts = async () => {
    loading.value = true
    try {
      scripts.value = await videoScriptAssistantApi.getScripts()
    } catch {
      scripts.value = [
        { id: 1, title: '여행 브이로그 스크립트', videoId: 101, videoTitle: '제주도 3일 여행', content: '안녕하세요 여러분! 오늘은 제주도 여행 브이로그를 가져왔어요...', tone: 'CASUAL', targetLength: 600, wordCount: 580, hookLine: '제주도에서 숨겨진 맛집을 찾았습니다!', ctaText: '구독과 좋아요 부탁드려요!', status: 'COMPLETED', createdAt: '2025-01-15T10:00:00Z', updatedAt: '2025-01-15T10:30:00Z' },
        { id: 2, title: '테크 리뷰 스크립트', videoId: 102, videoTitle: 'MacBook Pro M4 리뷰', content: '오늘 리뷰할 제품은 애플의 최신 맥북 프로입니다...', tone: 'PROFESSIONAL', targetLength: 800, wordCount: 750, hookLine: 'M4 칩, 정말 혁신적일까요?', ctaText: '여러분의 의견을 댓글로 남겨주세요', status: 'COMPLETED', createdAt: '2025-01-14T09:00:00Z', updatedAt: '2025-01-14T09:45:00Z' },
        { id: 3, title: '요리 튜토리얼', videoId: null, videoTitle: null, content: '', tone: 'HUMOROUS', targetLength: 500, wordCount: 0, hookLine: null, ctaText: null, status: 'GENERATING', createdAt: '2025-01-16T14:00:00Z', updatedAt: '2025-01-16T14:00:00Z' },
        { id: 4, title: '게임 공략 가이드', videoId: 103, videoTitle: '엘든링 보스 공략', content: '이번 영상에서는 가장 어려운 보스를 공략해보겠습니다...', tone: 'EDUCATIONAL', targetLength: 1000, wordCount: 920, hookLine: '이 방법이면 1트에 클리어 가능합니다', ctaText: '다음 보스 공략이 궁금하시면 구독!', status: 'COMPLETED', createdAt: '2025-01-13T11:00:00Z', updatedAt: '2025-01-13T12:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  const fetchSuggestions = async (scriptId: number) => {
    try {
      suggestions.value = await videoScriptAssistantApi.getSuggestions(scriptId)
    } catch {
      suggestions.value = [
        { id: 1, scriptId: 1, sectionType: 'HOOK', originalText: '안녕하세요 여러분!', suggestedText: '제주도에서 현지인만 아는 맛집 3곳을 발견했습니다!', reason: '더 강력한 훅으로 시청 유지율 향상', isApplied: true, createdAt: '2025-01-15T10:15:00Z' },
        { id: 2, scriptId: 1, sectionType: 'CTA', originalText: '구독과 좋아요 부탁드려요!', suggestedText: '다음 여행지 투표는 커뮤니티 탭에서!', reason: '시청자 참여를 유도하는 CTA', isApplied: false, createdAt: '2025-01-15T10:16:00Z' },
        { id: 3, scriptId: 1, sectionType: 'TRANSITION', originalText: '다음 장소로 이동했습니다', suggestedText: '자, 이제 진짜 하이라이트가 시작됩니다!', reason: '시청자의 기대감을 높이는 전환', isApplied: false, createdAt: '2025-01-15T10:17:00Z' },
      ]
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await videoScriptAssistantApi.getSummary()
    } catch {
      summary.value = { totalScripts: 28, completedScripts: 24, avgWordCount: 680, topTone: 'CASUAL', suggestionsApplied: 45 }
    }
  }

  return { scripts, suggestions, summary, loading, fetchScripts, fetchSuggestions, fetchSummary }
})
