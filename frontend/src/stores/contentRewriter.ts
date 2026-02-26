import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RewriteRequest, RewriteResult, OutputFormat } from '@/types/contentRewriter'
import { contentRewriterApi } from '@/api/contentRewriter'

function generateMockResults(formats: OutputFormat[]): RewriteResult[] {
  const mockTexts: Record<OutputFormat, string> = {
    SHORT_VIDEO: '🎬 60초만에 알려드리는 핵심 꿀팁! 영상 끝까지 보시면 놀라운 사실이...',
    TWEET: '크리에이터라면 꼭 알아야 할 팁 하나 공유합니다. 저도 이걸 알고 나서 조회수가 3배 늘었어요 🚀',
    BLOG_POST: '## 크리에이터가 반드시 알아야 할 성장 전략\n\n오늘은 많은 크리에이터들이 놓치고 있는 핵심 성장 전략에 대해 이야기해보겠습니다...',
    CAPTION: '이 비밀을 알면 콘텐츠 퀄리티가 확 달라집니다 ✨\n\n자세한 내용은 프로필 링크에서 확인하세요!',
    NEWSLETTER: '안녕하세요, 이번 주 뉴스레터에서는 콘텐츠 제작의 핵심 노하우를 공유합니다...',
    THREAD: '🧵 크리에이터 성장의 비밀 (스레드)\n\n1/ 많은 분들이 콘텐츠 양에만 집중하지만, 실제로 중요한 건...',
  }
  return formats.map((format, i) => ({
    id: Date.now() + i,
    sourceId: 1,
    format,
    platform: format === 'TWEET' || format === 'THREAD' ? 'twitter' : format === 'CAPTION' ? 'instagram' : 'youtube',
    text: mockTexts[format],
    hashtags: ['#크리에이터', '#콘텐츠팁', '#성장전략'],
    characterCount: mockTexts[format].length,
    estimatedEngagement: Math.round(5 + Math.random() * 10),
    createdAt: new Date().toISOString(),
  }))
}

export const useContentRewriterStore = defineStore('contentRewriter', () => {
  const results = ref<RewriteResult[]>([])
  const history = ref<RewriteResult[]>([])
  const rewriting = ref(false)
  const loading = ref(false)

  async function rewrite(request: RewriteRequest) {
    rewriting.value = true
    try {
      const response = await contentRewriterApi.rewrite(request)
      results.value = response.results
    } catch {
      results.value = generateMockResults(request.outputFormats)
    } finally {
      rewriting.value = false
    }
  }

  async function fetchHistory() {
    loading.value = true
    try {
      history.value = await contentRewriterApi.getHistory()
    } catch {
      history.value = generateMockResults(['SHORT_VIDEO', 'TWEET', 'CAPTION'])
    } finally {
      loading.value = false
    }
  }

  async function deleteResult(id: number) {
    try { await contentRewriterApi.deleteResult(id) } catch { /* 로컬 제거 */ }
    results.value = results.value.filter((r) => r.id !== id)
    history.value = history.value.filter((r) => r.id !== id)
  }

  return { results, history, rewriting, loading, rewrite, fetchHistory, deleteResult }
})
