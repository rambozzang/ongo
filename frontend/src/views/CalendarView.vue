<template>
  <div>
    <!-- 헤더 -->
    <PageHeader :title="$t('calendar.title')" :description="$t('calendar.description')">
      <template #actions>
        <button class="btn-secondary inline-flex items-center gap-2" @click="showAiOptimalModal = true">
          <SparklesIcon class="h-4 w-4" />
          {{ $t('calendar.aiOptimalTime') }}
        </button>
        <button class="btn-primary inline-flex items-center gap-2" @click="showAiCalendarModal = true">
          <SparklesIcon class="h-4 w-4" />
          {{ $t('calendar.aiCalendarGenerate') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('calendar.pageGuideTitle')" :items="($tm('calendar.pageGuide') as string[])" />

    <!-- 캘린더 / 리스트 뷰 전환 -->
    <OTabs
      v-model="activeTab"
      :tabs="viewTabs"
      :aria-label="$t('calendar.title')"
      class="mb-4"
    />

    <!-- 뷰별 보조 컨트롤 -->
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <!-- 캘린더: 월간/주간 -->
      <OTabs
        v-if="activeTab === 'calendar'"
        v-model="calendarMode"
        :tabs="calendarModeTabs"
        variant="pill"
        :aria-label="$t('calendar.monthly')"
      />

      <!-- 리스트: 정렬 -->
      <select v-else v-model="sortMode" class="input-field w-auto py-1.5 text-sm">
        <option value="date">{{ $t('scheduleView.sort.date') }}</option>
        <option value="platform">{{ $t('scheduleView.sort.platform') }}</option>
        <option value="status">{{ $t('scheduleView.sort.status') }}</option>
        <option value="manual">{{ $t('scheduleView.sort.manual') }}</option>
      </select>

      <!-- 필터 토글 -->
      <button class="btn-secondary ml-auto flex items-center gap-1.5" @click="showFilters = !showFilters">
        <FunnelIcon class="h-4 w-4" />
        <span class="hidden tablet:inline">{{ $t('scheduleView.filter') }}</span>
        <span
          v-if="activeFilterCount > 0"
          class="flex h-5 w-5 items-center justify-center rounded-full bg-primary-600 text-xs text-white"
        >
          {{ activeFilterCount }}
        </span>
      </button>
    </div>

    <!-- 필터 패널 -->
    <ScheduleFilterPanel
      v-model:platform="filterPlatform"
      v-model:status="filterStatus"
      :open="showFilters"
      @reset="clearFilters"
    />

    <!-- 로딩 -->
    <LoadingSpinner v-if="loading" full-page />

    <!-- 예약이 아예 없는 경우 -->
    <EmptyState
      v-else-if="schedules.length === 0"
      :title="$t('scheduleView.noSchedules')"
      :description="$t('scheduleView.noSchedulesDescription')"
      :icon="CalendarDaysIcon"
      :action-label="$t('scheduleView.uploadVideo')"
      action-to="/upload"
      :secondary-action-label="$t('scheduleView.viewMyVideos')"
      secondary-action-to="/videos"
    />

    <template v-else>
      <!-- 기간 이동 -->
      <SchedulePeriodNav
        :title="navigationTitle"
        @prev="navigatePrev"
        @next="navigateNext"
        @today="goToToday"
      />

      <!-- 캘린더 탭 -->
      <ScheduleMonthGrid
        v-if="calendarView === 'month'"
        :current-date="currentDate"
        :schedules="filteredSchedules"
        @select="selectedSchedule = $event"
        @add="openQuickAdd"
      />
      <ScheduleWeekGrid
        v-else-if="calendarView === 'week'"
        :current-date="currentDate"
        :schedules="filteredSchedules"
        @select="selectedSchedule = $event"
        @add="openQuickAdd"
      />

      <!-- 리스트 탭 -->
      <ScheduleListPanel
        v-else
        :schedules="filteredSchedules"
        :sort-mode="sortMode"
        @select="selectedSchedule = $event"
        @cancel="confirmCancel"
      />
    </template>

    <!-- 예약 추가 -->
    <ScheduleQuickAddModal
      v-model="showQuickAdd"
      :initial-date="quickAddDate"
      @submit="handleQuickAddSubmit"
    />

    <!-- 예약 상세 -->
    <ScheduleDetailModal
      :schedule="selectedSchedule"
      @close="selectedSchedule = null"
      @cancel="confirmCancel"
    />

    <!-- 예약 취소 확인 -->
    <ConfirmModal
      v-model="showCancelModal"
      :title="$t('scheduleView.cancelModal.title')"
      :message="$t('scheduleView.cancelModal.message')"
      :confirm-text="$t('scheduleView.cancelModal.confirm')"
      danger
      @confirm="handleCancel"
    />

    <!-- AI 최적 시간 분석 -->
    <AiOptimalTimeModal v-model="showAiOptimalModal" />

    <!-- AI 캘린더 생성 -->
    <AiCalendarGenerateModal v-model="showAiCalendarModal" @accepted="loadSchedules" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { CalendarDaysIcon, FunnelIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'
import AiCalendarGenerateModal from '@/components/schedule/AiCalendarGenerateModal.vue'
import AiOptimalTimeModal from '@/components/schedule/AiOptimalTimeModal.vue'
import ScheduleDetailModal from '@/components/schedule/ScheduleDetailModal.vue'
import ScheduleFilterPanel from '@/components/schedule/ScheduleFilterPanel.vue'
import ScheduleListPanel from '@/components/schedule/ScheduleListPanel.vue'
import ScheduleMonthGrid from '@/components/schedule/ScheduleMonthGrid.vue'
import SchedulePeriodNav from '@/components/schedule/SchedulePeriodNav.vue'
import ScheduleQuickAddModal from '@/components/schedule/ScheduleQuickAddModal.vue'
import ScheduleWeekGrid from '@/components/schedule/ScheduleWeekGrid.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useLocale } from '@/composables/useLocale'
import { getWeekStart, toDateStr } from '@/utils/schedule'
import type {
  CalendarView,
  Schedule,
  ScheduleCreateRequest,
  ScheduleSortMode,
  ScheduleStatus,
} from '@/types/schedule'
import type { Platform } from '@/types/channel'

const route = useRoute()
const router = useRouter()
const { t } = useLocale()

const scheduleStore = useScheduleStore()
const { schedules, loading, calendarView } = storeToRefs(scheduleStore)
const { fetchSchedules, createSchedule, cancelSchedule, setCalendarView } = scheduleStore

// ─── 뷰 전환 + URL 쿼리 동기화 ────────────────────────────
/** `?view=` 값 파싱 (구 링크 호환: `calendar` → `month`) */
function parseView(raw: unknown): CalendarView | null {
  if (raw === 'calendar') return 'month'
  return raw === 'month' || raw === 'week' || raw === 'list' ? raw : null
}

// 최초 렌더 이전에 URL → 스토어를 동기 반영해 중복 fetch를 방지
const initialView = parseView(route.query.view)
if (initialView) setCalendarView(initialView)

/** 캘린더 탭에서 마지막으로 본 하위 모드 (리스트 → 캘린더 복귀 시 복원) */
const lastCalendarMode = ref<'month' | 'week'>(calendarView.value === 'week' ? 'week' : 'month')

const viewTabs = computed(() => [
  { key: 'calendar', label: t('calendar.title') },
  { key: 'list', label: t('scheduleView.view.list') },
])

const calendarModeTabs = computed(() => [
  { key: 'month', label: t('calendar.monthly') },
  { key: 'week', label: t('calendar.weekly') },
])

/** 사용자가 뷰를 전환할 때: 스토어 반영 + URL 쿼리에 push (뒤로가기로 복귀 가능) */
function applyView(view: CalendarView) {
  setCalendarView(view)
  if (view !== 'list') lastCalendarMode.value = view
  if (route.query.view === view) return
  router.push({ query: { ...route.query, view } })
}

const activeTab = computed<string>({
  get: () => (calendarView.value === 'list' ? 'list' : 'calendar'),
  set: (tab) => applyView(tab === 'list' ? 'list' : lastCalendarMode.value),
})

const calendarMode = computed<string>({
  get: () => lastCalendarMode.value,
  set: (mode) => applyView(mode === 'week' ? 'week' : 'month'),
})

// 뒤로/앞으로 가기 대응
watch(
  () => route.query.view,
  (raw) => {
    const view = parseView(raw)
    if (!view || view === calendarView.value) return
    setCalendarView(view)
    if (view !== 'list') lastCalendarMode.value = view
  },
)

// ─── 기간 이동 ────────────────────────────────────────────
const currentDate = ref(new Date())

const navigationTitle = computed(() => {
  const d = currentDate.value

  if (calendarView.value === 'week') {
    const weekStart = getWeekStart(d)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekEnd.getDate() + 6)
    const startMonth = weekStart.getMonth() + 1
    const endMonth = weekEnd.getMonth() + 1

    return startMonth === endMonth
      ? t('calendar.dateFormatWeeklySameMonth', {
          year: weekStart.getFullYear(),
          month: startMonth,
          startDate: weekStart.getDate(),
          endDate: weekEnd.getDate(),
        })
      : t('calendar.dateFormatWeeklyCrossMonth', {
          year: weekStart.getFullYear(),
          startMonth,
          startDate: weekStart.getDate(),
          endMonth,
          endDate: weekEnd.getDate(),
        })
  }

  return t('calendar.dateFormatMonthly', { year: d.getFullYear(), month: d.getMonth() + 1 })
})

function shiftPeriod(direction: 1 | -1) {
  const d = new Date(currentDate.value)
  if (calendarView.value === 'week') {
    d.setDate(d.getDate() + 7 * direction)
  } else {
    d.setMonth(d.getMonth() + direction)
  }
  currentDate.value = d
}

const navigatePrev = () => shiftPeriod(-1)
const navigateNext = () => shiftPeriod(1)
const goToToday = () => {
  currentDate.value = new Date()
}

// ─── 필터 / 정렬 ──────────────────────────────────────────
const showFilters = ref(false)
const filterPlatform = ref<Platform | null>(null)
const filterStatus = ref<ScheduleStatus | null>(null)
const sortMode = ref<ScheduleSortMode>('date')

const activeFilterCount = computed(
  () => (filterPlatform.value ? 1 : 0) + (filterStatus.value ? 1 : 0),
)

function clearFilters() {
  filterPlatform.value = null
  filterStatus.value = null
}

const filteredSchedules = computed(() =>
  schedules.value.filter((s) => {
    if (filterPlatform.value && !s.platforms.some((p) => p.platform === filterPlatform.value)) {
      return false
    }
    return !(filterStatus.value && s.status !== filterStatus.value)
  }),
)

// ─── 예약 추가 ────────────────────────────────────────────
const showQuickAdd = ref(false)
const quickAddDate = ref<Date | null>(null)

function openQuickAdd(date: Date, hour?: number) {
  const d = new Date(date)
  d.setHours(hour ?? 9, 0, 0, 0)
  quickAddDate.value = d
  showQuickAdd.value = true
}

async function handleQuickAddSubmit(request: ScheduleCreateRequest) {
  try {
    await createSchedule(request)
    showQuickAdd.value = false
    await loadSchedules()
  } catch {
    // 에러는 스토어 / 전역 에러 핸들러가 처리
  }
}

// ─── 예약 상세 / 취소 ─────────────────────────────────────
const selectedSchedule = ref<Schedule | null>(null)
const showCancelModal = ref(false)
const cancelTargetId = ref<number | null>(null)

function confirmCancel(schedule: Schedule) {
  cancelTargetId.value = schedule.id
  selectedSchedule.value = null
  showCancelModal.value = true
}

async function handleCancel() {
  if (cancelTargetId.value == null) return
  try {
    await cancelSchedule(cancelTargetId.value)
    cancelTargetId.value = null
    await loadSchedules()
  } catch {
    // 에러는 스토어 / 전역 에러 핸들러가 처리
  }
}

// ─── AI 모달 ──────────────────────────────────────────────
const showAiOptimalModal = ref(false)
const showAiCalendarModal = ref(false)

// ─── 데이터 로딩 ──────────────────────────────────────────
function getDateRange(): { startDate: string; endDate: string } {
  const d = currentDate.value

  if (calendarView.value === 'week') {
    const weekStart = getWeekStart(d)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekEnd.getDate() + 6)
    return { startDate: toDateStr(weekStart), endDate: toDateStr(weekEnd) }
  }

  if (calendarView.value === 'month') {
    // 월간 그리드는 전/다음 달 잔여일까지 6행을 채우므로 범위를 확장
    const firstDay = new Date(d.getFullYear(), d.getMonth(), 1)
    const start = new Date(firstDay)
    start.setDate(start.getDate() - firstDay.getDay())

    const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0)
    const end = new Date(lastDay)
    end.setDate(end.getDate() + (6 - lastDay.getDay()))

    return { startDate: toDateStr(start), endDate: toDateStr(end) }
  }

  // 리스트: 해당 월
  return {
    startDate: toDateStr(new Date(d.getFullYear(), d.getMonth(), 1)),
    endDate: toDateStr(new Date(d.getFullYear(), d.getMonth() + 1, 0)),
  }
}

async function loadSchedules() {
  const { startDate, endDate } = getDateRange()
  await fetchSchedules(startDate, endDate)
}

watch([currentDate, calendarView], () => {
  loadSchedules()
})

onMounted(() => {
  // URL에 유효한 view가 없으면 현재 뷰로 정규화 (공유·새로고침 대응)
  if (!parseView(route.query.view)) {
    router.replace({ query: { ...route.query, view: calendarView.value } })
  }
  loadSchedules()
})
</script>
