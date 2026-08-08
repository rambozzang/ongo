import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { commentsApi } from '@/api/comments'
import { inboxApi } from '@/api/inbox'
import type { CommentResponse } from '@/types/comment'
import type { InboxMessageResponse } from '@/api/inbox'

/**
 * 리디자인 인박스 — 댓글(commentsApi)과 DM(inboxApi)을 하나의 스레드 목록으로 합친다.
 * 백엔드에 통합 스레드 API가 없으므로 두 소스를 클라이언트에서 병합한다.
 */

export type InboxFilter = 'all' | 'question' | 'negative' | 'dm' | 'collab' | 'done'

export interface InboxThread {
  /** 'c-{commentId}' | 'm-{messageId}' — source 가 달라도 id 가 겹치지 않게 한다 */
  id: string
  source: 'comment' | 'dm'
  refId: number
  platform: string | null
  author: string
  avatarUrl: string | null
  text: string
  videoId: number | null
  sentiment: string | null
  /** comment 는 isReplied, dm 은 isRead 를 "처리됨"으로 본다 */
  answered: boolean
  isPinned: boolean
  likeCount: number
  replyContent: string | null
  repliedAt: string | null
  time: string | null
}

// 분류 휴리스틱 — 백엔드에 멘션/협업 분류 필드가 없어 키워드로 추정한다
const QUESTION_RE = /\?|？/
const COLLAB_RE = /협업|제안|광고|sponsor|collab/i

function commentToThread(c: CommentResponse): InboxThread {
  return {
    id: `c-${c.id}`,
    source: 'comment',
    refId: c.id,
    platform: c.platform,
    author: c.authorName,
    avatarUrl: c.authorAvatarUrl,
    text: c.content,
    videoId: c.videoId,
    sentiment: c.sentiment,
    answered: c.isReplied,
    isPinned: c.isPinned,
    likeCount: c.likeCount,
    replyContent: c.replyContent,
    repliedAt: c.repliedAt,
    time: c.publishedAt ?? c.createdAt,
  }
}

function messageToThread(m: InboxMessageResponse): InboxThread {
  return {
    id: `m-${m.id}`,
    source: 'dm',
    refId: m.id,
    platform: m.platform,
    author: m.senderName,
    avatarUrl: m.senderAvatarUrl,
    text: m.content,
    videoId: m.videoId,
    sentiment: null,
    answered: m.isRead,
    isPinned: m.isStarred,
    likeCount: 0,
    replyContent: null,
    repliedAt: null,
    time: m.receivedAt ?? m.createdAt,
  }
}

export const useRedesignInboxStore = defineStore('redesignInbox', () => {
  const threads = ref<InboxThread[]>([])
  const loading = ref(false)
  /** 두 소스 중 하나만 실패해도 나머지는 보여 준다 */
  const loadError = ref<string | null>(null)
  const filter = ref<InboxFilter>('all')
  const selectedId = ref<string | null>(null)
  /** 일괄 처리용 체크된 스레드 id (comment 만 대상이 된다) */
  const checkedIds = ref<string[]>([])
  const sending = ref(false)

  function matchesFilter(t: InboxThread, f: InboxFilter): boolean {
    if (f === 'done') return t.answered
    if (t.answered) return false
    switch (f) {
      case 'all':
        return true
      case 'question':
        return QUESTION_RE.test(t.text)
      case 'negative':
        return t.sentiment === 'negative'
      case 'dm':
        return t.source === 'dm'
      case 'collab':
        return COLLAB_RE.test(t.text)
    }
  }

  const visibleThreads = computed(() =>
    threads.value
      .filter((t) => matchesFilter(t, filter.value))
      .sort((a, b) => (b.time ?? '').localeCompare(a.time ?? '')),
  )

  const selectedThread = computed(
    () => threads.value.find((t) => t.id === selectedId.value) ?? null,
  )

  const filterCounts = computed<Record<InboxFilter, number>>(() => ({
    all: threads.value.filter((t) => matchesFilter(t, 'all')).length,
    question: threads.value.filter((t) => matchesFilter(t, 'question')).length,
    negative: threads.value.filter((t) => matchesFilter(t, 'negative')).length,
    dm: threads.value.filter((t) => matchesFilter(t, 'dm')).length,
    collab: threads.value.filter((t) => matchesFilter(t, 'collab')).length,
    done: threads.value.filter((t) => matchesFilter(t, 'done')).length,
  }))

  async function fetchAll() {
    loading.value = true
    loadError.value = null
    // 한쪽이 실패해도 다른 쪽은 표시한다
    const [comments, messages] = await Promise.allSettled([
      commentsApi.list({ size: 100 }),
      inboxApi.listMessages({ size: 100 }),
    ])
    const previousComments = threads.value.filter((thread) => thread.source === 'comment')
    const previousMessages = threads.value.filter((thread) => thread.source === 'dm')
    const list: InboxThread[] = []
    if (comments.status === 'fulfilled') {
      list.push(...comments.value.comments.map(commentToThread))
    } else list.push(...previousComments)
    if (messages.status === 'fulfilled') {
      list.push(...messages.value.messages.map(messageToThread))
    } else list.push(...previousMessages)
    if (comments.status === 'rejected' && messages.status === 'rejected') {
      loadError.value = 'loadFailed'
    } else if (comments.status === 'rejected' || messages.status === 'rejected') {
      loadError.value = 'loadPartial'
    }
    threads.value = list
    loading.value = false

    // 처음 진입하면 첫 미답변 스레드를 선택한다
    if (!selectedId.value || !list.some((t) => t.id === selectedId.value)) {
      selectedId.value = visibleThreads.value[0]?.id ?? null
    }
  }

  function setFilter(f: InboxFilter) {
    filter.value = f
    checkedIds.value = []
    // 필터를 바꾸면 그 목록의 첫 스레드로 이동한다
    selectedId.value = visibleThreads.value[0]?.id ?? null
  }

  function select(id: string) {
    selectedId.value = id
  }

  /** J/K — 필터된 목록에서 선택을 한 칸 옮긴다 */
  function moveSelection(delta: number) {
    const list = visibleThreads.value
    if (list.length === 0) return
    const index = list.findIndex((t) => t.id === selectedId.value)
    const next = Math.min(list.length - 1, Math.max(0, (index === -1 ? 0 : index) + delta))
    selectedId.value = list[next].id
  }

  /** 전송 후 다음 미답변 스레드로 자동 이동한다 */
  function advanceToNext() {
    const list = visibleThreads.value
    const index = list.findIndex((t) => t.id === selectedId.value)
    const next = list[index + 1] ?? list[index - 1] ?? list[0]
    selectedId.value = next?.id ?? null
  }

  /** 댓글 답변 전송. DM은 백엔드에 답장 API가 없어 호출하면 안 된다(뷰에서 막는다). */
  async function sendReply(thread: InboxThread, content: string) {
    sending.value = true
    try {
      await commentsApi.reply(thread.refId, content)
      const found = threads.value.find((t) => t.id === thread.id)
      if (found) {
        found.answered = true
        found.replyContent = content
        found.repliedAt = new Date().toISOString()
      }
      advanceToNext()
    } finally {
      sending.value = false
    }
  }

  async function pinThread(thread: InboxThread) {
    if (thread.source === 'comment') await commentsApi.pin(thread.refId)
    else await inboxApi.toggleStar(thread.refId)
    const found = threads.value.find((t) => t.id === thread.id)
    if (found) found.isPinned = !found.isPinned
  }

  /** 숨기기 = 처리 완료로 본다. DM은 읽음 처리로 대체한다. */
  async function hideThread(thread: InboxThread) {
    if (thread.source === 'comment') await commentsApi.hide(thread.refId)
    else await inboxApi.markAsRead(thread.refId)
    const found = threads.value.find((t) => t.id === thread.id)
    if (found) found.answered = true
    advanceToNext()
  }

  /** AI 답변 제안 — 댓글만 지원. 첫 후보 문구를 반환한다. */
  async function suggestReply(thread: InboxThread): Promise<string | null> {
    const res = await commentsApi.aiReplyGenerate(thread.refId)
    return res.candidates[0]?.generatedReply ?? null
  }

  function toggleChecked(id: string) {
    const i = checkedIds.value.indexOf(id)
    if (i >= 0) checkedIds.value.splice(i, 1)
    else checkedIds.value.push(id)
  }

  function toggleCheckAll() {
    const commentIds = visibleThreads.value.filter((t) => t.source === 'comment').map((t) => t.id)
    checkedIds.value = checkedIds.value.length === commentIds.length ? [] : commentIds
  }

  /** 일괄 처리 = 선택한 댓글 숨김(처리 완료). DM은 배치 API가 없어 제외한다. */
  async function batchHideChecked() {
    const ids = threads.value
      .filter((t) => checkedIds.value.includes(t.id) && t.source === 'comment')
      .map((t) => t.refId)
    if (ids.length === 0) return 0
    const result = await commentsApi.batchHide(ids)
    threads.value.forEach((t) => {
      if (checkedIds.value.includes(t.id)) t.answered = true
    })
    checkedIds.value = []
    if (selectedId.value && !visibleThreads.value.some((t) => t.id === selectedId.value)) {
      selectedId.value = visibleThreads.value[0]?.id ?? null
    }
    return result.successCount
  }

  return {
    threads,
    loading,
    loadError,
    filter,
    selectedId,
    checkedIds,
    sending,
    visibleThreads,
    selectedThread,
    filterCounts,
    fetchAll,
    setFilter,
    select,
    moveSelection,
    sendReply,
    pinThread,
    hideThread,
    suggestReply,
    toggleChecked,
    toggleCheckAll,
    batchHideChecked,
  }
})
