<template>
  <div class="flex h-full min-h-0 flex-col gap-[14px] overflow-y-auto px-5 py-[18px]">
    <ConfirmModal
      :model-value="pendingAction !== null"
      :title="confirmationTitle"
      :message="confirmationMessage"
      :confirm-text="
        pendingAction?.type === 'delete'
          ? t('redesign.calendar.recurringDelete')
          : pendingAction?.type === 'cancel'
            ? t('redesign.calendar.cancelScheduleAction')
            : t('action.confirm')
      "
      :danger="pendingAction?.type === 'delete' || pendingAction?.type === 'cancel'"
      @update:model-value="clearPendingAction"
      @confirm="confirmPendingAction"
    />

    <!-- 상단 컨트롤 -->
    <div class="flex flex-wrap items-center gap-[10px]">
      <div class="flex items-center gap-1">
        <button
          type="button"
          class="rounded-[7px] border border-line-control px-[9px] py-[6px] text-[12px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-line-hover hover:text-content"
          :aria-label="$t('redesign.calendar.prevWeek')"
          @click="store.shiftWeek(-1)"
        >
          <ChevronLeftIcon class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="rounded-[7px] border border-line-control px-[9px] py-[6px] text-[12px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-line-hover hover:text-content"
          :aria-label="$t('redesign.calendar.nextWeek')"
          @click="store.shiftWeek(1)"
        >
          <ChevronRightIcon class="h-4 w-4" />
        </button>
      </div>
      <div class="whitespace-nowrap text-[13px] font-bold text-content">{{ weekTitle }}</div>
      <div class="text-[11.5px] text-content-tertiary">{{ $t('redesign.calendar.hint') }}</div>
      <div class="flex-1" />
      <button
        type="button"
        class="whitespace-nowrap rounded-[7px] border border-line-control px-[10px] py-[6px] text-[11.5px] text-content-secondary transition-colors duration-[120ms] ease-out hover:border-line-hover hover:text-content"
        @click="store.goToday()"
      >
        {{ $t('redesign.calendar.today') }}
      </button>
    </div>

    <div
      v-if="store.loadError"
      class="flex flex-wrap items-center gap-2 rounded-[8px] border border-error-subtle bg-error-subtle px-4 py-3 text-[12px] text-error-strong"
      role="alert"
    >
      <span class="min-w-0 flex-1">{{ $t('redesign.calendar.loadFailed') }}</span>
      <button
        type="button"
        class="shrink-0 rounded-md border border-error-strong px-2 py-1 text-[11px] font-semibold transition-colors hover:bg-error-strong hover:text-surface-base disabled:opacity-50"
        :disabled="store.loading"
        @click="store.fetchWeek"
      >
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- 추천을 실제 예약과 연결한다. 적용 전에는 서버의 PENDING 상태만 보여준다. -->
    <section
      v-if="store.recommendations.length > 0 || store.recommendationsError"
      class="rounded-[12px] border border-line bg-surface-card p-4"
      aria-labelledby="schedule-recommendations-title"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="schedule-recommendations-title" class="text-[13px] font-bold text-content">
            {{ t('redesign.calendar.autoArrange') }}
          </h2>
          <p class="mt-1 text-[11px] text-content-tertiary">
            {{ t('redesign.calendar.autoArrangeDescription') }}
          </p>
        </div>
        <button
          type="button"
          class="rounded-[7px] border border-line-control px-[10px] py-[6px] text-[11.5px] text-content-secondary transition-colors hover:border-line-hover hover:text-content disabled:opacity-50"
          :disabled="store.applyingRecommendationId !== null"
          @click="refreshRecommendations"
        >
          {{ t('action.retry') }}
        </button>
      </div>
      <div
        v-if="store.recommendationsError"
        class="mt-3 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2 text-[11px] text-error-strong"
        role="alert"
      >
        {{ t('redesign.calendar.autoArrangeFailed') }}
      </div>
      <div v-else class="mt-3 space-y-2">
        <article
          v-for="recommendation in pendingRecommendations"
          :key="recommendation.id"
          class="flex flex-wrap items-center gap-3 rounded-lg border border-line-row px-3 py-2.5"
        >
          <div class="min-w-0 flex-1">
            <p class="truncate text-[12px] font-semibold text-content">
              {{ recommendation.videoTitle }} · {{ recommendation.platform }}
              <span v-if="recommendation.channelId" class="font-normal text-content-tertiary">
                · {{ channelNameForRecommendation(recommendation) }}
              </span>
            </p>
            <p class="mt-1 text-[11px] text-content-secondary">
              {{ formatRecommendationDate(recommendation.recommendedSchedule) }} ·
              {{ t('redesign.calendar.autoArrangeConfidence', { count: recommendation.confidence }) }}
            </p>
          </div>
          <button
            type="button"
            class="shrink-0 rounded-[7px] bg-accent px-3 py-1.5 text-[11px] font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            :disabled="store.applyingRecommendationId !== null"
            @click="applyRecommendation(recommendation.id)"
          >
            {{
              store.applyingRecommendationId === recommendation.id
                ? t('redesign.calendar.autoArranging')
                : t('redesign.calendar.autoArrangeApply')
            }}
          </button>
        </article>
        <p v-if="pendingRecommendations.length === 0" class="text-[11.5px] text-content-tertiary">
          {{ t('redesign.calendar.autoArrangeEmpty') }}
        </p>
      </div>
    </section>

    <!-- 주간 그리드 -->
    <div class="overflow-x-auto rounded-[12px] border border-line bg-surface-card">
      <div class="min-w-[840px]">
        <div class="grid grid-cols-7 border-b border-line">
          <div
            v-for="(day, i) in store.days"
            :key="i"
            class="px-[9px] py-[10px] text-[11.5px]"
            :class="
              isToday(day) ? 'bg-accent-dim font-bold text-content' : 'text-content-secondary'
            "
          >
            {{ $t(`redesign.calendar.weekdays.${WEEKDAY_KEYS[i]}`) }}
            <span class="ml-1 font-mono text-[10px]">{{ day.getDate() }}</span>
          </div>
        </div>

        <div class="grid min-h-[560px] grid-cols-7">
          <div
            v-for="(day, i) in store.days"
            :key="i"
            class="flex flex-col gap-[7px] border-r border-line-row p-[9px] last:border-r-0"
            @dragover.prevent
            @drop="onDropToDay(day, $event)"
          >
            <div v-if="store.loading && i === 0" class="text-[11px] text-content-tertiary">
              {{ $t('redesign.calendar.loading') }}
            </div>

            <div
              v-for="s in schedulesOf(day)"
              :key="s.id"
              draggable="true"
              class="cursor-grab rounded-[8px] border border-line-control bg-surface-raised px-[9px] py-2 transition-[border-color] duration-[120ms] ease-out hover:border-accent"
              :class="store.moving ? 'opacity-60' : ''"
              @dragstart="onDragStart(s, $event)"
              @dragover.prevent.stop
              @drop.stop="onDropToBlock(day, s, $event)"
            >
              <div class="flex items-start gap-[6px]">
                <button
                  type="button"
                  class="schedule-open min-w-0 flex-1 text-left"
                  :aria-label="`${s.videoTitle}, ${timeOf(s.scheduledAt)}`"
                  @click="openSchedule(s)"
                >
                  <span class="flex items-center gap-[6px]">
                    <PlatformChip
                      v-if="chipOf(firstPlatform(s))"
                      :platform="chipOf(firstPlatform(s))!"
                      size="sm"
                    />
                    <span class="font-mono text-[10px] text-content-secondary">{{
                      timeOf(s.scheduledAt)
                    }}</span>
                    <StatusPill v-if="s.status !== 'SCHEDULED'" :variant="statusVariant(s.status)">
                      {{ statusLabel(s.status) }}
                    </StatusPill>
                  </span>
                  <span class="mt-[6px] block text-[11.5px] font-semibold leading-[1.35] text-content">
                    {{ s.videoTitle }}
                  </span>
                </button>
                <button
                  v-if="s.status === 'SCHEDULED'"
                  type="button"
                  class="shrink-0 rounded-md p-1 text-content-quaternary transition-colors hover:bg-error-subtle hover:text-error-strong"
                  :aria-label="t('redesign.calendar.cancelSchedule', { title: s.videoTitle })"
                  :disabled="store.moving"
                  @click.stop="requestCancel(s)"
                >
                  ×
                </button>
              </div>
              <a
                v-if="firstPlatformUrl(s)"
                :href="firstPlatformUrl(s)!"
                target="_blank"
                rel="noopener noreferrer"
                class="mt-1 inline-block text-[10.5px] font-semibold text-accent hover:underline"
                @click.stop
              >
                {{ t('redesign.calendar.openPublished') }}
              </a>
            </div>

            <!-- 빈 슬롯: 클릭하면 해당 일시로 컴포저 프리필 -->
            <button
              type="button"
              class="flex min-h-[34px] items-center justify-center rounded-[8px] border border-dashed border-line-soft text-[11px] text-content-quaternary transition-colors duration-[120ms] ease-out hover:border-accent hover:text-accent"
              :aria-label="t('redesign.calendar.addSchedule', { date: toDateStr(day) })"
              @click="goCompose(day)"
            >
              +
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 반복 게시 정의: 캘린더에서 생성된 서버 상태를 바로 관리한다 -->
    <section
      class="rounded-[12px] border border-line bg-surface-card p-4"
      aria-labelledby="recurring-schedules-title"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="recurring-schedules-title" class="text-[13px] font-bold text-content">
            {{ t('redesign.calendar.recurringTitle') }}
          </h2>
          <p class="mt-1 text-[11px] text-content-tertiary">
            {{ t('redesign.calendar.recurringDescription') }}
          </p>
        </div>
        <button
          type="button"
          class="rounded-[7px] border border-line-control px-[10px] py-[6px] text-[11.5px] text-content-secondary transition-colors hover:border-line-hover hover:text-content disabled:opacity-50"
          :disabled="recurringLoading"
          @click="loadRecurring"
        >
          {{ t('action.retry') }}
        </button>
      </div>

      <div
        v-if="recurringError"
        class="mt-3 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2 text-[11px] text-error-strong"
        role="alert"
      >
        {{ recurringError }}
      </div>
      <p v-else-if="recurringLoading" class="mt-4 text-[11.5px] text-content-tertiary">
        {{ t('redesign.calendar.recurringLoading') }}
      </p>
      <p
        v-else-if="recurringSchedules.length === 0"
        class="mt-4 rounded-lg border border-dashed border-line-soft px-3 py-4 text-center text-[11.5px] text-content-tertiary"
      >
        {{ t('redesign.calendar.recurringEmpty') }}
      </p>
      <div v-else class="mt-3 divide-y divide-line-row">
        <article
          v-for="schedule in recurringSchedules"
          :key="schedule.id"
          class="flex flex-wrap items-center gap-3 py-3 first:pt-0 last:pb-0"
        >
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h3 class="truncate text-[12px] font-semibold text-content">{{ schedule.name }}</h3>
              <StatusPill :variant="schedule.isActive ? 'success' : 'muted'">
                {{
                  schedule.isActive
                    ? t('redesign.calendar.recurringActive')
                    : t('redesign.calendar.recurringPaused')
                }}
              </StatusPill>
            </div>
            <p class="mt-1 text-[11px] text-content-secondary">
              {{ recurringSummary(schedule) }}
            </p>
            <p class="mt-1 text-[10.5px] text-content-tertiary">
              {{ t('redesign.calendar.recurringNextRun') }}:
              {{ formatRecurringDate(schedule.nextRunAt) }} · {{ schedule.platforms.join(', ') }}
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <button
              type="button"
              class="rounded-[7px] border border-line-control px-2.5 py-1.5 text-[11px] text-content-secondary transition-colors hover:border-line-hover hover:text-content disabled:opacity-50"
              :disabled="recurringBusyId === schedule.id"
              @click="toggleRecurring(schedule)"
            >
              {{
                schedule.isActive
                  ? t('redesign.calendar.recurringPause')
                  : t('redesign.calendar.recurringResume')
              }}
            </button>
            <button
              type="button"
              class="rounded-[7px] border border-error-subtle px-2.5 py-1.5 text-[11px] text-error-strong transition-colors hover:bg-error-subtle disabled:opacity-50"
              :disabled="recurringBusyId === schedule.id"
              @click="removeRecurring(schedule)"
            >
              {{ t('redesign.calendar.recurringDelete') }}
            </button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useRedesignCalendarStore } from '@/stores/redesignCalendar'
import { useChannelStore } from '@/stores/channel'
import { useNotificationStore } from '@/stores/notification'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { toDateStr, toDateTimeLocal } from '@/utils/schedule'
import type { Schedule } from '@/types/schedule'
import { recurringApi, type RecurringSchedule } from '@/api/recurring'
import type { ScheduleRecommendation } from '@/api/scheduleOptimizer'

const { t } = useI18n({ useScope: 'global' })
const router = useRouter()
const store = useRedesignCalendarStore()
const channelStore = useChannelStore()
const notify = useNotificationStore()
const recurringSchedules = ref<RecurringSchedule[]>([])
const recurringLoading = ref(false)
const recurringError = ref('')
const recurringBusyId = ref<number | null>(null)

type PendingCalendarAction =
  | { type: 'move'; scheduleId: number; next: Date; title: string; time: string }
  | { type: 'cancel'; schedule: Schedule }
  | { type: 'delete'; schedule: RecurringSchedule }

const pendingAction = ref<PendingCalendarAction | null>(null)
const pendingRecommendations = computed<ScheduleRecommendation[]>(() =>
  store.recommendations.filter((recommendation) => recommendation.status === 'PENDING'),
)

const WEEKDAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const

// PlatformChip 이 지원하는 7개 게시 플랫폼으로 매핑한다.
type ChipPlatform = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW'
const CHIP_MAP: Record<string, ChipPlatform> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  NAVER: 'NV',
  THREADS: 'TH',
  TWITTER: 'TW',
}

const weekTitle = computed(() => {
  const d = store.weekStart
  return t('redesign.calendar.weekTitle', {
    year: d.getFullYear(),
    month: d.getMonth() + 1,
    day: d.getDate(),
  })
})

const confirmationTitle = computed(() =>
  pendingAction.value?.type === 'delete'
    ? t('redesign.calendar.recurringConfirmTitle')
    : pendingAction.value?.type === 'cancel'
      ? t('redesign.calendar.cancelConfirmTitle')
      : t('redesign.calendar.confirmMoveTitle'),
)

const confirmationMessage = computed(() => {
  const action = pendingAction.value
  if (!action) return ''
  if (action.type === 'delete') {
    return t('redesign.calendar.recurringConfirmDelete', { name: action.schedule.name })
  }
  if (action.type === 'cancel') {
    return t('redesign.calendar.cancelConfirm', { title: action.schedule.videoTitle })
  }
  return t('redesign.calendar.confirmMove', { title: action.title, time: action.time })
})

function chipOf(platform: string | null | undefined): ChipPlatform | null {
  return platform ? (CHIP_MAP[platform] ?? null) : null
}

function firstPlatform(s: Schedule): string | null {
  return s.platforms[0]?.platform ?? null
}

function firstPlatformUrl(s: Schedule): string | null {
  return s.platforms.find((platform) => platform.platformUrl)?.platformUrl ?? null
}

function statusVariant(status: Schedule['status']): 'success' | 'warning' | 'error' | 'muted' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'UNCONFIRMED' || status === 'PARTIALLY_PUBLISHED') return 'warning'
  return 'muted'
}

function statusLabel(status: Schedule['status']): string {
  return t(`redesign.calendar.status.${status}`)
}

/** 로컬 날짜가 같은 예약만 시간순으로 */
function schedulesOf(day: Date): Schedule[] {
  const key = toDateStr(day)
  return store.schedules
    .filter((s) => toDateStr(new Date(s.scheduledAt)) === key)
    .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))
}

function timeOf(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function isToday(day: Date): boolean {
  return toDateStr(day) === toDateStr(new Date())
}

// ---- 드래그 이동 ----
// 같은 날 다른 블록 위에 놓으면 그 블록의 시각을 따르고, 빈 곳에 놓으면 시각은 유지한 채 요일만 바꾼다
function onDragStart(s: Schedule, e: DragEvent) {
  e.dataTransfer?.setData('text/plain', String(s.id))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

function moveTo(scheduleId: number, next: Date) {
  const current = store.schedules.find((s) => s.id === scheduleId)
  if (!current) return
  // 같은 시각이면 호출하지 않는다
  if (new Date(current.scheduledAt).getTime() === next.getTime()) return
  pendingAction.value = {
    type: 'move',
    scheduleId,
    next,
    title: current.videoTitle,
    time: timeOf(next.toISOString()),
  }
}

function onDropToDay(day: Date, e: DragEvent) {
  const id = Number(e.dataTransfer?.getData('text/plain'))
  const s = store.schedules.find((item) => item.id === id)
  if (!s) return
  const old = new Date(s.scheduledAt)
  const next = new Date(day)
  next.setHours(old.getHours(), old.getMinutes(), 0, 0)
  moveTo(id, next)
}

function onDropToBlock(day: Date, target: Schedule, e: DragEvent) {
  const id = Number(e.dataTransfer?.getData('text/plain'))
  if (id === target.id) return
  const targetTime = new Date(target.scheduledAt)
  const next = new Date(day)
  next.setHours(targetTime.getHours(), targetTime.getMinutes(), 0, 0)
  moveTo(id, next)
}

/** 빈 슬롯 → 컴포저로 이동. 시각은 시안 기본 최적 시간인 09:00 로 프리필한다 */
function goCompose(day: Date) {
  const at = new Date(day)
  at.setHours(9, 0, 0, 0)
  router.push({ path: '/compose', query: { at: toDateTimeLocal(at) } })
}

function openSchedule(schedule: Schedule) {
  router.push(`/videos/${schedule.videoId}`)
}

function recurringSummary(schedule: RecurringSchedule): string {
  const frequency = t(`redesign.calendar.recurringFrequency.${schedule.frequency}`)
  const day =
    schedule.frequency === 'MONTHLY'
      ? t('redesign.calendar.recurringDayOfMonth', { day: schedule.dayOfMonth ?? 1 })
      : schedule.frequency === 'WEEKLY' || schedule.frequency === 'BIWEEKLY'
        ? t(`redesign.calendar.weekdays.${WEEKDAY_KEYS[(schedule.dayOfWeek ?? 1) - 1]}`)
        : ''
  return [frequency, day, schedule.timeOfDay.slice(0, 5)].filter(Boolean).join(' · ')
}

function formatRecurringDate(value: string | null): string {
  if (!value) return t('redesign.calendar.recurringNotScheduled')
  return value.replace('T', ' ').slice(0, 16)
}

function formatRecommendationDate(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

function channelNameForRecommendation(recommendation: ScheduleRecommendation): string {
  const channel = recommendation.channelId
    ? channelStore.channels.find((item) => item.id === recommendation.channelId)
    : null
  return channel?.channelName ?? t('redesign.calendar.autoArrangeChannelFallback', { id: recommendation.channelId })
}

async function refreshRecommendations() {
  try {
    await store.fetchOptimalRecommendations()
  } catch {
    // The store exposes the server error without replacing confirmed data.
  }
}

async function applyRecommendation(id: number) {
  try {
    await store.applyRecommendation(id)
    notify.success(t('redesign.calendar.autoArrangeApplied'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.calendar.autoArrangeFailed'))
  }
}

async function loadRecurring() {
  recurringLoading.value = true
  recurringError.value = ''
  try {
    recurringSchedules.value = await recurringApi.list()
  } catch (e) {
    recurringError.value =
      e instanceof Error ? e.message : t('redesign.calendar.recurringLoadFailed')
  } finally {
    recurringLoading.value = false
  }
}

async function toggleRecurring(schedule: RecurringSchedule) {
  recurringBusyId.value = schedule.id
  try {
    const updated = await recurringApi.toggle(schedule.id)
    const index = recurringSchedules.value.findIndex((item) => item.id === schedule.id)
    if (index >= 0) recurringSchedules.value[index] = updated
    notify.success(
      updated.isActive
        ? t('redesign.calendar.recurringResumed')
        : t('redesign.calendar.recurringPausedNotice'),
    )
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.calendar.recurringActionFailed'))
  } finally {
    recurringBusyId.value = null
  }
}

function removeRecurring(schedule: RecurringSchedule) {
  pendingAction.value = { type: 'delete', schedule }
}

function requestCancel(schedule: Schedule) {
  pendingAction.value = { type: 'cancel', schedule }
}

function clearPendingAction() {
  pendingAction.value = null
}

async function confirmPendingAction() {
  const action = pendingAction.value
  clearPendingAction()
  if (!action) return

  try {
    if (action.type === 'move') {
      await store.moveSchedule(action.scheduleId, action.next)
    } else if (action.type === 'cancel') {
      await store.cancelSchedule(action.schedule.id)
      notify.success(t('redesign.calendar.scheduleCancelled'))
    } else {
      recurringBusyId.value = action.schedule.id
      await recurringApi.remove(action.schedule.id)
      recurringSchedules.value = recurringSchedules.value.filter(
        (item) => item.id !== action.schedule.id,
      )
      notify.success(t('redesign.calendar.recurringDeleted'))
    }
  } catch (e) {
    notify.error(
      e instanceof Error
        ? e.message
        : action.type === 'move'
          ? t('redesign.calendar.moveFailed')
          : action.type === 'cancel'
            ? t('redesign.calendar.cancelFailed')
            : t('redesign.calendar.recurringActionFailed'),
    )
  } finally {
    if (action.type === 'delete') recurringBusyId.value = null
  }
}

onMounted(() => {
  store.fetchWeek()
  channelStore.fetchChannels()
  loadRecurring()
  refreshRecommendations()
})
</script>
