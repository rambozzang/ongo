<template>
  <section class="space-y-4" :aria-label="t('creatorPro.performance.workbench')">
    <div class="grid gap-4 desktop:grid-cols-2">
      <SectionCard :title="t('analyticsView.platformComparison')">
        <div class="overflow-x-auto p-4">
          <p class="mb-3 text-xs leading-5 text-content-tertiary">
            {{ t('creatorPro.performance.platformHint') }}
          </p>
          <table v-if="store.platformComparison.length" class="w-full text-left text-xs">
            <thead>
              <tr class="border-b border-line text-content-secondary">
                <th class="pb-2">{{ t('analyticsView.table.platform') }}</th>
                <th v-for="metric in metrics" :key="metric" class="pb-2 text-right">
                  {{ t(`creatorPro.performance.${metric}`) }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in sortedPlatforms"
                :key="item.platform"
                class="border-b border-line-row last:border-0"
              >
                <th class="py-3 font-semibold text-content">{{ item.platform }}</th>
                <td
                  v-for="metric in metrics"
                  :key="metric"
                  class="py-3 text-right tabular-nums text-content-secondary"
                >
                  {{ measured(item[metric]) }}
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="py-5 text-xs text-content-tertiary">
            {{
              store.failedPanels.includes('platforms')
                ? t('creatorPro.performance.panelFailed')
                : t('analyticsView.noData')
            }}
          </p>
        </div>
      </SectionCard>
      <SectionCard :title="t('creatorPro.performance.optimalTimes')">
        <div class="p-4">
          <p class="mb-3 text-xs leading-5 text-content-tertiary">
            {{ t('creatorPro.performance.timeHint') }}
          </p>
          <ol v-if="store.optimalTimes.length" class="space-y-3">
            <li
              v-for="slot in store.optimalTimes.slice(0, 5)"
              :key="`${slot.dayOfWeek}-${slot.hour}`"
              class="flex flex-wrap items-center gap-3 border-b border-line-row pb-3 last:border-0"
            >
              <div class="min-w-0 flex-1">
                <p class="text-sm font-semibold text-content">
                  {{ weekday(slot.dayOfWeek) }} · {{ slot.timeLabel }}
                </p>
                <p class="mt-1 text-xs text-content-tertiary">
                  {{
                    t('creatorPro.performance.timeEvidence', {
                      views: measured(slot.expectedViews),
                      confidence: slot.confidenceScore,
                    })
                  }}
                </p>
              </div>
              <button type="button" class="btn-secondary min-h-11 text-xs" @click="planAt(slot)">
                {{ t('creatorPro.performance.planAt') }}
              </button>
            </li>
          </ol>
          <p v-else class="py-5 text-xs text-content-tertiary">
            {{
              store.failedPanels.includes('optimalTimes')
                ? t('creatorPro.performance.panelFailed')
                : t('creatorPro.performance.needHistory')
            }}
          </p>
        </div>
      </SectionCard>
    </div>
    <SectionCard :title="t('creatorPro.performance.tags')">
      <div class="overflow-x-auto p-4">
        <p class="mb-3 text-xs leading-5 text-content-tertiary">
          {{ t('creatorPro.performance.tagHint') }}
        </p>
        <table v-if="store.tagPerformance.length" class="w-full min-w-[500px] text-left text-xs">
          <thead>
            <tr class="border-b border-line text-content-secondary">
              <th class="pb-2">{{ t('creatorPro.performance.tag') }}</th>
              <th class="pb-2 text-right">{{ t('creatorPro.performance.sample') }}</th>
              <th class="pb-2 text-right">{{ t('creatorPro.performance.averageViews') }}</th>
              <th class="pb-2 text-right">{{ t('creatorPro.performance.likeRate') }}</th>
              <th class="pb-2 text-right">{{ t('creatorPro.performance.trend') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="tag in store.tagPerformance.slice(0, 12)"
              :key="tag.tag"
              class="border-b border-line-row last:border-0"
            >
              <th class="py-3 font-semibold text-content">#{{ tag.tag.replace(/^#/, '') }}</th>
              <td class="text-right text-content-secondary">{{ tag.videoCount }}</td>
              <td class="text-right text-content-secondary">{{ measured(tag.avgViews) }}</td>
              <td class="text-right text-content-secondary">
                {{
                  tag.avgEngagement == null
                    ? t('analyticsView.notMeasured')
                    : `${tag.avgEngagement.toFixed(2)}%`
                }}<span
                  v-if="tag.unavailableMetrics?.includes('avgEngagement')"
                  class="ml-1 text-warning-strong"
                  >{{ t('creatorPro.performance.partial') }}</span
                >
              </td>
              <td class="text-right text-content-secondary">
                {{
                  tag.trend
                    ? t(`creatorPro.performance.${tag.trend}`)
                    : t('analyticsView.notMeasured')
                }}
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="py-5 text-xs text-content-tertiary">
          {{
            store.failedPanels.includes('tags')
              ? t('creatorPro.performance.panelFailed')
              : t('creatorPro.performance.noTags')
          }}
        </p>
      </div>
    </SectionCard>
    <SectionCard :title="t('creatorPro.performance.compare')">
      <div class="p-4">
        <div class="flex flex-wrap items-end gap-3">
          <p class="flex-1 text-xs leading-5 text-content-tertiary">
            {{ t('creatorPro.performance.compareHint') }}
          </p>
          <button
            type="button"
            class="btn-primary min-h-11 text-xs"
            :disabled="selected.length < 2 || busy || store.loading"
            @click="compare"
          >
            {{
              busy
                ? t('creatorPro.performance.comparing')
                : t('creatorPro.performance.compareSelected', { count: selected.length })
            }}
          </button>
        </div>
        <div class="mt-3 grid gap-2 tablet:grid-cols-2">
          <label
            v-for="video in store.topVideos"
            :key="video.videoId"
            class="flex min-h-11 items-center gap-2 rounded-lg border border-line-control px-3 text-xs text-content"
            ><input
              v-model="selected"
              type="checkbox"
              :value="video.videoId"
              :disabled="busy || (!selected.includes(video.videoId) && selected.length >= 5)"
            /><span class="truncate">{{ video.title }}</span></label
          >
        </div>
        <p v-if="error" role="alert" class="mt-3 text-xs text-error-strong">{{ error }}</p>
        <div v-if="comparison.length" class="mt-4 overflow-x-auto">
          <table class="w-full min-w-[620px] text-left text-xs">
            <thead>
              <tr class="border-b border-line text-content-secondary">
                <th class="pb-2">{{ t('analyticsView.table.video') }}</th>
                <th v-for="metric in compareMetrics" :key="metric.key" class="pb-2 text-right">
                  {{ t(metric.label) }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="video in comparison" :key="video.videoId" class="border-b border-line-row">
                <th class="max-w-[240px] py-3 text-content">
                  <button
                    type="button"
                    class="text-left font-semibold hover:text-accent"
                    @click="router.push(`/videos/${video.videoId}`)"
                  >
                    {{ video.title ?? `#${video.videoId}` }}
                  </button>
                </th>
                <td
                  v-for="metric in compareMetrics"
                  :key="metric.key"
                  class="py-3 text-right text-content-secondary"
                >
                  {{
                    metric.key === 'engagementRate' && video.engagementRate != null
                      ? `${video.engagementRate.toFixed(2)}%`
                      : measured(video[metric.key])
                  }}<span
                    v-if="
                      video.unavailableMetrics.includes(metric.key) && video[metric.key] != null
                    "
                    class="ml-1 text-warning-strong"
                    >{{ t('creatorPro.performance.partial') }}</span
                  >
                </td>
              </tr>
            </tbody>
          </table>
          <p class="mt-2 text-xs text-content-tertiary">
            {{ t('creatorPro.performance.compareCaution') }}
          </p>
        </div>
      </div>
    </SectionCard>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SectionCard from '@/components/redesign/SectionCard.vue'
import { useRedesignPerformanceStore } from '@/stores/redesignPerformance'
import { analyticsApi } from '@/api/analytics'
import type { OptimalTimeSlot, VideoCompareItem } from '@/types/analytics'
import { kstDateString } from '@/utils/kst'
const { t, locale } = useI18n()
const router = useRouter()
const store = useRedesignPerformanceStore()
const metrics = ['views', 'likes', 'comments', 'shares'] as const
const compareMetrics = [
  { key: 'totalViews', label: 'creatorPro.performance.views' },
  { key: 'avgDailyViews', label: 'creatorPro.performance.dailyViews' },
  { key: 'totalLikes', label: 'creatorPro.performance.likes' },
  { key: 'totalComments', label: 'creatorPro.performance.comments' },
  { key: 'engagementRate', label: 'analyticsView.table.engagementRate' },
] as const
const sortedPlatforms = computed(() =>
  [...store.platformComparison].sort((a, b) => (b.views ?? -1) - (a.views ?? -1)),
)
const selected = ref<number[]>([])
const comparison = ref<VideoCompareItem[]>([])
const busy = ref(false)
const error = ref('')
let sequence = 0
watch(
  () => store.range,
  () => {
    sequence++
    selected.value = []
    comparison.value = []
    error.value = ''
    busy.value = false
  },
)
watch(
  () => selected.value.slice(),
  () => {
    comparison.value = []
    error.value = ''
  },
)
function measured(value: number | null | undefined) {
  return value == null
    ? t('analyticsView.notMeasured')
    : new Intl.NumberFormat(locale.value, { maximumFractionDigits: 1 }).format(value)
}
function weekday(day: number) {
  return new Intl.DateTimeFormat(locale.value, { weekday: 'long', timeZone: 'UTC' }).format(
    new Date(Date.UTC(2026, 0, 4 + day)),
  )
}
function planAt(slot: OptimalTimeSlot) {
  const today = new Date(`${kstDateString()}T00:00:00Z`)
  const offset = (slot.dayOfWeek - today.getUTCDay() + 7) % 7
  today.setUTCDate(today.getUTCDate() + offset)
  let at = `${today.toISOString().slice(0, 10)}T${String(slot.hour).padStart(2, '0')}:00`
  if (new Date(`${at}:00+09:00`).getTime() <= Date.now()) {
    today.setUTCDate(today.getUTCDate() + 7)
    at = `${today.toISOString().slice(0, 10)}T${String(slot.hour).padStart(2, '0')}:00`
  }
  void router.push({ path: '/compose', query: { at } })
}
async function compare() {
  if (busy.value || selected.value.length < 2) return
  const current = ++sequence
  busy.value = true
  error.value = ''
  try {
    const response = await analyticsApi.videoCompare([...selected.value], store.range)
    if (current === sequence) comparison.value = response.videos
  } catch (cause) {
    if (current === sequence)
      error.value =
        cause instanceof Error ? cause.message : t('creatorPro.performance.compareFailed')
  } finally {
    if (current === sequence) busy.value = false
  }
}
</script>
