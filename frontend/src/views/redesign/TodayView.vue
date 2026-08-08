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

    <!-- KPI 4장 -->
    <div class="grid gap-2.5" style="grid-template-columns: repeat(auto-fit, minmax(190px, 1fr))">
      <KpiCard
        :label="t('redesign.today.kpiScheduled')"
        :value="fmt(kpi.scheduled)"
        :delta="kpi.pending ? `${kpi.pending} 대기` : ''"
        delta-variant="muted"
        :note="kpi.failed ? `실패 ${kpi.failed}건` : '실패 0건'"
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
        :delta="kpi.weeklyGoal ? `목표 ${kpi.weeklyGoal}` : ''"
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

        <p v-else-if="queue.length === 0" class="px-[15px] py-10 text-center text-[12.5px] text-content-tertiary">
          {{ t('redesign.today.emptyQueue') }}
        </p>

        <template v-else>
          <div
            v-for="row in queue"
            :key="row.id"
            class="flex cursor-pointer items-center gap-3 border-b border-line-row px-[15px] py-[11px] transition-colors hover:bg-surface-raised tablet:grid tablet:[grid-template-columns:62px_84px_minmax(0,1fr)_auto]"
            @click="openItem(row)"
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
          <p v-if="channels.length === 0" class="px-[15px] py-8 text-center text-[12px] text-content-tertiary">
            {{ t('redesign.today.emptyChannels') }}
          </p>
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
const { t } = useLocale()
const router = useRouter()
const store = useRedesignTodayStore()

const loading = computed(() => store.loading)
const kpi = computed(() => store.kpi)
const queue = computed(() => store.queue)
const attention = computed(() => store.attention)
const channels = computed(() => store.channels)

const todayLabel = computed(() =>
  new Date().toLocaleDateString('ko-KR', { month: '2-digit', day: '2-digit', weekday: 'short' }),
)

const weeklyRemaining = computed(() => {
  const left = kpi.value.weeklyGoal - kpi.value.weeklyPublished
  return left > 0 ? `${left}편 남음` : '목표 달성'
})

const fmt = (n: number) => new Intl.NumberFormat('ko-KR').format(n)

const dotClass = (severity: 'error' | 'warning' | 'info') =>
  severity === 'error' ? 'bg-bad' : severity === 'warning' ? 'bg-warn' : 'bg-accent'

function openItem(row: { videoId: number | null }) {
  if (row.videoId) router.push(`/videos/${row.videoId}`)
}

onMounted(() => store.load())
</script>
