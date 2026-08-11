<template>
  <div class="flex flex-col gap-[18px] px-[22px] pb-10 pt-5">
    <div
      v-if="store.loadError"
      class="flex flex-wrap items-center gap-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2.5 text-[12px] text-warning-strong"
      role="status"
      aria-live="polite"
    >
      <span class="min-w-0 flex-1">{{ $t(`redesign.today.${store.loadError}`) }}</span>
      <button
        type="button"
        class="shrink-0 rounded-md border border-warning-strong px-2 py-1 text-[11px] font-semibold transition-colors hover:bg-warning-strong hover:text-surface-base disabled:opacity-50"
        :disabled="loading"
        @click="store.load"
      >
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- 한 화면에서 바로 다음 행동을 결정한다. 데이터가 없을 때도 실제 상태에 맞는 CTA만 보여준다. -->
    <header class="flex flex-wrap items-end justify-between gap-3 border-b border-line pb-3">
      <div class="min-w-0">
        <p class="text-[10px] font-bold uppercase tracking-[0.14em] text-content-tertiary">
          {{ t('redesign.today.workHeading') }}
        </p>
        <p class="mt-1 text-[13px] font-semibold text-content">
          {{ t('redesign.today.workStatus', { queue: queue.length, attention: attention.length }) }}
        </p>
        <p class="mt-0.5 text-[11px] text-content-tertiary">
          {{ t('redesign.today.workSummary') }}
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-if="store.loadError"
          type="button"
          class="btn-secondary !text-[11px]"
          :disabled="loading"
          @click="store.load"
        >
          {{ t('redesign.today.retryLoad') }}
        </button>
        <router-link v-else :to="nextAction.to" class="btn-primary !text-[11px]">
          {{ nextAction.label }}
        </router-link>
        <router-link to="/calendar-v2" class="btn-secondary !text-[11px]">
          {{ t('redesign.today.viewCalendar') }}
        </router-link>
      </div>
    </header>

    <!-- KPI 4장 -->
    <div class="grid gap-2.5" style="grid-template-columns: repeat(auto-fit, minmax(190px, 1fr))">
      <KpiCard
        :label="t('redesign.today.kpiScheduled')"
        :value="fmt(kpi.scheduled)"
        :delta="pendingLabel"
        delta-variant="muted"
        :note="failedLabel"
      />
      <KpiCard
        :label="t('redesign.today.kpiUnanswered')"
        :value="fmt(kpi.unanswered)"
        :delta="kpi.unansweredDelta"
        delta-variant="warning"
        :note="kpi.avgResponse"
      />
      <KpiCard
        :label="t('redesign.today.kpiViews')"
        :value="kpi.viewsLabel"
        :delta="kpi.viewsDelta"
        :note="kpi.shortsShare"
      />
      <KpiCard
        :label="t('redesign.today.kpiWeekly')"
        :value="fmt(kpi.weeklyPublished)"
        :delta="weeklyGoalLabel"
        delta-variant="muted"
        :note="weeklyRemaining"
      />
    </div>

    <!-- 좌: 발행 큐 / 우: 확인 필요 + 채널 상태. <1024 는 단일 열로 쌓는다 -->
    <div class="grid items-start gap-3.5 desktop:[grid-template-columns:minmax(0,1.55fr)_minmax(0,1fr)]">
      <SectionCard :title="t('redesign.today.queueTitle')" :meta="todayLabel" body-class="">
        <template #action>
          <router-link to="/calendar-v2" class="text-[11px] text-accent hover:text-content">
            {{ t('redesign.today.viewCalendar') }}
          </router-link>
        </template>

        <LoadingSpinner v-if="loading" class="py-10" />

        <div v-else-if="queue.length === 0" class="px-[15px] py-10 text-center">
          <p class="text-[12.5px] text-content-tertiary">{{ t('redesign.today.emptyQueue') }}</p>
          <router-link to="/compose" class="mt-3 inline-flex btn-secondary !text-[11px]">
            {{ t('redesign.today.emptyQueueAction') }}
          </router-link>
        </div>

        <template v-else>
          <div
            v-for="row in queue"
            :key="row.id"
            class="flex cursor-pointer items-center gap-3 border-b border-line-row px-[15px] py-[11px] transition-colors hover:bg-surface-raised tablet:grid tablet:[grid-template-columns:62px_84px_minmax(0,1fr)_auto]"
            role="button"
            tabindex="0"
            :aria-label="row.title"
            @click="openItem(row)"
            @keydown.enter.prevent="openItem(row)"
            @keydown.space.prevent="openItem(row)"
          >
            <!-- 모바일에서는 시간이 제목 위로 올라가고 썸네일이 작아진다 -->
            <span class="hidden font-mono text-[13px] text-content tablet:inline">{{ row.time }}</span>
            <ThumbPlaceholder
              :src="row.thumbnailUrl"
              :duration="row.duration"
              :width="44"
              :height="52"
              class="tablet:hidden"
            />
            <ThumbPlaceholder
              :src="row.thumbnailUrl"
              :duration="row.duration"
              :width="84"
              :height="46"
              class="hidden tablet:block"
            />
            <div class="min-w-0 flex-1">
              <span class="font-mono text-[11px] text-content-tertiary tablet:hidden">{{ row.time }}</span>
              <p class="truncate text-[13px] font-semibold text-content">{{ row.title }}</p>
              <div class="mt-1 flex items-center gap-1.5">
                <PlatformChip v-for="p in row.platforms" :key="p" :platform="p" size="sm" />
                <span v-if="row.meta" class="truncate text-[11px] text-content-tertiary">{{ row.meta }}</span>
              </div>
            </div>
            <StatusPill :variant="row.statusVariant">{{ row.statusLabel }}</StatusPill>
          </div>

          <router-link
            to="/compose"
            class="m-[15px] flex items-center justify-center rounded-lg border border-dashed border-line-control py-2.5 text-[12px] text-content-tertiary transition-colors hover:border-accent hover:text-accent"
          >
            {{ t('redesign.today.addAtSlot') }}
          </router-link>
        </template>
      </SectionCard>

      <div class="flex flex-col gap-3.5">
        <!-- 확인 필요 -->
        <SectionCard :title="t('redesign.today.needsAttention')" :meta="attention.length ? String(attention.length) : ''">
          <p v-if="attention.length === 0" class="px-[15px] py-8 text-center text-[12px] text-content-tertiary">
            {{ t('redesign.today.emptyAttention') }}
          </p>
          <div
            v-for="item in attention"
            :key="item.id"
            class="flex items-start gap-2.5 border-b border-line-row px-[15px] py-3 last:border-b-0"
          >
            <span class="mt-1.5 h-[7px] w-[7px] shrink-0 rounded-full" :class="dotClass(item.severity)" />
            <div class="min-w-0 flex-1">
              <p class="text-[12.5px] text-content">{{ item.message }}</p>
              <p v-if="item.meta" class="mt-0.5 text-[11px] text-content-tertiary">{{ item.meta }}</p>
            </div>
            <!-- CTA 는 중간 상세 없이 해당 작업 화면으로 바로 진입한다 -->
            <router-link
              :to="item.to"
              class="shrink-0 rounded-md border border-line-control px-2 py-1 text-[11px] font-semibold text-accent transition-colors hover:border-accent"
            >
              {{ item.cta }}
            </router-link>
          </div>
        </SectionCard>

        <!-- 채널 상태 -->
        <SectionCard :title="t('redesign.today.channelStatus')">
          <template #action>
            <router-link to="/channels-v2" class="text-[11px] text-accent hover:text-content">
              {{ t('redesign.today.manage') }}
            </router-link>
          </template>
          <div v-if="channels.length === 0" class="px-[15px] py-8 text-center">
            <p class="text-[12px] text-content-tertiary">{{ t('redesign.today.emptyChannels') }}</p>
            <router-link to="/channels-v2" class="mt-3 inline-flex btn-secondary !text-[11px]">
              {{ t('redesign.today.emptyChannelsAction') }}
            </router-link>
          </div>
          <div
            v-for="ch in channels"
            :key="ch.id"
            class="flex items-center gap-2.5 border-b border-line-row px-[15px] py-2.5 last:border-b-0"
          >
            <PlatformChip :platform="ch.platform" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-[12.5px] font-semibold text-content">{{ ch.name }}</p>
              <p class="truncate text-[10.5px] text-content-tertiary">{{ ch.sub }}</p>
            </div>
            <StatusPill :variant="ch.statusVariant">{{ ch.statusLabel }}</StatusPill>
          </div>
        </SectionCard>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import ThumbPlaceholder from '@/components/redesign/ThumbPlaceholder.vue'
import { useRedesignTodayStore } from '@/stores/redesignToday'

/**
 * 오늘 — 로그인 직후 착지 화면.
 *
 * 오늘 무엇이 나가는지 / 무엇이 막혀 있는지 / 무엇에 답해야 하는지를 한 화면에서 본다.
 * 확인 필요 CTA 는 중간 상세 페이지 없이 해당 작업 화면으로 바로 진입한다.
 */
const { t, currentLocale } = useLocale()
const router = useRouter()
const store = useRedesignTodayStore()

const loading = computed(() => store.loading)
const kpi = computed(() => store.kpi)
const queue = computed(() => store.queue)
const attention = computed(() => store.attention)
const channels = computed(() => store.channels)

const nextAction = computed(() => {
  if (channels.value.length === 0) {
    return { to: '/channels-v2', label: t('redesign.today.connectChannel') }
  }
  if (attention.value.length > 0) {
    return { to: attention.value[0].to, label: t('redesign.today.reviewAttention') }
  }
  return { to: '/compose', label: t('redesign.today.createContent') }
})

const todayLabel = computed(() =>
  new Intl.DateTimeFormat(currentLocale.value, {
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
  }).format(new Date()),
)

const weeklyRemaining = computed(() => {
  const left = kpi.value.weeklyGoal - kpi.value.weeklyPublished
  return left > 0
    ? t('redesign.today.remainingCount', { count: left })
    : t('redesign.today.goalReached')
})

const pendingLabel = computed(() =>
  kpi.value.pending ? t('redesign.today.pendingCount', { count: kpi.value.pending }) : '',
)
const failedLabel = computed(() => t('redesign.today.failedCount', { count: kpi.value.failed }))
const weeklyGoalLabel = computed(() =>
  kpi.value.weeklyGoal ? t('redesign.today.goalCount', { count: kpi.value.weeklyGoal }) : '',
)
const fmt = (n: number) => new Intl.NumberFormat(currentLocale.value).format(n)

const dotClass = (severity: 'error' | 'warning' | 'info') =>
  severity === 'error' ? 'bg-bad' : severity === 'warning' ? 'bg-warn' : 'bg-accent'

function openItem(row: { videoId: number | null }) {
  if (row.videoId) router.push(`/videos/${row.videoId}`)
}

onMounted(() => store.load())
</script>
