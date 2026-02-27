<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('aiCalendar.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('aiCalendar.description') }}
        </p>
      </div>
      <div v-if="calendarStore.calendarResult" class="flex items-center gap-2">
        <button
          :class="[
            'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            calendarStore.viewMode === 'weekly'
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
          ]"
          @click="calendarStore.setViewMode('weekly')"
        >
          {{ $t('aiCalendar.weekly') }}
        </button>
        <button
          :class="[
            'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            calendarStore.viewMode === 'monthly'
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
          ]"
          @click="calendarStore.setViewMode('monthly')"
        >
          {{ $t('aiCalendar.monthly') }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('aiCalendar.pageGuideTitle')"
      :items="($tm('aiCalendar.pageGuide') as string[])"
    />

    <!-- Main Content: Settings + Calendar -->
    <div v-if="!calendarStore.calendarResult" class="grid gap-6 desktop:grid-cols-3">
      <!-- Settings Panel -->
      <div class="desktop:col-span-1">
        <div class="card p-5">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('aiCalendar.settingsTitle') }}
          </h2>
          <AiCalendarSettings
            v-model="settingsForm"
            :disabled="calendarStore.loading"
          />
          <div class="mt-6">
            <button
              class="btn-primary inline-flex w-full items-center justify-center gap-2"
              :disabled="!isFormValid || calendarStore.loading"
              @click="handleGenerate"
            >
              <SparklesIcon class="h-4 w-4" />
              {{ calendarStore.loading ? $t('aiCalendar.generating') : $t('aiCalendar.generate') }}
            </button>
          </div>
          <!-- Error -->
          <div
            v-if="calendarStore.error"
            class="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400"
          >
            {{ calendarStore.error }}
          </div>
        </div>
      </div>

      <!-- Preview / Empty State -->
      <div class="desktop:col-span-2">
        <div class="card flex min-h-[400px] items-center justify-center p-8">
          <div v-if="calendarStore.loading" class="text-center">
            <div class="mx-auto mb-4 h-12 w-12 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('aiCalendar.generatingMessage') }}
            </p>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              {{ $t('aiCalendar.generatingHint') }}
            </p>
          </div>
          <div v-else class="text-center">
            <CalendarDaysIcon class="mx-auto mb-3 h-12 w-12 text-gray-300 dark:text-gray-600" />
            <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ $t('aiCalendar.emptyTitle') }}
            </h3>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              {{ $t('aiCalendar.emptyDescription') }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Generated Calendar View -->
    <div v-else>
      <!-- Week Navigation -->
      <div class="card mb-4 flex items-center justify-between p-4">
        <button
          class="rounded-lg p-2 text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="calendarStore.previousWeek()"
        >
          <ChevronLeftIcon class="h-5 w-5" />
        </button>

        <div class="flex items-center gap-4">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ formattedWeekRange }}
          </h2>
          <button
            class="rounded-lg px-3 py-1.5 text-sm font-medium text-primary-700 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/30"
            @click="calendarStore.setWeekStart(new Date())"
          >
            {{ $t('aiCalendar.grid.today') }}
          </button>
        </div>

        <button
          class="rounded-lg p-2 text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="calendarStore.nextWeek()"
        >
          <ChevronRightIcon class="h-5 w-5" />
        </button>
      </div>

      <!-- Season Events Banner -->
      <div
        v-if="currentWeekSeasonEvents.length > 0"
        class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-800 dark:bg-amber-900/20"
      >
        <span class="text-sm font-medium text-amber-700 dark:text-amber-400">
          {{ $t('aiCalendar.seasonEventsThisWeek') }}:
        </span>
        <SeasonEventBadge
          v-for="event in currentWeekSeasonEvents"
          :key="event.date"
          :event="event"
        />
      </div>

      <!-- Calendar Grid -->
      <AiCalendarGrid
        :slots="calendarStore.weekSlots"
        :week-start="calendarStore.currentWeekStart"
        :season-events="calendarStore.seasonEvents"
        @toggle-confirm="calendarStore.toggleSlotConfirm($event)"
        @regenerate="handleRegenerateSlot"
      />

      <!-- Actions Bar -->
      <AiCalendarActions
        :total-slots="calendarStore.slots.length"
        :confirmed-count="calendarStore.confirmedSlotIds.length"
        :credits-used="calendarStore.calendarResult?.creditsUsed ?? 0"
        :loading="calendarStore.loading"
        @regenerate-all="handleRegenerateAll"
        @apply="handleApply"
      />
    </div>

    <!-- Apply Success Modal -->
    <Teleport to="body">
      <div
        v-if="showSuccessModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
      >
        <div class="fixed inset-0 bg-black/50" @click="showSuccessModal = false" />
        <div class="relative w-full max-w-md rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800">
          <div class="mb-4 flex justify-center">
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
          </div>
          <h3 class="mb-2 text-center text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('aiCalendar.applySuccess.title') }}
          </h3>
          <p class="mb-6 text-center text-sm text-gray-500 dark:text-gray-400">
            {{ $t('aiCalendar.applySuccess.message', { count: appliedCount }) }}
          </p>
          <div class="flex gap-3">
            <button class="btn-secondary flex-1" @click="showSuccessModal = false">
              {{ $t('aiCalendar.applySuccess.stay') }}
            </button>
            <router-link to="/schedule" class="btn-primary flex-1 text-center">
              {{ $t('aiCalendar.applySuccess.goToSchedule') }}
            </router-link>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  SparklesIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  CalendarDaysIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import AiCalendarSettings from '@/components/aicalendar/AiCalendarSettings.vue'
import AiCalendarGrid from '@/components/aicalendar/AiCalendarGrid.vue'
import AiCalendarActions from '@/components/aicalendar/AiCalendarActions.vue'
import SeasonEventBadge from '@/components/aicalendar/SeasonEventBadge.vue'
import { useAiCalendarStore } from '@/stores/aiCalendar'
import { useLocale } from '@/composables/useLocale'
import type { Platform } from '@/types/channel'
import type { CalendarPeriod, PostingFrequency, ContentSlot, SeasonEvent } from '@/types/aiCalendar'

const calendarStore = useAiCalendarStore()
const { t } = useLocale()

// --- Settings Form ---
const settingsForm = ref({
  period: '2weeks' as CalendarPeriod,
  frequency: '3perWeek' as PostingFrequency,
  platforms: [] as Platform[],
  categories: [] as string[],
  includeSeasonEvents: true,
})

const isFormValid = computed(() => {
  return settingsForm.value.platforms.length > 0 && settingsForm.value.categories.length > 0
})

// --- Week Navigation ---
const formattedWeekRange = computed(() => {
  const start = calendarStore.currentWeekStart
  const end = new Date(start)
  end.setDate(end.getDate() + 6)

  const startMonth = start.getMonth() + 1
  const startDate = start.getDate()
  const endMonth = end.getMonth() + 1
  const endDate = end.getDate()
  const year = start.getFullYear()

  if (startMonth === endMonth) {
    return t('calendar.dateFormatWeeklySameMonth', { year, month: startMonth, startDate, endDate })
  }
  return t('calendar.dateFormatWeeklyCrossMonth', { year, startMonth, startDate, endMonth, endDate })
})

const currentWeekSeasonEvents = computed(() => {
  const start = calendarStore.currentWeekStart
  const end = new Date(start)
  end.setDate(end.getDate() + 7)

  return calendarStore.seasonEvents.filter((e) => {
    const d = new Date(e.date)
    return d >= start && d < end
  })
})

// --- Success Modal ---
const showSuccessModal = ref(false)
const appliedCount = ref(0)

// --- Actions ---
async function handleGenerate() {
  const mockResult = generateMockCalendar()
  calendarStore.$patch({ calendarResult: mockResult, loading: false })
}

function handleRegenerateSlot(slotId: string) {
  const slot = calendarStore.slots.find((s) => s.id === slotId)
  if (!slot) return

  const mockTitles = [
    '트렌드 챌린지: 요즘 핫한 밈 따라잡기',
    '일상 브이로그: 주말 루틴 공개',
    '꿀팁 공유: 시간 절약 라이프핵 5가지',
    '리뷰: 이달의 신제품 언박싱',
    'Q&A: 구독자 질문 총정리',
  ]

  calendarStore.updateSlot(slotId, {
    title: mockTitles[Math.floor(Math.random() * mockTitles.length)],
    trendScore: Math.floor(Math.random() * 40) + 60,
    trendKeywords: ['트렌드', '인기', '추천'].sort(() => Math.random() - 0.5).slice(0, 2),
    status: 'suggested',
  })
}

function handleRegenerateAll() {
  handleGenerate()
}

async function handleApply() {
  const confirmed = calendarStore.confirmedSlotIds
  if (confirmed.length === 0) return

  appliedCount.value = confirmed.length
  showSuccessModal.value = true
}

// --- Mock Data Generator ---
function generateMockCalendar() {
  const periodWeeks =
    settingsForm.value.period === '2weeks' ? 2 : settingsForm.value.period === '3weeks' ? 3 : 4

  const freqMap: Record<PostingFrequency, number> = {
    daily: 7,
    '3perWeek': 3,
    '2perWeek': 2,
    '1perWeek': 1,
  }
  const postsPerWeek = freqMap[settingsForm.value.frequency]

  const koreanSeasonEvents: SeasonEvent[] = [
    { date: '2026-01-01', name: '신정', nameEn: "New Year's Day", type: 'holiday' },
    { date: '2026-01-28', name: '설날 연휴', nameEn: 'Lunar New Year', type: 'holiday' },
    { date: '2026-01-29', name: '설날', nameEn: 'Lunar New Year', type: 'holiday' },
    { date: '2026-01-30', name: '설날 연휴', nameEn: 'Lunar New Year', type: 'holiday' },
    { date: '2026-02-14', name: '발렌타인데이', nameEn: "Valentine's Day", type: 'event' },
    { date: '2026-03-01', name: '삼일절', nameEn: 'Independence Movement Day', type: 'holiday' },
    { date: '2026-03-14', name: '화이트데이', nameEn: 'White Day', type: 'event' },
    { date: '2026-04-05', name: '식목일', nameEn: 'Arbor Day', type: 'season' },
    { date: '2026-05-05', name: '어린이날', nameEn: "Children's Day", type: 'holiday' },
    { date: '2026-05-08', name: '어버이날', nameEn: "Parents' Day", type: 'event' },
    { date: '2026-05-15', name: '스승의 날', nameEn: "Teachers' Day", type: 'event' },
    { date: '2026-06-06', name: '현충일', nameEn: 'Memorial Day', type: 'holiday' },
    { date: '2026-08-15', name: '광복절', nameEn: 'Liberation Day', type: 'holiday' },
    { date: '2026-09-24', name: '추석 연휴', nameEn: 'Chuseok', type: 'holiday' },
    { date: '2026-09-25', name: '추석', nameEn: 'Chuseok', type: 'holiday' },
    { date: '2026-09-26', name: '추석 연휴', nameEn: 'Chuseok', type: 'holiday' },
    { date: '2026-10-03', name: '개천절', nameEn: 'National Foundation Day', type: 'holiday' },
    { date: '2026-10-09', name: '한글날', nameEn: 'Hangul Day', type: 'holiday' },
    { date: '2026-10-31', name: '할로윈', nameEn: 'Halloween', type: 'event' },
    { date: '2026-11-11', name: '빼빼로데이', nameEn: 'Pepero Day', type: 'event' },
    { date: '2026-12-25', name: '크리스마스', nameEn: 'Christmas', type: 'holiday' },
    { date: '2026-12-31', name: '연말', nameEn: "New Year's Eve", type: 'event' },
  ]

  const titleTemplates: Record<string, string[]> = {
    엔터테인먼트: [
      '이번 주 핫이슈 총정리',
      '연예계 비하인드 스토리',
      '화제의 콘텐츠 리액션',
    ],
    게임: [
      '신작 게임 첫인상 리뷰',
      '게임 공략: 초보자 필수 팁',
      '게이머 일상 브이로그',
    ],
    음악: [
      '이 달의 추천 플레이리스트',
      '커버곡 챌린지',
      '뮤직 리뷰: 신곡 분석',
    ],
    교육: [
      '5분 만에 배우는 핵심 개념',
      '공부법 꿀팁 대공개',
      '트렌드 기술 해설',
    ],
    푸드: [
      '간단 레시피: 5분 요리',
      '맛집 탐방 브이로그',
      '편의점 신상 리뷰',
    ],
    '뷰티/패션': [
      '데일리 메이크업 루틴',
      '이번 시즌 패션 트렌드',
      '뷰티템 솔직 리뷰',
    ],
    '일상/브이로그': [
      '주말 루틴 브이로그',
      '일상 속 소소한 행복',
      '자취생 리얼 일상',
    ],
    default: [
      '트렌드 콘텐츠 기획',
      '구독자 소통 라이브',
      '주간 하이라이트 정리',
    ],
  }

  const trendKeywordPool = [
    '챌린지', '브이로그', '리뷰', '트렌드', '꿀팁',
    '일상', '추천', '랭킹', '핫이슈', '신상',
    'ASMR', '먹방', '언박싱', '루틴', '공략',
  ]

  const times = ['09:00', '11:00', '14:00', '17:00', '19:00', '20:00', '21:00']

  const startDate = new Date()
  startDate.setHours(0, 0, 0, 0)
  // Start from next Monday
  const dayOfWeek = startDate.getDay()
  const daysToMonday = dayOfWeek === 0 ? 1 : dayOfWeek === 1 ? 0 : 8 - dayOfWeek
  startDate.setDate(startDate.getDate() + daysToMonday)

  const slots: ContentSlot[] = []
  let slotId = 1

  for (let week = 0; week < periodWeeks; week++) {
    // Pick random days of the week for posting
    const dayIndices = Array.from({ length: 7 }, (_, i) => i)
    const selectedDays =
      postsPerWeek >= 7
        ? dayIndices
        : dayIndices.sort(() => Math.random() - 0.5).slice(0, postsPerWeek).sort((a, b) => a - b)

    for (const dayIdx of selectedDays) {
      const slotDate = new Date(startDate)
      slotDate.setDate(startDate.getDate() + week * 7 + dayIdx)
      const dateStr = formatDate(slotDate)

      const platform =
        settingsForm.value.platforms[Math.floor(Math.random() * settingsForm.value.platforms.length)]
      const category =
        settingsForm.value.categories[
          Math.floor(Math.random() * settingsForm.value.categories.length)
        ]

      const titles = titleTemplates[category] ?? titleTemplates['default']
      const title = titles[Math.floor(Math.random() * titles.length)]
      const time = times[Math.floor(Math.random() * times.length)]

      const keywords = [...trendKeywordPool]
        .sort(() => Math.random() - 0.5)
        .slice(0, 3)

      const seasonEvent = koreanSeasonEvents.find((e) => e.date === dateStr)

      slots.push({
        id: `slot-${slotId++}`,
        date: dateStr,
        time,
        title: seasonEvent ? `[${seasonEvent.name} 특집] ${title}` : title,
        category,
        platform,
        trendScore: Math.floor(Math.random() * 40) + 60,
        trendKeywords: keywords,
        seasonEvent,
        status: 'suggested',
      })
    }
  }

  // Set week start to the first slot's week
  calendarStore.setWeekStart(startDate)

  return {
    id: `cal-${Date.now()}`,
    slots,
    generatedAt: new Date().toISOString(),
    period: settingsForm.value.period,
    seasonEvents: koreanSeasonEvents,
    creditsUsed: 10,
    creditsRemaining: 90,
  }
}

function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
</script>
