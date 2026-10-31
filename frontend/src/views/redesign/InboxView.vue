<template>
  <div class="flex h-full min-h-0 flex-col">
    <!-- 한쪽 소스만 불러와졌을 때 알림 -->
    <div
      v-if="store.loadError"
      class="flex items-center gap-2 border-b border-line px-4 py-2 text-[11.5px] text-warn"
    >
      {{ $t(`redesign.inbox.${store.loadError}`) }}
    </div>

    <!-- <1440px: 필터는 가로 칩으로 -->
    <div class="flex gap-[6px] overflow-x-auto border-b border-line px-3 py-2 min-[1440px]:hidden">
      <button
        v-for="f in FILTERS"
        :key="f"
        type="button"
        class="shrink-0 whitespace-nowrap rounded-[7px] border px-[10px] py-[6px] text-[11.5px] transition-[border-color,color] duration-[120ms] ease-out"
        :class="store.filter === f
          ? 'border-line-hover bg-accent-dim font-bold text-content'
          : 'border-line text-content-tertiary hover:border-line-hover hover:text-content'"
        @click="store.setFilter(f)"
      >
        {{ $t(`redesign.inbox.filters.${f}`) }}
        <span class="ml-1 font-mono text-[10px] opacity-70">{{ store.filterCounts[f] }}</span>
      </button>
    </div>

    <div class="flex min-h-0 flex-1">
      <!-- 1열: 필터 레일 (≥1440px) -->
      <aside class="hidden w-[178px] shrink-0 flex-col gap-[3px] overflow-y-auto border-r border-line px-3 py-[15px] min-[1440px]:flex">
        <div class="px-2 pb-2 font-mono text-[10.5px] tracking-[0.12em] text-content-tertiary">
          {{ $t('redesign.inbox.filterLabel') }}
        </div>
        <button
          v-for="f in FILTERS"
          :key="f"
          type="button"
          class="flex items-center rounded-[7px] px-2 py-[7px] text-left text-[12px] transition-colors duration-[120ms] ease-out"
          :class="store.filter === f
            ? 'bg-accent-dim font-bold text-content'
            : 'text-content-secondary hover:bg-surface-raised hover:text-content'"
          @click="store.setFilter(f)"
        >
          <span class="flex-1">{{ $t(`redesign.inbox.filters.${f}`) }}</span>
          <span class="font-mono text-[10px] opacity-70">{{ store.filterCounts[f] }}</span>
        </button>

        <div class="mt-4 px-2 pb-2 font-mono text-[10.5px] tracking-[0.12em] text-content-tertiary">
          {{ $t('redesign.inbox.savedReplyLabel') }}
        </div>
        <button
          v-for="(m, i) in savedReplies"
          :key="i"
          type="button"
          class="rounded-[7px] px-2 py-[7px] text-left text-[12px] text-content-secondary transition-colors duration-[120ms] ease-out hover:bg-surface-raised hover:text-content"
          @click="insertSavedReply(m.body)"
        >
          {{ m.label }}
        </button>
      </aside>

      <!-- 2열: 스레드 목록. <1024px 에서는 상세가 열리면 숨긴다 -->
      <section
        class="min-h-0 w-full flex-col overflow-y-auto border-r border-line min-[1024px]:flex min-[1024px]:w-[340px] min-[1024px]:shrink-0"
        :class="store.selectedThread ? 'hidden' : 'flex'"
      >
        <div class="sticky top-0 z-10 border-b border-line bg-surface">
          <!-- 서버 검색: 댓글 전체에서 찾는다(불러온 목록만 거르지 않는다) -->
          <div class="flex gap-[6px] px-[13px] pt-[11px]">
            <input
              v-model="searchInput"
              type="search"
              data-testid="inbox-search"
              class="min-h-[36px] min-w-0 flex-1 rounded-[7px] border border-line-control bg-surface-input px-[10px] text-[12px] text-content outline-none placeholder:text-content-quaternary focus:border-accent"
              :placeholder="$t('redesign.inbox.searchPlaceholder')"
              :aria-label="$t('redesign.inbox.searchLabel')"
            />
            <select
              data-testid="inbox-platform"
              class="min-h-[36px] rounded-[7px] border border-line-control bg-surface-input px-[8px] text-[12px] text-content"
              :aria-label="$t('redesign.inbox.platformLabel')"
              :value="store.platform ?? ''"
              @change="onPlatformChange(($event.target as HTMLSelectElement).value)"
            >
              <option value="">{{ $t('redesign.inbox.platformAll') }}</option>
              <option v-for="(label, key) in PLATFORM_LABELS" :key="key" :value="key">{{ label }}</option>
            </select>
          </div>

          <div class="flex items-center gap-[9px] px-[13px] py-[11px]">
            <div class="flex-1 text-[12px] font-bold text-content">
              <template v-if="checkedCount > 0">{{ $t('redesign.inbox.checkedCount', { count: checkedCount }) }}</template>
              <template v-else>{{ $t('redesign.inbox.unanswered', { count: store.filterCounts.all }) }}</template>
            </div>
            <button
              type="button"
              class="min-h-[32px] text-[11px] text-content-secondary hover:text-content"
              @click="store.toggleCheckAll()"
            >
              {{ $t('redesign.inbox.selectAll') }}
            </button>
          </div>

          <!-- 일괄 도구: 선택이 있을 때만 -->
          <div v-if="checkedCount > 0" class="flex flex-wrap gap-[6px] px-[13px] pb-[11px]" data-testid="inbox-batch-bar">
            <p v-if="checkedCount > INBOX_BATCH_MAX" class="w-full text-[11px] text-warn" role="alert">
              {{ $t('redesign.inbox.batchTooMany', { max: INBOX_BATCH_MAX }) }}
            </p>
            <button
              type="button"
              data-testid="inbox-batch-hide"
              class="min-h-[32px] rounded-[7px] border border-line-control px-[10px] text-[11.5px] text-content-secondary hover:border-line-hover hover:text-content disabled:opacity-40"
              :disabled="store.batchRunning || checkedCount > INBOX_BATCH_MAX"
              @click="runBatch"
            >
              {{ $t('redesign.inbox.batchHide') }}
            </button>
            <button
              type="button"
              data-testid="inbox-batch-reply-open"
              class="min-h-[32px] rounded-[7px] border border-line-control px-[10px] text-[11.5px] text-content-secondary hover:border-line-hover hover:text-content disabled:opacity-40"
              :disabled="store.batchRunning || checkedCount > INBOX_BATCH_MAX"
              @click="batchReplyOpen = !batchReplyOpen"
            >
              {{ $t('redesign.inbox.batchReply') }}
            </button>
            <button
              type="button"
              data-testid="inbox-ai-draft"
              class="min-h-[32px] rounded-[7px] border border-line-control px-[10px] text-[11.5px] text-accent hover:border-accent disabled:opacity-40"
              :disabled="store.batchRunning || draftCredits === null || checkedCount > INBOX_AI_DRAFT_MAX"
              :title="checkedCount > INBOX_AI_DRAFT_MAX ? $t('redesign.inbox.aiDraftTooMany', { max: INBOX_AI_DRAFT_MAX }) : undefined"
              @click="runAiDrafts"
            >
              {{ draftCredits === null
                ? $t('redesign.inbox.aiDraftCostUnknown')
                : $t('redesign.inbox.aiDraftCost', { credits: draftCredits }) }}
            </button>

            <div v-if="batchReplyOpen" class="w-full overflow-hidden rounded-[9px] border border-line-control bg-surface-input">
              <textarea
                v-model="batchReplyText"
                rows="2"
                data-testid="inbox-batch-reply-text"
                class="block w-full resize-y border-0 bg-transparent px-[11px] py-[9px] text-[12.5px] text-content outline-none placeholder:text-content-quaternary"
                :placeholder="$t('redesign.inbox.batchReplyPlaceholder')"
              />
              <div class="flex justify-end gap-[6px] border-t border-line px-[9px] py-[7px]">
                <button type="button" class="min-h-[32px] px-[8px] text-[11px] text-content-secondary" @click="batchReplyOpen = false">
                  {{ $t('redesign.inbox.batchCancel') }}
                </button>
                <button
                  type="button"
                  data-testid="inbox-batch-reply-send"
                  class="min-h-[32px] rounded-[7px] bg-accent px-[11px] text-[11.5px] font-bold text-accent-on disabled:opacity-40"
                  :disabled="!batchReplyText.trim() || store.batchRunning"
                  @click="runBatchReply"
                >
                  {{ $t('redesign.inbox.batchReplySend', { count: checkedCount }) }}
                </button>
              </div>
            </div>
          </div>

          <!-- 다시 시도할 수 있는 실패 -->
          <div
            v-if="store.retryableFailures.length > 0"
            class="flex items-center gap-2 border-t border-line px-[13px] py-[8px]"
            data-testid="inbox-retry-bar"
          >
            <span class="flex-1 text-[11px] text-warn">{{ $t('redesign.inbox.retryFailed', { count: store.retryableFailures.length }) }}</span>
            <button
              type="button"
              data-testid="inbox-retry"
              class="min-h-[32px] rounded-[7px] border border-line-control px-[10px] text-[11px] text-content hover:border-line-hover disabled:opacity-40"
              :disabled="store.batchRunning"
              @click="runRetry"
            >
              {{ store.batchRunning ? $t('redesign.inbox.retrying') : $t('redesign.inbox.retryFailed', { count: store.retryableFailures.length }) }}
            </button>
          </div>

          <button
            v-if="store.pendingDrafts.length > 0 && !showDrafts"
            type="button"
            class="block w-full border-t border-line px-[13px] py-[8px] text-left text-[11.5px] font-semibold text-accent"
            data-testid="inbox-drafts-open"
            @click="showDrafts = true"
          >
            {{ $t('redesign.inbox.draftOpen', { count: store.pendingDrafts.length }) }}
          </button>
        </div>

        <div v-if="store.loading" class="p-[13px] text-[12px] text-content-tertiary">
          {{ $t('redesign.inbox.loading') }}
        </div>
        <div
          v-else-if="store.visibleThreads.length === 0"
          class="p-[13px] text-[12px] text-content-tertiary"
        >
          {{ $t('redesign.inbox.empty') }}
        </div>

        <div
          v-for="t in store.visibleThreads"
          :key="t.id"
          class="w-full cursor-pointer border-b border-line-row px-[13px] py-3 text-left transition-colors duration-[120ms] ease-out hover:bg-surface-raised"
          :class="t.id === store.selectedId ? 'bg-surface-raised shadow-[inset_2px_0_0_0_var(--accent-primary)]' : ''"
          role="button"
          tabindex="0"
          @click="store.select(t.id)"
          @keydown.enter="store.select(t.id)"
          @keydown.space.prevent="store.select(t.id)"
        >
          <div class="flex items-center gap-2">
            <input
              v-if="t.source === 'comment'"
              type="checkbox"
              class="h-[14px] w-[14px] shrink-0 accent-[var(--accent-primary)]"
              :checked="store.checkedIds.includes(t.id)"
              @click.stop
              @change="store.toggleChecked(t.id)"
            />
            <PlatformChip v-if="chipOf(t.platform)" :platform="chipOf(t.platform)!" size="sm" />
            <span class="min-w-0 flex-1 truncate text-[12.5px] font-semibold text-content">{{ t.author }}</span>
            <span class="shrink-0 font-mono text-[10px] text-content-tertiary">{{ relativeTime(t.time) }}</span>
          </div>
          <p class="mt-[6px] line-clamp-2 text-[12px] leading-[1.5] text-content-secondary">{{ t.text }}</p>
          <div class="mt-[7px] flex items-center gap-[7px]">
            <span class="min-w-0 flex-1 truncate text-[10.5px] text-content-tertiary">
              {{ t.videoId ? `#${t.videoId}` : '' }}
            </span>
            <StatusPill :variant="categoryOf(t).variant">{{ $t(`redesign.inbox.categories.${categoryOf(t).key}`) }}</StatusPill>
          </div>
          <p
            v-if="store.failures[t.id]"
            class="mt-[6px] text-[11px] leading-[1.45] text-error-strong"
            :data-testid="`inbox-failure-${t.id}`"
          >
            {{ $t('redesign.inbox.failedReason', { reason: store.failures[t.id].message ?? $t('redesign.inbox.failedUnknown') }) }}
            <span v-if="!store.failures[t.id].retryable" class="text-content-tertiary"> · {{ $t('redesign.inbox.notRetryable') }}</span>
          </p>
        </div>

        <button
          v-if="store.hasMoreComments"
          type="button"
          data-testid="inbox-load-more"
          class="m-[13px] min-h-[36px] rounded-[7px] border border-line-control text-[12px] text-content-secondary hover:border-line-hover hover:text-content disabled:opacity-40"
          :disabled="store.loadingMore"
          @click="loadMore"
        >
          {{ store.loadingMore ? $t('redesign.inbox.loadingMore') : $t('redesign.inbox.loadMore') }}
        </button>
      </section>

      <!-- 3열: 상세. <1024px 에서는 선택 시에만 보인다 -->
      <section
        class="min-h-0 flex-1 flex-col overflow-y-auto min-[1024px]:flex"
        :class="store.selectedThread || reviewingDrafts ? 'flex' : 'hidden'"
      >
        <!-- AI 초안 검토: 보내기 전까지 아무것도 게시되지 않는다 -->
        <div v-if="reviewingDrafts" class="flex flex-col gap-[12px] p-[18px]" data-testid="inbox-draft-review">
          <div class="flex items-center gap-2">
            <h2 class="flex-1 text-[13px] font-bold text-content">
              {{ $t('redesign.inbox.draftReviewTitle', { count: store.pendingDrafts.length }) }}
            </h2>
            <button type="button" class="min-h-[32px] px-[8px] text-[11px] text-content-secondary" @click="showDrafts = false">
              {{ $t('redesign.inbox.draftClose') }}
            </button>
            <button
              type="button"
              data-testid="inbox-drafts-send-all"
              class="min-h-[32px] rounded-[7px] bg-accent px-[11px] text-[11.5px] font-bold text-accent-on disabled:opacity-40"
              :disabled="store.batchRunning"
              @click="runSendDrafts()"
            >
              {{ $t('redesign.inbox.draftSendAll') }}
            </button>
          </div>
          <p class="text-[11px] text-content-tertiary">{{ $t('redesign.inbox.draftReviewHint') }}</p>
          <div
            v-for="d in store.pendingDrafts"
            :key="d.thread.id"
            class="rounded-[11px] border border-line bg-surface-card p-3"
            :data-testid="`inbox-draft-${d.thread.id}`"
          >
            <div class="mb-[5px] text-[11px] text-content-tertiary">{{ d.thread.author }} · {{ platformLabel(d.thread.platform) }}</div>
            <p class="mb-[8px] text-[12.5px] leading-[1.55] text-content-secondary">{{ d.thread.text }}</p>
            <textarea
              :value="d.text"
              rows="2"
              class="block w-full resize-y rounded-[8px] border border-line-control bg-surface-input px-[10px] py-[8px] text-[12.5px] text-content outline-none focus:border-accent"
              :aria-label="$t('redesign.inbox.draftReviewTitle', { count: 1 })"
              @input="store.updateDraft(d.thread.id, ($event.target as HTMLTextAreaElement).value)"
            />
            <p v-if="store.failures[d.thread.id]" class="mt-[6px] text-[11px] text-error-strong">
              {{ $t('redesign.inbox.failedReason', { reason: store.failures[d.thread.id].message ?? $t('redesign.inbox.failedUnknown') }) }}
            </p>
            <div class="mt-[8px] flex justify-end gap-[6px]">
              <button type="button" class="min-h-[32px] px-[8px] text-[11px] text-content-secondary" @click="store.discardDraft(d.thread.id)">
                {{ $t('redesign.inbox.draftDiscard') }}
              </button>
              <button
                type="button"
                class="min-h-[32px] rounded-[7px] border border-line-control px-[10px] text-[11.5px] text-content disabled:opacity-40"
                :disabled="store.batchRunning || !d.text.trim()"
                @click="runSendDrafts([d.thread.id])"
              >
                {{ $t('redesign.inbox.draftSend') }}
              </button>
            </div>
          </div>
        </div>

        <template v-else-if="store.selectedThread">
          <!-- 상세 헤더 -->
          <div class="flex items-center gap-[10px] border-b border-line px-[18px] py-[14px]">
            <button
              type="button"
              class="rounded-[7px] border border-line-control px-[9px] py-[6px] text-[11px] text-content-secondary min-[1024px]:hidden"
              @click="store.select('')"
            >
              {{ $t('redesign.inbox.back') }}
            </button>
            <div class="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-full bg-surface-raised text-[11px] text-content-secondary">
              {{ store.selectedThread.author.slice(0, 1) }}
            </div>
            <div class="min-w-0 flex-1">
              <div class="truncate text-[13px] font-bold text-content">{{ store.selectedThread.author }}</div>
              <div class="text-[11px] text-content-tertiary">
                {{ platformLabel(store.selectedThread.platform) }}
                <template v-if="store.selectedThread.likeCount > 0">
                  · {{ $t('redesign.inbox.likes', { count: store.selectedThread.likeCount }) }}
                </template>
              </div>
            </div>
            <div class="flex shrink-0 gap-[6px]">
              <button
                type="button"
                class="rounded-[7px] border border-line-control px-[10px] py-[6px] text-[11px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-line-hover hover:text-content"
                @click="runPin"
              >
                {{ store.selectedThread.isPinned ? $t('redesign.inbox.unpin') : $t('redesign.inbox.pin') }}
              </button>
              <button
                type="button"
                class="rounded-[7px] border border-line-control px-[10px] py-[6px] text-[11px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-line-hover hover:text-content"
                @click="runHide"
              >
                {{ $t('redesign.inbox.hide') }}
              </button>
            </div>
          </div>

          <div class="flex flex-1 flex-col gap-[14px] p-[18px]">
            <!-- 대상 게시물 -->
            <div v-if="store.selectedThread.videoId" class="flex items-start gap-3">
              <ThumbPlaceholder :width="78" :height="104" />
              <div class="min-w-0 flex-1">
                <div class="text-[12px] text-content-tertiary">{{ $t('redesign.inbox.targetPost') }}</div>
                <div class="mt-1 truncate text-[13px] font-semibold text-content">
                  {{ targetVideoTitle ?? `#${store.selectedThread.videoId}` }}
                </div>
              </div>
            </div>

            <!-- 메시지 스레드 -->
            <div class="rounded-[11px] border border-line bg-surface-card p-3">
              <div class="mb-[5px] text-[11px] text-content-tertiary">{{ store.selectedThread.author }}</div>
              <div class="text-[13px] leading-[1.65] text-content">{{ store.selectedThread.text }}</div>
            </div>
            <div
              v-if="store.selectedThread.replyContent"
              class="rounded-[11px] border border-line-row bg-surface-input p-3 opacity-[0.72]"
            >
              <div class="mb-[5px] text-[11px] text-content-tertiary">
                {{ $t('redesign.inbox.myReply') }} · {{ relativeTime(store.selectedThread.repliedAt) }}
              </div>
              <div class="text-[13px] leading-[1.65] text-content">{{ store.selectedThread.replyContent }}</div>
            </div>

            <!-- 답변 입력 (하단 고정) -->
            <div class="mt-auto overflow-hidden rounded-[11px] border border-line-control bg-surface-input">
              <textarea
                v-model="draft"
                rows="3"
                class="block min-h-[62px] w-full resize-y border-0 bg-transparent px-[13px] py-3 text-[13px] leading-[1.6] text-content outline-none placeholder:text-content-quaternary disabled:opacity-50"
                :placeholder="isDm ? $t('redesign.inbox.dmUnsupported') : $t('redesign.inbox.composerPlaceholder')"
                :disabled="isDm || store.sending"
                @keydown.meta.enter.prevent="send"
                @keydown.ctrl.enter.prevent="send"
              />
              <div class="flex flex-wrap items-center gap-[7px] border-t border-line px-[11px] py-[9px]">
                <button
                  v-if="!isDm"
                  type="button"
                  class="rounded-[6px] border border-line-control px-2 py-[5px] text-[11px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-accent hover:text-accent disabled:opacity-40"
                  :disabled="aiSuggesting"
                  @click="runAiSuggest"
                >
                  {{ aiSuggesting ? $t('redesign.inbox.aiSuggesting') : $t('redesign.inbox.aiSuggest') }}
                </button>
                <div class="relative">
                  <button
                    type="button"
                    class="rounded-[6px] border border-line-control px-2 py-[5px] text-[11px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-line-hover hover:text-content"
                    @click="savedMenuOpen = !savedMenuOpen"
                  >
                    {{ $t('redesign.inbox.savedReply') }}
                  </button>
                  <div
                    v-if="savedMenuOpen"
                    class="absolute bottom-full left-0 z-20 mb-1 w-44 overflow-hidden rounded-[8px] border border-line bg-surface-card"
                  >
                    <button
                      v-for="(m, i) in savedReplies"
                      :key="i"
                      type="button"
                      class="block w-full px-3 py-2 text-left text-[12px] text-content-secondary transition-colors duration-[120ms] ease-out hover:bg-surface-raised hover:text-content"
                      @click="insertSavedReply(m.body); savedMenuOpen = false"
                    >
                      {{ m.label }}
                    </button>
                  </div>
                </div>
                <div class="flex-1" />
                <span class="hidden font-mono text-[10px] text-content-tertiary min-[1024px]:inline">
                  {{ $t('redesign.inbox.sendHint') }}
                </span>
                <button
                  type="button"
                  class="rounded-[7px] bg-accent px-[13px] py-[7px] text-[12px] font-bold text-accent-on transition-[filter] duration-[120ms] ease-out hover:brightness-110 disabled:opacity-40"
                  :disabled="isDm || !draft.trim() || store.sending"
                  @click="send"
                >
                  {{ store.sending ? $t('redesign.inbox.sending') : $t('redesign.inbox.send') }}
                </button>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  INBOX_AI_DRAFT_MAX,
  INBOX_BATCH_MAX,
  useRedesignInboxStore,
  type InboxBatchOutcome,
  type InboxFilter,
  type InboxThread,
} from '@/stores/redesignInbox'
import { useAiFeaturePricing } from '@/composables/useAiFeaturePricing'
import { useNotificationStore } from '@/stores/notification'
import { videoApi } from '@/api/video'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import ThumbPlaceholder from '@/components/redesign/ThumbPlaceholder.vue'

const { t } = useI18n({ useScope: 'global' })
const store = useRedesignInboxStore()
const notify = useNotificationStore()

const FILTERS: InboxFilter[] = ['all', 'question', 'negative', 'dm', 'collab', 'done']
const SEARCH_DEBOUNCE_MS = 300

// 저장된 답변 — 문구는 i18n 으로 관리한다
interface SavedReply {
  label: string
  body: string
}
const SAVED_REPLY_KEYS = ['thanks', 'routine', 'collab', 'reupload'] as const
const savedReplies = computed<SavedReply[]>(() =>
  SAVED_REPLY_KEYS.map((key) => ({
    label: t(`redesign.inbox.savedReplies.${key}.label`),
    body: t(`redesign.inbox.savedReplies.${key}.body`),
  })),
)

const draft = ref('')
const aiSuggesting = ref(false)
const savedMenuOpen = ref(false)
const targetVideoTitle = ref<string | null>(null)

const pricing = useAiFeaturePricing()
const searchInput = ref('')
const batchReplyOpen = ref(false)
const batchReplyText = ref('')
const showDrafts = ref(false)

const checkedCount = computed(() => store.checkedIds.length)
const reviewingDrafts = computed(() => showDrafts.value && store.pendingDrafts.length > 0)

/** AI 초안 비용 — 서버 단가 × 선택 수. 단가를 모르면 null 이고 버튼을 열지 않는다(화면이 금액을 지어내지 않는다). */
const draftCredits = computed<number | null>(() => {
  const per = pricing.costOf('BATCH_REPLY_DRAFT')
  return per == null ? null : per * checkedCount.value
})

const isDm = computed(() => store.selectedThread?.source === 'dm')

// PlatformChip 이 지원하는 6개 플랫폼으로 매핑. 그 외는 칩 없이 텍스트만 보여 준다
type ChipPlatform = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH'
const CHIP_MAP: Record<string, ChipPlatform> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  NAVER: 'NV',
  THREADS: 'TH',
}
const PLATFORM_LABELS: Record<string, string> = {
  YOUTUBE: 'YouTube',
  INSTAGRAM: 'Instagram',
  TIKTOK: 'TikTok',
  FACEBOOK: 'Facebook',
  NAVER_CLIP: 'Naver',
  THREADS: 'Threads',
}

function chipOf(platform: string | null): ChipPlatform | null {
  return platform ? (CHIP_MAP[platform] ?? null) : null
}

function platformLabel(platform: string | null): string {
  return platform ? (PLATFORM_LABELS[platform] ?? platform) : '-'
}

/** 분류 배지 — 스토어와 같은 휴리스틱으로 행의 배지를 고른다 */
function categoryOf(t: InboxThread): { key: string; variant: 'success' | 'warning' | 'error' | 'muted' } {
  if (t.sentiment === 'negative') return { key: 'negative', variant: 'error' }
  if (/협업|제안|광고|sponsor|collab/i.test(t.text)) return { key: 'collab', variant: 'warning' }
  if (/\?|？/.test(t.text)) return { key: 'question', variant: 'muted' }
  return { key: 'normal', variant: 'muted' }
}

function relativeTime(iso: string | null): string {
  if (!iso) return ''
  const diffMs = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(diffMs / 60000)
  if (minutes < 60) return t('redesign.inbox.minutesAgo', { n: Math.max(minutes, 1) })
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('redesign.inbox.hoursAgo', { n: hours })
  return t('redesign.inbox.daysAgo', { n: Math.floor(hours / 24) })
}

function insertSavedReply(body: string) {
  if (isDm.value) return
  draft.value = body
}

async function send() {
  const thread = store.selectedThread
  if (!thread || thread.source === 'dm' || !draft.value.trim()) return
  try {
    await store.sendReply(thread, draft.value.trim())
    draft.value = ''
    notify.success(t('redesign.inbox.sent'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.inbox.sendFailed'))
  }
}

async function runAiSuggest() {
  const thread = store.selectedThread
  if (!thread || thread.source !== 'comment') return
  aiSuggesting.value = true
  try {
    const suggestion = await store.suggestReply(thread)
    if (suggestion) draft.value = suggestion
    else notify.error(t('redesign.inbox.aiSuggestEmpty'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.inbox.aiSuggestFailed'))
  } finally {
    aiSuggesting.value = false
  }
}

async function runPin() {
  const thread = store.selectedThread
  if (!thread) return
  try {
    await store.pinThread(thread)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.inbox.actionFailed'))
  }
}

async function runHide() {
  const thread = store.selectedThread
  if (!thread) return
  try {
    await store.hideThread(thread)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.inbox.actionFailed'))
  }
}

/** 일괄 결과를 사용자에게 알린다. 실패가 있으면 성공처럼 말하지 않는다. */
function reportOutcome(outcome: InboxBatchOutcome) {
  const params = { succeeded: outcome.succeeded, failed: outcome.failed }
  if (outcome.failed > 0) notify.error(t('redesign.inbox.batchResult', params))
  else if (outcome.succeeded > 0) notify.success(t('redesign.inbox.batchAllDone', params))
}

function errorMessage(e: unknown, fallbackKey: string): string {
  const server = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  if (typeof server === 'string' && server.trim()) return server
  return e instanceof Error && e.message ? e.message : t(fallbackKey)
}

async function runBatch() {
  try {
    reportOutcome(await store.batchHideChecked())
  } catch (e) {
    notify.error(errorMessage(e, 'redesign.inbox.actionFailed'))
  }
}

async function runBatchReply() {
  try {
    const outcome = await store.batchReplyChecked(batchReplyText.value)
    reportOutcome(outcome)
    // 다 나갔을 때만 입력을 비운다. 실패가 있으면 같은 문구로 다시 시도할 수 있게 남긴다.
    if (outcome.failed === 0) {
      batchReplyText.value = ''
      batchReplyOpen.value = false
    }
  } catch (e) {
    notify.error(errorMessage(e, 'redesign.inbox.sendFailed'))
  }
}

async function runRetry() {
  try {
    reportOutcome(await store.retryFailed())
  } catch (e) {
    notify.error(errorMessage(e, 'redesign.inbox.actionFailed'))
  }
}

async function runAiDrafts() {
  try {
    const result = await store.generateDrafts()
    if (result.created === 0) return
    showDrafts.value = true
    notify.success(
      result.created < result.requested
        ? t('redesign.inbox.aiDraftPartial', { created: result.created, requested: result.requested })
        : t('redesign.inbox.aiDraftCreated', { created: result.created, credits: result.creditsUsed }),
    )
  } catch (e) {
    // 크레딧 부족·권한·상한은 서버 문구를 그대로 보인다. 실패하면 서버가 크레딧을 돌려준다.
    notify.error(errorMessage(e, 'redesign.inbox.aiDraftFailed'))
  }
}

async function runSendDrafts(ids?: string[]) {
  reportOutcome(await store.sendDrafts(ids))
}

async function loadMore() {
  try {
    await store.loadMoreComments()
  } catch {
    notify.error(t('redesign.inbox.loadMoreFailed'))
  }
}

function onPlatformChange(value: string) {
  void store.setQuery({ platform: value || null })
}

// 입력을 멈춘 뒤에 서버 검색을 보낸다 — 글자마다 요청하지 않는다.
let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(searchInput, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void store.setQuery({ search: value })
  }, SEARCH_DEBOUNCE_MS)
})

// 대상 게시물 제목 — CommentResponse 에는 videoId 만 있어 따로 읽는다
watch(
  () => store.selectedThread?.videoId,
  async (videoId) => {
    targetVideoTitle.value = null
    if (videoId == null) return
    try {
      targetVideoTitle.value = (await videoApi.get(videoId)).title
    } catch {
      targetVideoTitle.value = null
    }
  },
)

// 스레드가 바뀌면 작성 중인 답변과 메뉴를 정리한다
watch(
  () => store.selectedId,
  () => {
    draft.value = ''
    savedMenuOpen.value = false
  },
)

// J/K 목록 이동, E 숨김(처리 완료). 입력 중에는 동작하지 않는다.
function onKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement
  if (target.tagName === 'TEXTAREA' || target.tagName === 'INPUT') return
  if (e.metaKey || e.ctrlKey || e.altKey) return
  if (e.key === 'j' || e.key === 'J') store.moveSelection(1)
  else if (e.key === 'k' || e.key === 'K') store.moveSelection(-1)
  else if (e.key === 'e' || e.key === 'E') runHide()
}

onMounted(() => {
  searchInput.value = store.search
  store.fetchAll()
  void pricing.load()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>
