<template>
  <div class="min-h-full bg-surface-base px-4 py-5 text-content tablet:px-[22px] tablet:py-6">
    <header class="mb-[18px] flex flex-col gap-3 tablet:flex-row tablet:items-end tablet:justify-between">
      <div>
        <p class="font-mono text-[10px] uppercase tracking-[0.16em] text-content-tertiary">{{ t('nav.analytics') }}</p>
        <h1 class="mt-1 text-[26px] font-bold tracking-[-0.02em] text-content">{{ t('analyticsView.title') }}</h1>
        <p class="mt-1 text-[12px] text-content-tertiary">{{ t('analyticsView.description') }}</p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex rounded-lg border border-line-control bg-surface-input p-0.5" role="group" :aria-label="t('analyticsView.title')">
          <button
            v-for="option in periods"
            :key="option.value"
            type="button"
            class="rounded-md px-3 py-1.5 text-[11px] font-semibold transition-colors duration-150"
            :class="store.range === option.value ? 'bg-accent-dim text-accent' : 'text-content-secondary hover:bg-surface-raised hover:text-content'"
            @click="store.fetchPerformance(option.value)"
          >
            {{ t(`analyticsView.period.${option.value}`) }}
          </button>
        </div>
        <button type="button" class="btn-secondary !min-h-9 !px-3 text-[11px]" :disabled="!hasExportData" @click="exportCsv">
          <ArrowDownTrayIcon class="h-4 w-4" />
          {{ t('action.download') }} CSV
        </button>
      </div>
    </header>

    <div v-if="store.hasError" class="mb-4 flex items-start gap-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2 text-[12px] text-warning-strong">
      <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0" />
      <span>{{ t('analyticsView.emptyDataDescription') }}</span>
    </div>

    <div v-if="store.loading" class="grid gap-2.5 tablet:grid-cols-2 desktop:grid-cols-4">
      <div v-for="slot in 4" :key="slot" class="h-[112px] animate-pulse rounded-[11px] border border-line bg-surface-card" />
    </div>
    <div v-else class="space-y-[14px]">
      <section class="grid gap-2.5 tablet:grid-cols-2 desktop:grid-cols-4">
        <KpiCard
          :label="t('analyticsView.metrics.views')"
          :value="store.kpi ? formatNumber(store.kpi.totalViews) : '—'"
          :delta="store.kpi ? formatDelta(store.kpi.viewsChangePercent, '%') : undefined"
          :delta-variant="store.kpi && store.kpi.viewsChangePercent < 0 ? 'error' : 'success'"
          :note="t('analyticsView.period.' + store.range)"
        />
        <KpiCard
          :label="t('analyticsView.table.watchTime')"
          :value="store.averageViewDuration ? formatDuration(store.averageViewDuration.avgDurationSeconds) : '—'"
          :note="store.averageViewDuration ? store.averageViewDuration.period : t('analyticsView.noData')"
        />
        <KpiCard
          :label="t('analyticsView.subscriberTrend')"
          :value="store.subscriberConversion ? formatNumber(store.subscriberConversion.totalGained) : '—'"
          :delta="store.subscriberConversion && store.subscriberConversion.totalGained > 0 ? t('status.success') : undefined"
          :note="t('analyticsView.subscriberTrend')"
        />
        <KpiCard
          :label="t('action.publish')"
          :value="store.publishedCount === null ? '—' : formatNumber(store.publishedCount)"
          :note="t('analyticsView.period.' + store.range)"
        />
      </section>

      <SectionCard :title="t('analyticsView.viewsTrend')" :meta="t('analyticsView.period.' + store.range)">
        <div v-if="bars.length" class="px-[15px] pb-[15px] pt-4">
          <div class="flex h-[168px] items-end gap-1.5 border-b border-line-row">
            <div v-for="(bar, index) in bars" :key="bar.date" class="group flex min-w-0 flex-1 flex-col items-center justify-end gap-1">
              <span class="pointer-events-none rounded bg-surface-raised px-1.5 py-0.5 font-mono text-[9px] text-content-secondary opacity-0 transition-opacity duration-150 group-hover:opacity-100">
                {{ formatNumber(bar.value) }}
              </span>
              <div
                class="w-full rounded-[4px_4px_2px_2px] transition-[height,background-color] duration-150"
                :class="index >= Math.max(0, bars.length - Math.max(1, Math.ceil(bars.length * 0.1))) ? 'bg-accent' : 'bg-line-control'"
                :style="{ height: `${bar.height}%` }"
              />
              <span v-if="index % 2 === 0" class="font-mono text-[9px] text-content-quaternary">{{ shortDate(bar.date) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="flex min-h-[168px] flex-col items-center justify-center px-4 py-8 text-center">
          <ChartBarIcon class="mb-2 h-7 w-7 text-content-quaternary" />
          <p class="text-[12px] font-semibold text-content-secondary">{{ t('analyticsView.noData') }}</p>
          <p class="mt-1 text-[11px] text-content-tertiary">{{ t('analyticsView.emptyDataDescription') }}</p>
        </div>
      </SectionCard>

      <div class="grid items-start gap-[14px] desktop:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <SectionCard :title="t('analyticsView.topVideos')" :meta="t('analyticsView.table.views')" body-class="overflow-x-auto">
          <div v-if="store.topVideos.length" class="min-w-[620px]">
            <div class="grid grid-cols-[34px_minmax(0,1fr)_76px_62px_76px_62px] gap-2.5 border-b border-line px-[15px] py-2 font-mono text-[10px] uppercase tracking-[0.06em] text-content-tertiary">
              <span>#</span><span>{{ t('analyticsView.table.video') }}</span><span class="text-right">{{ t('analyticsView.table.views') }}</span><span class="text-right">{{ t('analyticsView.table.watchTime') }}</span><span class="text-right">{{ t('analyticsView.table.platform') }}</span><span class="text-right">{{ t('analyticsView.table.engagementRate') }}</span>
            </div>
            <div v-for="(video, index) in store.topVideos" :key="video.videoId" class="grid min-h-[48px] grid-cols-[34px_minmax(0,1fr)_76px_62px_76px_62px] items-center gap-2.5 border-b border-line-row px-[15px] py-2 transition-colors duration-150 last:border-0 hover:bg-surface-raised">
              <span class="font-mono text-[11px] text-content-tertiary">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="flex min-w-0 items-center gap-2">
                <ThumbPlaceholder :src="video.thumbnailUrl" :width="52" :height="30" />
                <span class="truncate text-[12px] font-semibold text-content">{{ video.title }}</span>
              </div>
              <span class="text-right font-mono text-[11px] text-content">{{ formatNumber(video.totalViews) }}</span>
              <span class="text-right font-mono text-[11px] text-content-secondary">—</span>
              <div class="flex justify-end gap-1">
                <PlatformChip v-for="platform in video.platforms.filter(isRedesignPlatform)" :key="platform" :platform="toRedesignPlatform(platform)" size="sm" />
                <span v-if="!video.platforms.some(isRedesignPlatform)" class="font-mono text-[11px] text-content-tertiary">—</span>
              </div>
              <span class="text-right font-mono text-[11px] text-content-secondary">—</span>
            </div>
          </div>
          <div v-else class="flex flex-col items-center justify-center px-4 py-10 text-center">
            <p class="text-[12px] font-semibold text-content-secondary">{{ t('analyticsView.noTopVideos') }}</p>
            <p class="mt-1 text-[11px] text-content-tertiary">{{ t('analyticsView.emptyDataDescription') }}</p>
          </div>
        </SectionCard>

        <div class="space-[14px] space-y-2.5">
          <SectionCard v-for="insight in insights" :key="insight.tag" body-class="bg-surface-input px-[15px] py-[14px]">
            <div class="font-mono text-[10px] uppercase tracking-[0.14em] text-accent">{{ insight.tag }}</div>
            <h2 class="mt-2 text-[13.5px] font-bold text-content">{{ insight.title }}</h2>
            <p class="mt-1 text-[12px] leading-5 text-content-secondary">{{ insight.body }}</p>
          </SectionCard>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { ArrowDownTrayIcon, ChartBarIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import type { AnalyticsPeriod, TrendDataPoint } from '@/types/analytics'
import type { Platform } from '@/types/channel'
import KpiCard from '@/components/redesign/KpiCard.vue'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import ThumbPlaceholder from '@/components/redesign/ThumbPlaceholder.vue'
import { useRedesignPerformanceStore } from '@/stores/redesignPerformance'

const { t, locale } = useI18n()
const store = useRedesignPerformanceStore()
const { trends } = storeToRefs(store)
const periods: { value: AnalyticsPeriod }[] = [{ value: '7d' }, { value: '30d' }, { value: '90d' }]

const bars = computed(() => {
  const values = trends.value.map((point: TrendDataPoint) => point.totalViews)
  const max = Math.max(...values, 0)
  return trends.value.map((point) => ({
    date: point.date,
    value: point.totalViews,
    height: max > 0 ? Math.max(4, (point.totalViews / max) * 100) : 4,
  }))
})

const hasExportData = computed(() => store.trends.length > 0 || store.topVideos.length > 0)

const insights = computed(() => {
  const first = store.topVideos[0]
  const bestDay = [...store.trends].sort((a, b) => b.totalViews - a.totalViews)[0]
  return [
    {
      tag: t('analyticsView.topVideos'),
      title: first?.title ?? t('analyticsView.noTopVideos'),
      body: first ? `${formatNumber(first.totalViews)} · ${t('analyticsView.table.views')}` : t('analyticsView.emptyDataDescription'),
    },
    {
      tag: t('analyticsView.viewsTrend'),
      title: bestDay ? shortDate(bestDay.date) : t('analyticsView.noData'),
      body: bestDay ? `${formatNumber(bestDay.totalViews)} · ${t('analyticsView.table.views')}` : t('analyticsView.emptyDataDescription'),
    },
    {
      tag: t('analyticsView.platformComparison'),
      title: bestPlatform.value ? platformLabel(bestPlatform.value) : t('analyticsView.noData'),
      body: bestPlatform.value ? `${formatNumber(bestPlatformCount.value)} · ${t('analyticsView.table.video')}` : t('analyticsView.emptyDataDescription'),
    },
  ]
})

const REDESIGN_PLATFORMS = ['YT', 'IG', 'TT', 'FB', 'NV', 'TH'] as const
type RedesignPlatform = (typeof REDESIGN_PLATFORMS)[number]
const PLATFORM_CODES: Partial<Record<Platform, RedesignPlatform>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
}
const bestPlatform = computed(() => {
  const counts = new Map<RedesignPlatform, number>()
  for (const video of store.topVideos) {
    for (const platform of video.platforms) {
      const code = PLATFORM_CODES[platform]
      if (code) counts.set(code, (counts.get(code) ?? 0) + video.totalViews)
    }
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null
})
const bestPlatformCount = computed(() => {
  if (!bestPlatform.value) return 0
  return store.topVideos.reduce((total, video) =>
    video.platforms.some((platform) => PLATFORM_CODES[platform] === bestPlatform.value) ? total + video.totalViews : total,
  0)
})
function isRedesignPlatform(platform: Platform): platform is Platform & keyof typeof PLATFORM_CODES {
  return Boolean(PLATFORM_CODES[platform])
}
function toRedesignPlatform(platform: Platform): RedesignPlatform {
  return PLATFORM_CODES[platform] ?? 'YT'
}
function platformLabel(platform: RedesignPlatform): string {
  const labels: Record<RedesignPlatform, string> = {
    YT: t('platform.youtube'),
    IG: t('platform.instagram'),
    TT: t('platform.tiktok'),
    FB: t('platform.facebook'),
    NV: t('platform.naverClip'),
    TH: t('platform.threads'),
  }
  return labels[platform]
}
function formatNumber(value: number): string {
  return new Intl.NumberFormat(locale.value, { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}
function formatDelta(value: number, suffix: string): string {
  return `${value >= 0 ? '+' : ''}${value}${suffix}`
}
function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds)) return '—'
  if (seconds < 60) return `${Math.round(seconds)}s`
  return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
}
function shortDate(date: string): string {
  const parsed = new Date(date)
  return Number.isNaN(parsed.getTime()) ? date.slice(5, 10) : new Intl.DateTimeFormat(locale.value, { month: 'numeric', day: 'numeric' }).format(parsed)
}
function exportCsv() {
  const rows = [
    [t('analyticsView.export.date'), t('analyticsView.export.totalViews')],
    ...store.trends.map((point) => [point.date, String(point.totalViews)]),
    [],
    [t('analyticsView.table.video'), t('analyticsView.export.totalViews')],
    ...store.topVideos.map((video) => [video.title, String(video.totalViews)]),
  ]
  const csv = rows.map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `ongo-performance-${store.range}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(() => store.fetchPerformance())
</script>
