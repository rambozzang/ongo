<template>
  <section class="space-y-3" :aria-label="t('creatorPro.calendar.operations')">
    <div class="flex flex-wrap items-center gap-2">
      <div
        class="flex rounded-lg border border-line-control bg-surface-card p-1"
        role="group"
        :aria-label="t('creatorPro.calendar.view')"
      >
        <button
          v-for="mode in modes"
          :key="mode"
          type="button"
          class="min-h-11 rounded-md px-3 text-xs font-semibold"
          :aria-pressed="store.view === mode"
          :class="
            store.view === mode
              ? 'bg-accent-dim text-accent'
              : 'text-content-secondary hover:bg-surface-raised'
          "
          :disabled="store.moving"
          @click="changeView(mode)"
        >
          {{ t(`creatorPro.calendar.${mode}`) }}
        </button>
      </div>
      <label class="flex min-w-[160px] flex-1 items-center gap-2">
        <span class="sr-only">{{ t('creatorPro.calendar.search') }}</span>
        <input
          v-model="store.search"
          type="search"
          class="min-h-11 w-full rounded-lg border border-line-control bg-surface-input px-3 text-xs text-content"
          :placeholder="t('creatorPro.calendar.search')"
        />
      </label>
      <label>
        <span class="sr-only">{{ t('creatorPro.calendar.platform') }}</span>
        <select
          v-model="store.platformFilter"
          class="min-h-11 rounded-lg border border-line-control bg-surface-input px-3 text-xs text-content"
        >
          <option value="">{{ t('creatorPro.calendar.allPlatforms') }}</option>
          <option v-for="platform in platforms" :key="platform" :value="platform">
            {{ platform }}
          </option>
        </select>
      </label>
      <label>
        <span class="sr-only">{{ t('creatorPro.calendar.status') }}</span>
        <select
          v-model="store.statusFilter"
          class="min-h-11 rounded-lg border border-line-control bg-surface-input px-3 text-xs text-content"
        >
          <option value="">{{ t('creatorPro.calendar.allStatuses') }}</option>
          <option v-for="status in statuses" :key="status" :value="status">
            {{ t(`redesign.calendar.status.${status}`) }}
          </option>
        </select>
      </label>
      <button
        type="button"
        class="btn-secondary min-h-11 text-xs"
        :disabled="!store.filteredSchedules.length || store.loading || store.loadError"
        @click="exportCalendar"
      >
        {{ t('creatorPro.calendar.export') }}
      </button>
    </div>
    <div
      class="flex flex-wrap items-center gap-x-5 gap-y-1 text-xs text-content-secondary"
      aria-live="polite"
    >
      <span>{{ t('creatorPro.calendar.results', { count: store.filteredSchedules.length }) }}</span>
      <span>{{
        t('creatorPro.calendar.pending', {
          count: store.filteredSchedules.filter((s) => s.status === 'SCHEDULED').length,
        })
      }}</span>
      <span class="text-warning-strong">{{
        t('creatorPro.calendar.attention', {
          count: store.filteredSchedules.filter((s) =>
            ['FAILED', 'UNCONFIRMED', 'PARTIALLY_PUBLISHED'].includes(s.status),
          ).length,
        })
      }}</span>
      <span class="ml-auto text-content-tertiary">Asia/Seoul (UTC+9)</span>
    </div>
    <div
      v-if="store.view === 'month'"
      class="overflow-x-auto rounded-xl border border-line bg-surface-card"
    >
      <div class="grid min-w-[700px] grid-cols-7">
        <div
          v-for="day in weekdayLabels"
          :key="day"
          class="border-b border-line px-2 py-2 text-xs font-semibold text-content-secondary"
        >
          {{ day }}
        </div>
        <div
          v-for="day in store.monthDays"
          :key="dateKey(day)"
          class="min-h-[110px] border-b border-r border-line-row p-2"
          :class="day.getMonth() === store.weekStart.getMonth() ? '' : 'bg-surface-input'"
        >
          <button
            type="button"
            class="min-h-8 text-xs font-semibold text-content-secondary hover:text-accent"
            :aria-label="t('redesign.calendar.addSchedule', { date: dateKey(day) })"
            @click="newContent(day)"
          >
            {{ dateKey(day).slice(5) }}
          </button>
          <button
            v-for="schedule in daySchedules(day)"
            :key="schedule.id"
            type="button"
            class="mb-1 block w-full rounded border border-line-control px-2 py-1.5 text-left text-xs text-content hover:border-accent"
            @click="openEditor(schedule)"
          >
            <span class="text-content-tertiary">{{ schedule.scheduledAt.slice(11, 16) }}</span>
            <span class="block truncate">{{ schedule.videoTitle }}</span
            ><span class="text-[10px] text-content-secondary">{{
              t(`redesign.calendar.status.${schedule.status}`)
            }}</span>
          </button>
        </div>
      </div>
    </div>
    <div
      v-if="store.view === 'list'"
      class="overflow-hidden rounded-xl border border-line bg-surface-card"
    >
      <div class="flex flex-wrap items-center gap-3 border-b border-line p-3">
        <label class="flex min-h-11 items-center gap-2 text-xs text-content"
          ><input
            type="checkbox"
            :checked="allSelected"
            :disabled="!editableSchedules.length || blocked"
            @change="selectAll"
          />{{ t('creatorPro.calendar.selectAll') }}</label
        >
        <span class="text-xs text-content-tertiary">{{
          t('creatorPro.calendar.selected', { count: selectedIds.length })
        }}</span>
        <label class="ml-auto flex items-center gap-2 text-xs text-content-secondary"
          >{{ t('creatorPro.calendar.shiftDays')
          }}<input
            v-model.number="offset"
            type="number"
            min="-365"
            max="365"
            class="min-h-11 w-20 rounded border border-line-control bg-surface-input px-2 text-content"
        /></label>
        <button
          type="button"
          class="btn-secondary min-h-11 text-xs"
          :disabled="!selectedIds.length || blocked || !validOffset"
          @click="requestBatch('shift')"
        >
          {{ t('creatorPro.calendar.shift') }}
        </button>
        <button
          type="button"
          class="min-h-11 px-3 text-xs font-semibold text-error-strong disabled:opacity-50"
          :disabled="!selectedIds.length || blocked"
          @click="requestBatch('cancel')"
        >
          {{ t('creatorPro.calendar.cancelSelected') }}
        </button>
      </div>
      <article
        v-for="schedule in store.filteredSchedules"
        :key="schedule.id"
        class="flex flex-wrap items-center gap-3 border-b border-line-row p-3 last:border-b-0"
      >
        <input
          v-if="schedule.status === 'SCHEDULED'"
          v-model="selectedIds"
          type="checkbox"
          :value="schedule.id"
          :disabled="blocked"
          :aria-label="t('creatorPro.calendar.select', { title: schedule.videoTitle })"
          class="h-4 w-4"
        />
        <div class="min-w-0 flex-1">
          <button
            type="button"
            class="min-h-8 text-left text-sm font-semibold text-content hover:text-accent"
            @click="router.push(`/videos/${schedule.videoId}`)"
          >
            {{ schedule.videoTitle }}
          </button>
          <div class="flex flex-wrap gap-2 text-xs text-content-secondary">
            <span>{{ schedule.scheduledAt.replace('T', ' ').slice(0, 16) }}</span
            ><span>{{ t(`redesign.calendar.status.${schedule.status}`) }}</span>
          </div>
          <ul class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-content-tertiary">
            <li
              v-for="target in schedule.platforms"
              :key="`${target.platform}-${target.channelId}`"
            >
              {{ target.platform }}<span v-if="target.channelId"> #{{ target.channelId }}</span> ·
              {{ target.scheduledAt.replace('T', ' ').slice(5, 16) }} ·
              {{ t(`redesign.calendar.status.${target.status ?? schedule.status}`) }}
            </li>
          </ul>
        </div>
        <button
          type="button"
          class="btn-secondary min-h-11 text-xs"
          :disabled="blocked"
          @click="openEditor(schedule)"
        >
          {{
            schedule.status === 'SCHEDULED'
              ? t('creatorPro.calendar.editTimes')
              : t('creatorPro.calendar.details')
          }}
        </button>
      </article>
      <p
        v-if="!store.filteredSchedules.length"
        class="px-4 py-8 text-center text-sm text-content-tertiary"
      >
        {{ t('creatorPro.calendar.empty') }}
      </p>
    </div>
    <div
      v-if="store.batchFailures.length"
      role="alert"
      class="rounded-lg border border-error-subtle bg-error-subtle p-3 text-xs text-error-strong"
    >
      <p class="font-semibold">{{ t('creatorPro.calendar.batchFailures') }}</p>
      <ul class="mt-1 space-y-1">
        <li v-for="failure in store.batchFailures" :key="failure.id">
          {{ failure.title }}: {{ failure.message }}
        </li>
      </ul>
      <p class="mt-2">{{ t('creatorPro.calendar.retryFailures') }}</p>
    </div>
    <section
      v-if="editing"
      class="rounded-xl border border-accent bg-surface-card p-4"
      :aria-label="t('creatorPro.calendar.editTimes')"
    >
      <div class="flex items-start justify-between gap-3">
        <div>
          <h2 class="text-sm font-bold text-content">{{ editing.videoTitle }}</h2>
          <p class="mt-1 text-xs text-content-tertiary">{{ t('creatorPro.calendar.editHint') }}</p>
        </div>
        <button
          type="button"
          class="min-h-11 px-2 text-xs text-content-secondary"
          :disabled="store.moving"
          @click="editing = null"
        >
          {{ t('action.close') }}
        </button>
      </div>
      <form class="mt-3 space-y-3" @submit.prevent="saveTimes">
        <label class="block text-xs text-content-secondary"
          >{{ t('creatorPro.calendar.baseTime')
          }}<input
            v-model="editBaseTime"
            type="datetime-local"
            required
            :disabled="editing.status !== 'SCHEDULED' || store.moving"
            class="mt-1 block min-h-11 rounded-lg border border-line-control bg-surface-input px-3 text-content"
        /></label>
        <label
          v-for="(target, index) in editTargets"
          :key="`${target.platform}-${target.channelId}`"
          class="flex flex-wrap items-center gap-3 text-xs text-content-secondary"
          ><span class="min-w-[120px]"
            >{{ target.platform }}
            <span v-if="target.channelId">#{{ target.channelId }}</span></span
          ><input
            v-model="editTargets[index].scheduledAt"
            type="datetime-local"
            required
            :disabled="editing.status !== 'SCHEDULED' || store.moving"
            class="min-h-11 rounded-lg border border-line-control bg-surface-input px-3 text-content"
        /></label>
        <p v-if="editError" role="alert" class="text-xs text-error-strong">{{ editError }}</p>
        <button
          v-if="editing.status === 'SCHEDULED'"
          type="submit"
          class="btn-primary min-h-11 text-xs"
          :disabled="blocked"
        >
          {{ store.moving ? t('creatorPro.calendar.saving') : t('action.save') }}
        </button>
      </form>
    </section>
    <ConfirmModal
      :model-value="batchAction !== null"
      :title="t('creatorPro.calendar.batchConfirmTitle')"
      :message="
        t(
          batchAction === 'cancel'
            ? 'creatorPro.calendar.cancelConfirm'
            : 'creatorPro.calendar.shiftConfirm',
          { count: selectedIds.length, days: offset },
        )
      "
      :danger="batchAction === 'cancel'"
      :confirm-text="t('action.confirm')"
      :cancel-text="t('action.cancel')"
      @confirm="runBatch"
      @update:model-value="batchAction = null"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useRedesignCalendarStore } from '@/stores/redesignCalendar'
import { useNotificationStore } from '@/stores/notification'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import type {
  CalendarView,
  Schedule,
  SchedulePlatformRequest,
  ScheduleStatus,
} from '@/types/schedule'
import { toDateStr } from '@/utils/schedule'
import { parseKstLocalDateTime } from '@/utils/kst'
import { downloadCreatorFile, schedulesIcs } from '@/utils/creatorExport'

const { t, locale } = useI18n()
const weekdayLabels = computed(() =>
  Array.from({ length: 7 }, (_, index) =>
    new Intl.DateTimeFormat(locale.value, { weekday: 'short', timeZone: 'UTC' }).format(
      new Date(Date.UTC(2026, 0, 5 + index)),
    ),
  ),
)
const store = useRedesignCalendarStore()
const router = useRouter()
const route = useRoute()
const notify = useNotificationStore()
const modes: CalendarView[] = ['week', 'month', 'list']
const statuses: ScheduleStatus[] = [
  'SCHEDULED',
  'PROCESSING',
  'PUBLISHED',
  'PARTIALLY_PUBLISHED',
  'UNCONFIRMED',
  'FAILED',
  'CANCELLED',
]
const selectedIds = ref<number[]>([])
const offset = ref(1)
const batchAction = ref<'cancel' | 'shift' | null>(null)
const editing = ref<Schedule | null>(null)
const editBaseTime = ref('')
const editTargets = ref<SchedulePlatformRequest[]>([])
const editError = ref('')
const blocked = computed(() => store.loading || store.moving || store.loadError)
const platforms = computed(() =>
  [...new Set(store.schedules.flatMap((s) => s.platforms.map((p) => p.platform)))].sort(),
)
const editableSchedules = computed(() =>
  store.filteredSchedules.filter((s) => s.status === 'SCHEDULED'),
)
const allSelected = computed(
  () =>
    editableSchedules.value.length > 0 &&
    editableSchedules.value.every((s) => selectedIds.value.includes(s.id)),
)
const validOffset = computed(
  () => Number.isInteger(offset.value) && offset.value !== 0 && Math.abs(offset.value) <= 365,
)
const dateKey = toDateStr

watch(
  () => route.query.view,
  (mode) => {
    const next = modes.includes(mode as CalendarView) ? (mode as CalendarView) : 'week'
    if (store.view !== next) void store.setView(next)
  },
  { immediate: true },
)
watch(
  () => store.filteredSchedules,
  (items) => {
    const visibleIds = new Set(items.filter((s) => s.status === 'SCHEDULED').map((s) => s.id))
    selectedIds.value = selectedIds.value.filter((id) => visibleIds.has(id))
  },
)
function changeView(mode: CalendarView) {
  void router.replace({ query: { ...route.query, view: mode } })
}
function daySchedules(day: Date) {
  return store.filteredSchedules.filter((s) => s.scheduledAt.slice(0, 10) === dateKey(day))
}
function newContent(day: Date) {
  void router.push({ path: '/compose', query: { at: `${dateKey(day)}T09:00` } })
}
function selectAll() {
  selectedIds.value = allSelected.value ? [] : editableSchedules.value.map((s) => s.id)
}
function requestBatch(action: 'cancel' | 'shift') {
  if (!blocked.value && selectedIds.value.length) batchAction.value = action
}
async function runBatch() {
  const action = batchAction.value
  batchAction.value = null
  if (!action || blocked.value) return
  const result = await store.batchSchedules([...selectedIds.value], action, offset.value)
  selectedIds.value = result.failed.map((f) => f.id)
  const message = t('creatorPro.calendar.batchResult', {
    count: result.succeeded.length,
    failed: result.failed.length,
  })
  if (result.failed.length) notify.warning(message)
  else notify.success(message)
}
function openEditor(schedule: Schedule) {
  editing.value = schedule
  editBaseTime.value = schedule.scheduledAt.slice(0, 16)
  editTargets.value = schedule.platforms.map((p) => ({
    platform: p.platform,
    ...(p.channelId != null ? { channelId: p.channelId } : {}),
    scheduledAt: (p.scheduledAt || schedule.scheduledAt).slice(0, 16),
  }))
  editError.value = ''
}
async function saveTimes() {
  if (!editing.value || editing.value.status !== 'SCHEDULED' || blocked.value) return
  editError.value = ''
  const times = [editBaseTime.value, ...editTargets.value.map((p) => p.scheduledAt ?? '')]
  if (
    times.some(
      (value) =>
        !Number.isFinite(parseKstLocalDateTime(value).getTime()) ||
        parseKstLocalDateTime(value).getTime() <= Date.now(),
    )
  ) {
    editError.value = t('creatorPro.calendar.futureOnly')
    return
  }
  try {
    await store.editSchedule(editing.value.id, {
      scheduledAt: editBaseTime.value,
      platforms: editTargets.value,
    })
    editing.value = null
    notify.success(t('creatorPro.calendar.saved'))
  } catch (error) {
    editError.value = error instanceof Error ? error.message : t('creatorPro.calendar.saveFailed')
  }
}
function exportCalendar() {
  downloadCreatorFile(
    schedulesIcs(store.filteredSchedules),
    'ongo-content-calendar.ics',
    'text/calendar;charset=utf-8',
  )
}
</script>
