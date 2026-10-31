import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { commentsApi } from '@/api/comments'
import { inboxApi } from '@/api/inbox'
import type { BatchActionResult, CommentResponse } from '@/types/comment'
import type { InboxMessageResponse } from '@/api/inbox'
import { readStableCode } from '@/composables/usePlanLimit'

/** 한 번에 불러오는 댓글 수. 더 있으면 "더 불러오기" 로 이어 받는다. */
export const INBOX_PAGE_SIZE = 50
/** 서버 일괄 처리 상한(CommentBatchUseCase.MAX_BATCH_SIZE / GenerateReplyUseCase.MAX_BATCH_DRAFTS)과 같다. */
export const INBOX_BATCH_MAX = 50
export const INBOX_AI_DRAFT_MAX = 25

export type InboxBatchKind = 'hide' | 'reply' | 'draft'

/** 한 번의 일괄 동작. 실패를 다시 보낼 때 **그 실패가 속한 동작·문구**로만 보내기 위해 실패마다 붙여 둔다. */
export interface InboxBatchAction {
  id: number
  kind: InboxBatchKind
  replyText?: string
}

/** 일괄·초안 전송에서 실패한 스레드의 마지막 결과. 성공하면 지운다. */
export interface InboxItemFailure {
  errorCode: string | null
  message: string | null
  /** 다시 보내면 나아질 수 있는가. 권한·미지원·삭제된 댓글은 false. */
  retryable: boolean
  /** 이 실패를 낸 일괄 동작. 다른 동작의 문구가 이 댓글로 나가는 일을 막는다. */
  batch: InboxBatchAction
}

export interface InboxBatchOutcome {
  succeeded: number
  failed: number
}

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
    // 서버는 NEGATIVE 처럼 대문자로 저장한다. 비교를 한 곳에서 맞춘다 — 예전에는 부정 필터가 한 번도 맞지 않았다.
    sentiment: c.sentiment ? c.sentiment.toLowerCase() : null,
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

  /** 서버 검색어·플랫폼. 댓글은 서버가 거르고, DM 은 화면에서 같은 조건으로 거른다. */
  const search = ref('')
  const platform = ref<string | null>(null)
  const commentPage = ref(0)
  const commentTotal = ref(0)
  const loadingMore = ref(false)

  /** 스레드별 마지막 실패. 목록에 사유를 보이고 "실패만 다시 시도" 의 대상이 된다. */
  const failures = ref<Record<string, InboxItemFailure>>({})
  /** 마지막 일괄 동작. "실패 다시 시도" 는 **이 동작에서 난 실패만** 같은 방식으로 다시 보낸다. */
  const lastBatch = ref<InboxBatchAction | null>(null)
  let batchSeq = 0
  function beginBatch(kind: InboxBatchKind, replyText?: string): InboxBatchAction {
    const action: InboxBatchAction = { id: ++batchSeq, kind, replyText }
    lastBatch.value = action
    return action
  }
  const batchRunning = ref(false)

  /** AI 초안 검토 대기열(스레드 id → 수정 가능한 초안). 보내기 전까지 서버에 아무것도 나가지 않는다. */
  const drafts = ref<Record<string, string>>({})
  const draftOrder = ref<string[]>([])

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

  /** 댓글은 서버가 이미 걸렀다. DM 은 서버 검색이 없어 같은 조건을 여기서 적용한다. */
  function matchesQuery(t: InboxThread): boolean {
    if (t.source !== 'dm') return true
    if (platform.value && t.platform !== platform.value) return false
    const q = search.value.trim().toLowerCase()
    return !q || t.text.toLowerCase().includes(q) || t.author.toLowerCase().includes(q)
  }

  const visibleThreads = computed(() =>
    threads.value
      .filter((t) => matchesQuery(t) && matchesFilter(t, filter.value))
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

  /**
   * 조회 순번. 검색어·필터를 빠르게 바꾸면 응답이 요청 순서와 다르게 올 수 있다 — 늦게 온 **예전 조회의 응답**이
   * 새 결과를 덮지 않도록, 최신 순번이 아닌 응답은 버린다. 더 불러오기도 그 사이 새 조회가 시작됐으면 붙이지 않는다.
   */
  let requestSeq = 0

  async function fetchAll() {
    const seq = ++requestSeq
    loading.value = true
    loadError.value = null
    // 한쪽이 실패해도 다른 쪽은 표시한다
    const [comments, messages] = await Promise.allSettled([
      commentsApi.list(commentQuery(0)),
      inboxApi.listMessages({ size: 100 }),
    ])
    if (seq !== requestSeq) return
    const previousComments = threads.value.filter((thread) => thread.source === 'comment')
    const previousMessages = threads.value.filter((thread) => thread.source === 'dm')
    const list: InboxThread[] = []
    if (comments.status === 'fulfilled') {
      list.push(...comments.value.comments.map(commentToThread))
      commentPage.value = 0
      commentTotal.value = comments.value.totalCount ?? comments.value.comments.length
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

  function commentQuery(page: number) {
    return {
      page,
      size: INBOX_PAGE_SIZE,
      searchText: search.value.trim() || undefined,
      platform: platform.value ?? undefined,
      // 부정 탭은 불러온 목록만 거르지 않고 서버에서 부정 댓글 전체를 찾는다.
      sentiment: filter.value === 'negative' ? 'NEGATIVE' : undefined,
    }
  }

  const loadedCommentCount = computed(() => threads.value.filter((t) => t.source === 'comment').length)
  const hasMoreComments = computed(() => loadedCommentCount.value < commentTotal.value)

  /** 검색어·플랫폼을 바꾸고 서버에서 다시 받는다. 선택·체크는 새 목록 기준으로 정리된다. */
  async function setQuery(next: { search?: string; platform?: string | null }) {
    if (next.search !== undefined) search.value = next.search
    if (next.platform !== undefined) platform.value = next.platform
    checkedIds.value = []
    await fetchAll()
  }

  /** 다음 페이지 댓글을 이어 붙인다. 이미 있는 댓글(동기화로 앞 페이지가 밀린 경우)은 겹치지 않게 거른다. */
  async function loadMoreComments() {
    if (loadingMore.value || !hasMoreComments.value) return
    loadingMore.value = true
    const seq = requestSeq
    try {
      const next = commentPage.value + 1
      const res = await commentsApi.list(commentQuery(next))
      // 그 사이 검색·필터가 바뀌었으면 예전 조건의 다음 페이지를 붙이지 않는다.
      if (seq !== requestSeq) return
      const known = new Set(threads.value.map((t) => t.id))
      threads.value.push(...res.comments.map(commentToThread).filter((t) => !known.has(t.id)))
      commentPage.value = next
      commentTotal.value = res.totalCount ?? commentTotal.value
    } finally {
      loadingMore.value = false
    }
  }

  /** 서버 조회 조건이 달라지는 필터인가(부정 탭은 서버 감정 필터를 쓴다). */
  function usesServerSentiment(f: InboxFilter): boolean {
    return f === 'negative'
  }

  async function setFilter(f: InboxFilter) {
    const refetch = usesServerSentiment(f) !== usesServerSentiment(filter.value)
    filter.value = f
    checkedIds.value = []
    // 필터를 바꾸면 그 목록의 첫 스레드로 이동한다
    selectedId.value = visibleThreads.value[0]?.id ?? null
    // 부정 탭에 들어가면 감정 조건으로, 나오면 조건 없이 다시 받는다.
    if (refetch) {
      selectedId.value = null
      await fetchAll()
    }
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
    // 처리 완료는 숨김으로 정한다(토글이면 이미 숨긴 댓글이 도로 보인다).
    if (thread.source === 'comment') await commentsApi.hide(thread.refId, true)
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

  function checkedComments(): InboxThread[] {
    return threads.value.filter((t) => checkedIds.value.includes(t.id) && t.source === 'comment')
  }

  function threadOfComment(commentId: number): InboxThread | undefined {
    return threads.value.find((t) => t.source === 'comment' && t.refId === commentId)
  }

  function markSucceeded(thread: InboxThread, kind: InboxBatchKind, replyText?: string) {
    thread.answered = true
    if (kind !== 'hide' && replyText) {
      thread.replyContent = replyText
      thread.repliedAt = new Date().toISOString()
    }
    delete failures.value[thread.id]
    checkedIds.value = checkedIds.value.filter((id) => id !== thread.id)
  }

  /**
   * 서버의 항목별 결과를 반영한다. **성공한 것만** 처리 완료로 바꾸고, 실패한 것은 체크와 사유를 남긴다.
   * 예전에는 일부가 실패해도 선택한 전부를 처리 완료로 표시했다.
   */
  function applyBatchResult(action: InboxBatchAction, result: BatchActionResult): InboxBatchOutcome {
    const { kind, replyText } = action
    let succeeded = 0
    let failed = 0
    for (const item of result.items ?? []) {
      const thread = threadOfComment(item.commentId)
      if (!thread) continue
      if (item.success) {
        markSucceeded(thread, kind, replyText)
        succeeded++
      } else {
        failures.value[thread.id] = { errorCode: item.errorCode, message: item.message, retryable: item.retryable, batch: action }
        failed++
      }
    }
    keepSelectionVisible()
    return { succeeded, failed }
  }

  function keepSelectionVisible() {
    if (selectedId.value && !visibleThreads.value.some((t) => t.id === selectedId.value)) {
      selectedId.value = visibleThreads.value[0]?.id ?? null
    }
  }

  async function runCommentBatch(kind: 'hide' | 'reply', targets: InboxThread[], replyText?: string): Promise<InboxBatchOutcome> {
    if (targets.length === 0) return { succeeded: 0, failed: 0 }
    if (targets.length > INBOX_BATCH_MAX) throw new Error(`한 번에 ${INBOX_BATCH_MAX}개까지 처리할 수 있습니다.`)
    batchRunning.value = true
    try {
      const ids = targets.map((t) => t.refId)
      const action = beginBatch(kind, replyText)
      const result = kind === 'hide'
        ? await commentsApi.batchHide(ids)
        : await commentsApi.batchReply(ids, replyText ?? '')
      return applyBatchResult(action, result)
    } finally {
      batchRunning.value = false
    }
  }

  /** 선택한 댓글 숨김(처리 완료). DM 은 일괄 API 가 없어 제외한다. */
  async function batchHideChecked(): Promise<InboxBatchOutcome> {
    return runCommentBatch('hide', checkedComments())
  }

  /** 선택한 댓글에 같은 답변을 보낸다. 플랫폼에 실제로 전송된 것만 처리 완료가 된다. */
  async function batchReplyChecked(replyText: string): Promise<InboxBatchOutcome> {
    const text = replyText.trim()
    if (!text) throw new Error('보낼 답변을 입력해 주세요.')
    return runCommentBatch('reply', checkedComments(), text)
  }

  /**
   * **마지막 일괄 동작에서 난**, 다시 보내면 나아질 수 있는 실패.
   *
   * 예전에는 모든 실패를 모아 마지막 동작으로 다시 보냈다 — 일괄 숨김에서 실패한 댓글이 남은 채 다른 댓글에 일괄
   * 답변을 하면, 재시도가 그 숨김 실패 댓글에 **답변 문구를 실제로 게시**했다. 이전 동작의 실패는 사유만 보인다.
   */
  const retryableFailures = computed(() => {
    const batch = lastBatch.value
    if (!batch) return []
    return threads.value.filter((t) => {
      const failure = failures.value[t.id]
      return failure?.retryable === true && failure.batch.id === batch.id
    })
  })

  /** 마지막 일괄 동작의 다시 시도할 수 있는 실패만, **그 동작과 그 문구로** 다시 보낸다. */
  async function retryFailed(): Promise<InboxBatchOutcome> {
    const batch = lastBatch.value
    const targets = retryableFailures.value
    if (!batch || targets.length === 0) return { succeeded: 0, failed: 0 }
    if (batch.kind === 'draft') return sendDrafts(targets.map((t) => t.id))
    return runCommentBatch(batch.kind, targets, batch.replyText)
  }

  /**
   * 선택한 댓글의 AI 답변 초안을 만든다(크레딧 차감 — 나오지 않은 초안 몫은 서버가 돌려준다).
   * 초안은 검토 대기열에 들어갈 뿐 **보내지 않는다.**
   */
  async function generateDrafts(tone = 'FRIENDLY'): Promise<{ created: number; requested: number; creditsUsed: number }> {
    const targets = checkedComments()
    if (targets.length === 0) return { created: 0, requested: 0, creditsUsed: 0 }
    if (targets.length > INBOX_AI_DRAFT_MAX) throw new Error(`AI 초안은 한 번에 ${INBOX_AI_DRAFT_MAX}개까지 만들 수 있습니다.`)
    batchRunning.value = true
    try {
      const res = await commentsApi.batchAiDraft(targets.map((t) => t.refId), tone)
      for (const draft of res.drafts) {
        const thread = threadOfComment(draft.commentId)
        if (!thread) continue
        if (!(thread.id in drafts.value)) draftOrder.value.push(thread.id)
        drafts.value[thread.id] = draft.draftReply
      }
      return { created: res.drafts.length, requested: targets.length, creditsUsed: res.totalCreditsUsed }
    } finally {
      batchRunning.value = false
    }
  }

  function updateDraft(threadId: string, text: string) {
    if (threadId in drafts.value) drafts.value[threadId] = text
  }

  function discardDraft(threadId: string) {
    delete drafts.value[threadId]
    draftOrder.value = draftOrder.value.filter((id) => id !== threadId)
  }

  const pendingDrafts = computed(() =>
    draftOrder.value
      .map((id) => ({ thread: threads.value.find((t) => t.id === id), text: drafts.value[id] }))
      .filter((d): d is { thread: InboxThread; text: string } => d.thread != null && d.text != null),
  )

  /**
   * 검토한 초안을 보낸다. 초안마다 문구가 달라 하나씩 보낸다. 성공한 초안만 대기열에서 빠지고,
   * 실패한 초안은 문구와 사유를 남겨 고쳐서 다시 보낼 수 있다.
   */
  async function sendDrafts(threadIds: string[] = [...draftOrder.value]): Promise<InboxBatchOutcome> {
    let succeeded = 0
    let failed = 0
    batchRunning.value = true
    const action = beginBatch('draft')
    try {
      for (const id of threadIds) {
        const thread = threads.value.find((t) => t.id === id)
        const text = drafts.value[id]?.trim()
        if (!thread || !text) continue
        try {
          await commentsApi.reply(thread.refId, text)
          markSucceeded(thread, 'draft', text)
          discardDraft(id)
          succeeded++
        } catch (e) {
          failures.value[id] = failureFrom(e, action)
          failed++
        }
      }
    } finally {
      batchRunning.value = false
    }
    keepSelectionVisible()
    return { succeeded, failed }
  }

  /** 단건 요청 실패를 목록 표시용 사유로. 플랫폼·네트워크·서버 오류는 다시 시도할 수 있다. */
  function failureFrom(e: unknown, batch: InboxBatchAction): InboxItemFailure {
    const code = readStableCode(e)
    const status = (e as { response?: { status?: number } })?.response?.status
    const message = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
    return {
      errorCode: code,
      message: typeof message === 'string' ? message : e instanceof Error ? e.message : null,
      retryable: code === 'PLATFORM_API_FAILED' || code === 'RATE_LIMITED' || status == null || status >= 500 || status === 429,
      batch,
    }
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
    batchReplyChecked,
    retryFailed,
    retryableFailures,
    failures,
    batchRunning,
    generateDrafts,
    updateDraft,
    discardDraft,
    pendingDrafts,
    sendDrafts,
    search,
    platform,
    setQuery,
    loadMoreComments,
    hasMoreComments,
    loadingMore,
  }
})
