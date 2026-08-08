<template>
  <div class="flex h-full min-h-0 flex-col gap-[14px] overflow-y-auto px-5 py-[18px]">
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

    <!-- 주간 그리드 -->
    <div class="overflow-x-auto rounded-[12px] border border-line bg-surface-card">
      <div class="min-w-[840px]">
        <div class="grid grid-cols-7 border-b border-line">
          <div
            v-for="(day, i) in store.days"
            :key="i"
            class="px-[9px] py-[10px] text-[11.5px]"
            :class="isToday(day) ? 'bg-accent-dim font-bold text-content' : 'text-content-secondary'"
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
              <div class="flex items-center gap-[6px]">
                <PlatformChip
                  v-if="chipOf(firstPlatform(s))"
                  :platform="chipOf(firstPlatform(s))!"
                  size="sm"
                />
                <span class="font-mono text-[10px] text-content-secondary">{{ timeOf(s.scheduledAt) }}</span>
                <StatusPill v-if="s.status === 'FAILED'" variant="error">
                  {{ $t('redesign.calendar.failed') }}
                </StatusPill>
              </div>
              <div class="mt-[6px] text-[11.5px] font-semibold leading-[1.35] text-content">
                {{ s.videoTitle }}
              </div>
            </div>

            <!-- 빈 슬롯: 클릭하면 해당 일시로 컴포저 프리필 -->
            <button
              type="button"
              class="flex min-h-[34px] items-center justify-center rounded-[8px] border border-dashed border-line-soft text-[11px] text-content-quaternary transition-colors duration-[120ms] ease-out hover:border-accent hover:text-accent"
              @click="goCompose(day)"
            >
              +
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useRedesignCalendarStore } from '@/stores/redesignCalendar'
import { useNotificationStore } from '@/stores/notification'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import { toDateStr, toDateTimeLocal } from '@/utils/schedule'
import type { Schedule } from '@/types/schedule'

const { t } = useI18n({ useScope: 'global' })
const router = useRouter()
const store = useRedesignCalendarStore()
const notify = useNotificationStore()

const WEEKDAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const

// PlatformChip 이 지원하는 6개 플랫폼으로 매핑. 그 외는 칩 없이 시간만 보여 준다
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


const weekTitle = computed(() => {
  const d = store.weekStart
  return t('redesign.calendar.weekTitle', {
    year: d.getFullYear(),
    month: d.getMonth() + 1,
    day: d.getDate(),
  })
})

function chipOf(platform: string | null | undefined): ChipPlatform | null {
  return platform ? (CHIP_MAP[platform] ?? null) : null
}

function firstPlatform(s: Schedule): string | null {
  return s.platforms[0]?.platform ?? null
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

async function moveTo(scheduleId: number, next: Date) {
  const current = store.schedules.find((s) => s.id === scheduleId)
  if (!current) return
  // 같은 시각이면 호출하지 않는다
  if (new Date(current.scheduledAt).getTime() === next.getTime()) return
  const confirmed = window.confirm(t('redesign.calendar.confirmMove', {
    title: current.videoTitle,
    time: timeOf(next.toISOString()),
  }))
  if (!confirmed) return
  try {
    await store.moveSchedule(scheduleId, next)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('redesign.calendar.moveFailed'))
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

onMounted(() => {
  store.fetchWeek()
})
</script>
