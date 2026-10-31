<template>
  <div class="min-h-full py-5 text-content tablet:py-6">
    <PageHeader :title="$t('channelAudit.title')" :description="$t('channelAudit.description')">
      <template #actions>
        <button
          data-testid="channel-audit-generate-header"
          :disabled="store.generating || store.creditBlocked"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="handleGenerate"
        >
          <SparklesIcon class="h-5 w-5" />
          {{ store.generating ? $t('channelAudit.generating') : $t('channelAudit.generateButton') }}
        </button>
      </template>
    </PageHeader>

    <div v-if="store.loadError || store.generationError" class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong" role="alert">
      <span class="min-w-0 flex-1">{{ store.generationError || store.loadError }}</span>
      <button v-if="store.loadError" type="button" class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="store.loading" @click="store.fetchReports()">
        다시 시도
      </button>
    </div>

    <!-- 크레딧 부족 차단 블록: 생성 실패(잔액 부족) 시에만 노출 -->
    <div
      v-if="store.creditBlocked"
      class="mb-4 flex flex-col gap-2 rounded-lg border border-warning bg-warning-subtle px-4 py-3"
      role="alert"
    >
      <p class="text-body text-warning-strong">{{ $t('channelAudit.creditBlocked') }}</p>
      <button
        type="button"
        data-testid="channel-audit-credit-cta"
        class="btn-primary inline-flex w-full items-center justify-center gap-2"
        @click="showCreditModal = true"
      >
        {{ $t('channelAudit.chargeCredits') }}
      </button>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" />

    <!-- Empty -->
    <EmptyState
      v-else-if="store.loadError && store.reports.length === 0"
      :title="store.loadError"
      description="연결 상태를 확인한 뒤 다시 시도해 주세요."
    >
      <template #action>
        <button class="btn-primary" @click="store.fetchReports()">다시 시도</button>
      </template>
    </EmptyState>

    <EmptyState
      v-else-if="store.reports.length === 0"
      :title="$t('channelAudit.emptyTitle')"
      :description="$t('channelAudit.emptyDescription')"
    >
      <template #action>
        <button
          data-testid="channel-audit-generate-empty"
          :disabled="store.generating || store.creditBlocked"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="handleGenerate"
        >
          <SparklesIcon class="h-5 w-5" />
          {{ $t('channelAudit.generateButton') }}
        </button>
      </template>
    </EmptyState>

    <!-- Reports List -->
    <div v-else class="space-y-4">
      <div
        v-for="report in store.reports"
        :key="report.id"
        class="card cursor-pointer transition-colors hover:border-line-hover"
        @click="openDetail(report)"
      >
          <div class="flex items-center justify-between gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex flex-wrap items-center gap-3 mb-2">
              <!-- 새 보고서는 AI 점수가 없다. 실측 조회수를 보이고, 예전 보고서의 AI 점수만 추정 배지와 함께 보인다. -->
              <template v-if="report.measured">
                <span class="font-mono text-h3 font-bold text-content" data-testid="audit-list-views">
                  {{ formatCount(report.measured.totalViews) }}
                </span>
                <span class="text-body-xs text-content-tertiary">{{ $t('channelAudit.measured.viewsIn', { days: report.measured.periodDays }) }}</span>
              </template>
              <template v-else-if="report.overallScore != null">
                <span class="font-mono text-h1 font-bold text-accent">{{ report.overallScore }}</span>
                <span class="text-body-sm text-content-tertiary">/ 100</span>
                <AiEstimateBadge :basis="report.overallScoreEstimate?.basis" />
              </template>
              <span class="text-body-xs text-content-quaternary">
                {{ formatDate(report.createdAt) }}
              </span>
            </div>
            <p class="line-clamp-2 text-body-sm text-content-secondary">{{ report.growthForecast || 'AI 분석 결과가 저장되었습니다.' }}</p>
          </div>
          <ChevronRightIcon class="h-5 w-5 shrink-0 text-content-tertiary" />
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="store.totalPages > 1" class="flex items-center justify-center gap-3 pt-2">
        <button :disabled="!store.hasPrevPage" class="btn-secondary disabled:opacity-40" @click="store.prevPage()">
          {{ $t('action.prev') }}
        </button>
        <span class="font-mono text-body-sm text-content-secondary">
          {{ store.page + 1 }} / {{ store.totalPages }}
        </span>
        <button :disabled="!store.hasNextPage" class="btn-secondary disabled:opacity-40" @click="store.nextPage()">
          {{ $t('action.next') }}
        </button>
      </div>
    </div>

    <!-- Detail Modal -->
    <BaseModal
      v-model="detailModalOpen"
      :title="$t('channelAudit.detailTitle')"
      max-width="xl"
    >
      <div v-if="store.selectedReport" class="space-y-6">
        <!-- 실측 지표(감사 시점). 수집하지 않는 지표는 "측정 안 됨" -->
        <div v-if="store.selectedReport.measured" class="card" data-testid="audit-measured">
          <h3 class="mb-2 text-body font-semibold text-content">
            {{ $t('channelAudit.measured.title', { days: store.selectedReport.measured.periodDays }) }}
            <span class="ml-1 text-body-xs font-normal text-content-tertiary">{{ $t('common.aiEstimate.measured') }}</span>
          </h3>
          <dl class="grid grid-cols-2 gap-2 tablet:grid-cols-3">
            <div v-for="row in measuredRows(store.selectedReport.measured)" :key="row.key">
              <dt class="text-body-xs text-content-tertiary">{{ $t(`channelAudit.measured.${row.key}`) }}</dt>
              <dd class="font-mono text-body font-semibold text-content" :data-testid="`audit-measured-${row.key}`">{{ row.value }}</dd>
            </div>
          </dl>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <!-- 예전 보고서의 AI 점수만. 새 보고서는 점수를 매기지 않는다. -->
          <div
            v-if="store.selectedReport.overallScore != null"
            class="rounded-[11px] border border-line-control bg-accent-dim p-4 text-center"
            data-testid="audit-legacy-score"
          >
            <p class="font-mono text-display font-bold text-accent">
              {{ store.selectedReport.overallScore }}
            </p>
            <p class="mt-1 text-body-xs text-content-tertiary">{{ $t('channelAudit.overall') }}</p>
            <AiEstimateBadge class="mt-2" :basis="store.selectedReport.overallScoreEstimate?.basis" show-basis />
          </div>
          <div class="rounded-[11px] border border-line-control bg-surface-raised p-4 text-center">
            <p class="font-mono text-h1 font-bold text-content">{{ store.selectedReport.actionItems.length }}</p>
            <p class="mt-1 text-body-xs text-content-tertiary">{{ $t('channelAudit.actionItems') }}</p>
          </div>
        </div>

        <!-- Summary -->
        <div class="card">
          <div class="mb-2 flex items-center gap-2">
            <h3 class="text-h3 text-content">{{ $t('channelAudit.summaryLabel') }}</h3>
            <AiEstimateBadge :basis="store.selectedReport.forecastEstimate?.basis" />
          </div>
          <p class="whitespace-pre-line text-body-sm text-content-secondary">{{ store.selectedReport.growthForecast || 'AI 진단 요약이 없습니다.' }}</p>
        </div>

        <!-- Strengths & Weaknesses -->
        <div class="grid tablet:grid-cols-2 gap-4">
          <div class="card">
            <h3 class="mb-3 text-body font-semibold text-success-strong">{{ $t('channelAudit.strengths') }}</h3>
            <ul class="space-y-1.5">
              <li
                v-for="(s, i) in store.selectedReport.strengths"
                :key="i"
                class="flex gap-2 text-body text-gray-700 dark:text-gray-300"
              >
                <CheckCircleIcon class="h-4 w-4 text-success-strong shrink-0 mt-0.5" />
                {{ s }}
              </li>
            </ul>
          </div>
          <div class="card">
            <h3 class="mb-3 text-body font-semibold text-error-strong">{{ $t('channelAudit.weaknesses') }}</h3>
            <ul class="space-y-1.5">
              <li
                v-for="(w, i) in store.selectedReport.weaknesses"
                :key="i"
                class="flex gap-2 text-body text-gray-700 dark:text-gray-300"
              >
                <ExclamationCircleIcon class="h-4 w-4 text-error-strong shrink-0 mt-0.5" />
                {{ w }}
              </li>
            </ul>
          </div>
        </div>

        <!-- Action Items -->
        <div class="card">
            <h3 class="mb-3 text-h3 text-content">{{ $t('channelAudit.actionItems') }}</h3>
          <div class="space-y-3">
            <div
              v-for="(item, i) in store.selectedReport.actionItems"
              :key="i"
              class="flex gap-3 rounded-lg border p-3"
              :class="{
                'border-error bg-error-subtle': item.priority === 'HIGH',
                'border-warning bg-warning-subtle': item.priority === 'MEDIUM',
                'border-line-control bg-surface-raised': item.priority === 'LOW',
              }"
            >
              <span
                class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider h-fit mt-0.5"
                :class="{
                  'bg-error-subtle text-error-strong': item.priority === 'HIGH',
                  'bg-warning-subtle text-warning-strong': item.priority === 'MEDIUM',
                  'bg-muted-subtle text-muted-strong': item.priority === 'LOW',
                }"
              >
                {{ item.priority }}
              </span>
              <div>
                <p class="text-body-sm font-semibold text-content">{{ item.action }}</p>
                <p class="mt-0.5 text-body-xs text-content-secondary">{{ item.expectedImpact }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Outlier Videos -->
        <div v-if="store.selectedReport.outlierVideos.length > 0" class="card">
          <h3 class="mb-3 text-body font-semibold text-gray-900 dark:text-white">{{ $t('channelAudit.outlierVideos') }}</h3>
          <div class="space-y-3">
            <div
              v-for="video in store.selectedReport.outlierVideos"
              :key="`${video.videoTitle}-${video.metric}`"
              class="flex gap-3 items-start"
            >
              <div class="min-w-0 flex-1">
                <p class="truncate text-body-sm font-semibold text-content">{{ video.videoTitle }}</p>
                <p class="mt-0.5 text-body-xs text-content-tertiary">{{ video.metric }}</p>
                <p class="mt-0.5 text-body-xs text-content-tertiary">{{ video.reason }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <button class="btn-secondary" @click="detailModalOpen = false">
          {{ $t('action.close') }}
        </button>
      </template>
    </BaseModal>

    <CreditPurchaseModal v-model="showCreditModal" @purchase="onCreditPurchase" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  SparklesIcon,
  ChevronRightIcon,
  CheckCircleIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import { useChannelAuditStore } from '@/stores/channelAudit'
import { useCreditStore } from '@/stores/credit'
import type { ChannelAuditMeasured, ChannelAuditReport } from '@/types/channelAudit'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import AiEstimateBadge from '@/components/common/AiEstimateBadge.vue'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'

const store = useChannelAuditStore()
const creditStore = useCreditStore()
const detailModalOpen = ref(false)
const showCreditModal = ref(false)

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

const { t } = useI18n()

/** 숫자가 없으면 "측정 안 됨" — 0 으로 보이면 "아무도 안 봤다"는 관측이 된다. */
function formatCount(value: number | null): string {
  return value == null ? t('common.aiEstimate.notMeasured') : value.toLocaleString()
}

function measuredRows(m: ChannelAuditMeasured) {
  return [
    { key: 'views', value: formatCount(m.totalViews) },
    {
      key: 'viewsChange',
      value: m.totalViewsChange == null ? t('common.aiEstimate.notMeasured') : `${m.totalViewsChange > 0 ? '+' : ''}${m.totalViewsChange.toFixed(1)}%`,
    },
    { key: 'likes', value: formatCount(m.totalLikes) },
    { key: 'comments', value: formatCount(m.totalComments) },
    { key: 'subscribers', value: formatCount(m.subscriberCount) },
    { key: 'videos', value: m.analyzedVideoCount.toLocaleString() },
  ]
}

function openDetail(report: ChannelAuditReport) {
  store.selectedReport = report
  detailModalOpen.value = true
}

async function handleGenerate() {
  // 차단 상태에서는 반복 요청을 막는다. 사용자가 충전 CTA 로 잔액을 채운 뒤
  // 직접 다시 눌러야만 재시도된다 (auto re-run 금지).
  if (store.creditBlocked) return
  const report = await store.generateAudit()
  if (report) {
    detailModalOpen.value = true
  }
}

async function onCreditPurchase() {
  await creditStore.fetchBalance()
  // 차단/에러 상태만 해제하고 채널 진단은 자동 재실행하지 않는다.
  store.creditBlocked = false
  store.generationError = null
}

onMounted(() => {
  store.fetchReports()
})
</script>
