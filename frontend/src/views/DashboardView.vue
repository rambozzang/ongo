<template>
  <div>
    <!-- Mobile Layout (below 768px) -->
    <MobileDashboard
      class="md:hidden"
      :loading="loading"
      :kpi="kpi"
      :greeting="greeting"
      :user-name="userName"
      :current-date="currentDate"
      :credit-percentage="creditPercentage"
      :recent-videos="recentVideos"
      :trend-data="trendData"
      :period="period"
      :today-and-tomorrow-schedules="todayAndTomorrowSchedules"
      :grouped-schedules="groupedSchedules"
      @set-period="dashboardStore.setPeriod"
    />

    <!-- Desktop/Tablet Layout (768px+) -->
    <DesktopDashboard
      class="hidden md:block"
      :loading="loading"
      :kpi="kpi"
      :greeting="greeting"
      :user-name="userName"
      :today-schedule-count="todayScheduleCount"
      :credit-balance="creditBalance"
      :credit-percentage="creditPercentage"
      :trend-data="trendData"
      :period="period"
      :platform-comparison="platformComparison"
      :recent-videos="recentVideos"
      :upcoming-schedules="upcomingSchedules"
      :top-videos="topVideos"
      :visible-widgets="visibleWidgets"
      :show-widget-customizer="showWidgetCustomizer"
      @set-period="dashboardStore.setPeriod"
      @open-customizer="showWidgetCustomizer = true"
      @recycle="handleRecycleVideo"
      @update:show-widget-customizer="showWidgetCustomizer = $event"
    />

    <!-- Recycle Modal (shared) -->
    <RecycleModal
      v-if="recycleVideo"
      v-model="showRecycleModal"
      :video="recycleVideo"
      @confirm="handleRecycleConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useDashboardStore } from '@/stores/dashboard'
import { useCreditStore } from '@/stores/credit'
import { useWidgetSettingsStore } from '@/stores/widgetSettings'
import MobileDashboard from '@/components/dashboard/MobileDashboard.vue'
import DesktopDashboard from '@/components/dashboard/DesktopDashboard.vue'
import RecycleModal from '@/components/video/RecycleModal.vue'
import type { Video } from '@/types/video'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ko'

dayjs.extend(relativeTime)
dayjs.locale('ko')

const dashboardStore = useDashboardStore()
const authStore = useAuthStore()
const creditStore = useCreditStore()
const widgetSettingsStore = useWidgetSettingsStore()

const { t } = useI18n({ useScope: 'global' })

const { kpi, trendData, platformComparison, recentVideos, upcomingSchedules, topVideos, loading, period } =
  storeToRefs(dashboardStore)

const userName = computed(() => authStore.user?.nickname || authStore.user?.name || '크리에이터')
const creditBalance = computed(() => creditStore.totalBalance)
const todayScheduleCount = computed(() => {
  const today = dayjs().startOf('day')
  return upcomingSchedules.value.filter(s => dayjs(s.scheduledAt).isSame(today, 'day')).length
})

// Widget Customizer
const showWidgetCustomizer = ref(false)

// Get visible widgets
const visibleWidgets = computed(() => widgetSettingsStore.getVisibleWidgets())

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 6 && hour < 12) return t('dashboard.greetingMorning')
  if (hour >= 12 && hour < 18) return t('dashboard.greetingAfternoon')
  if (hour >= 18 && hour < 22) return t('dashboard.greetingEvening')
  return t('dashboard.greetingNight')
})

const currentDate = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}/${now.getDate()}`
})

const creditPercentage = computed(() => {
  if (!kpi.value || kpi.value.creditTotal === 0) return 0
  return Math.round((kpi.value.creditBalance / kpi.value.creditTotal) * 100)
})

// Filter schedules for today and tomorrow only (mobile)
const todayAndTomorrowSchedules = computed(() => {
  const now = dayjs()
  const tomorrow = now.add(1, 'day')

  return upcomingSchedules.value.filter((schedule) => {
    const scheduleDate = dayjs(schedule.scheduledAt)
    return scheduleDate.isBefore(tomorrow.endOf('day'))
  })
})

// Group schedules by today/tomorrow
const groupedSchedules = computed(() => {
  const now = dayjs()
  const today = now.startOf('day')
  const tomorrow = today.add(1, 'day')

  const groups = [
    {
      label: t('dashboard.today'),
      schedules: todayAndTomorrowSchedules.value.filter((s) => {
        const scheduleDate = dayjs(s.scheduledAt)
        return scheduleDate.isSame(today, 'day')
      }),
    },
    {
      label: t('dashboard.tomorrow'),
      schedules: todayAndTomorrowSchedules.value.filter((s) => {
        const scheduleDate = dayjs(s.scheduledAt)
        return scheduleDate.isSame(tomorrow, 'day')
      }),
    },
  ]

  return groups.filter(g => g.schedules.length > 0)
})

// Recycle Modal
const showRecycleModal = ref(false)
const recycleVideo = ref<Video | null>(null)

function handleRecycleVideo(video: Video) {
  recycleVideo.value = video
  showRecycleModal.value = true
}

async function handleRecycleConfirm() {
  await dashboardStore.fetchDashboard()
}

onMounted(() => {
  dashboardStore.fetchDashboard()
  creditStore.fetchBalance()
})
</script>
