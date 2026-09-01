<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('revenue.title')" :description="$t('revenue.description')">
      <template #actions>
        <button
          class="btn-secondary flex items-center gap-1.5 text-body"
          @click="showAlertModal = true"
        >
          <BellIcon class="h-4 w-4" />
          {{ $t('revenue.alerts.title') }}
        </button>
        <button
          v-for="option in periodOptions"
          :key="option.value"
          class="rounded-lg border border-line-control px-3 py-1.5 text-body font-semibold transition-colors"
          :class="
            selectedPeriod === option.value
              ? 'bg-accent-dim text-accent'
              : 'bg-surface-input text-content-secondary hover:bg-surface-raised hover:text-content'
          "
          @click="selectedPeriod = option.value"
        >
          {{ option.label }}
        </button>
      </template>
    </PageHeader>

    <!-- 알림 설정 모달 -->
    <RevenueAlertModal v-model="showAlertModal" />

    <PageGuide :title="$t('revenue.pageGuideTitle')" :items="($tm('revenue.pageGuide') as string[])" />

    <div
      v-if="revenueStore.apiSummary && !revenueStore.apiSummary.platformRevenueAvailable"
      class="flex flex-wrap items-center gap-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2.5 text-body text-warning-strong"
      role="status"
    >
      <span class="min-w-0 flex-1">{{ revenueStore.apiSummary.platformRevenueUnavailableReason || $t('revenue.platformRevenueUnavailable') }}</span>
      <RouterLink
        v-if="revenueStore.apiSummary.platformRevenueReconnectRequired === true"
        to="/channels-v2?connect=1"
        class="shrink-0 rounded-md border border-warning-strong px-2 py-1 text-body-xs font-semibold transition-colors hover:bg-warning-strong hover:text-surface-base"
      >
        {{ $t('revenue.reconnectChannel') }}
      </RouterLink>
    </div>

    <div
      v-if="revenueStore.loadError"
      class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
      role="alert"
    >
      <span class="min-w-0 flex-1">{{ $t('revenue.loadFailed') }}</span>
      <button
        type="button"
        class="shrink-0 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold hover:bg-error-strong hover:text-surface-base"
        :disabled="revenueStore.loading"
        @click="revenueStore.fetchRevenue(selectedPeriod)"
      >
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- Sub-tab Navigation -->
    <OTabs v-model="activeTab" :tabs="revenueTabs" class="mb-0" />

    <!-- Loading State -->
    <div v-if="revenueStore.loading" class="flex items-center justify-center py-12">
      <div class="text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
    </div>

    <template v-else>
      <!-- 개요 탭 -->
      <template v-if="activeTab === 'overview'">
        <!-- AI 인사이트 섹션 -->
        <SectionCard :title="$t('revenue.insights.title')" :meta="$t('revenue.insights.description')">
          <div class="mb-4 flex items-center justify-between gap-4">
            <div>
              <h2 class="flex items-center gap-2 text-title font-semibold text-content">
                <SparklesIcon class="h-5 w-5 text-accent" />
                {{ $t('revenue.insights.title') }}
              </h2>
              <p class="mt-0.5 text-body text-content-tertiary">
                {{ $t('revenue.insights.description') }}
              </p>
              <!--
                AI 분석 기간은 **백엔드에 30일로 고정**돼 있다
                (`RevenueInsightUseCase:51` 이 `getRevenueSummary(userId, 30)`).
                위 기간 선택과 다르므로, 다르다는 사실을 화면에 적는다. 기간을 넘겨받는
                것처럼 보이게 만들면 90/180/365일을 고른 사용자가 그 기간의 분석으로 읽는다.
              -->
              <p
                v-if="revenueStore.loadedPeriod !== '30d'"
                data-testid="insights-fixed-period-notice"
                class="mt-1 text-body-xs text-content-tertiary"
              >
                {{ $t('revenue.insights.fixedPeriodNotice') }}
              </p>
            </div>
            <button
              class="btn-primary flex shrink-0 items-center gap-2 text-body"
              data-testid="revenue-generate-insight-button"
              :disabled="revenueStore.generateInsightLoading || revenueStore.creditBlocked || revenueStore.apiSummary?.platformRevenueAvailable !== true"
              @click="handleGenerateInsight"
            >
              <SparklesIcon class="h-4 w-4" />
              <span>{{ revenueStore.generateInsightLoading ? $t('revenue.insights.generating') : $t('revenue.insights.generate') }}</span>
              <span class="rounded-full bg-primary-500/30 px-1.5 py-0.5 text-body-xs">
                {{ $t('revenue.insights.generateHint') }}
              </span>
            </button>
          </div>

          <!-- 크레딧 부족 차단 블록: 생성 실패(잔액 부족) 시에만 노출 -->
          <div
            v-if="revenueStore.creditBlocked"
            class="mb-4 flex flex-col gap-2 rounded-lg border border-warning bg-warning-subtle px-4 py-3"
            role="alert"
          >
            <p class="text-body text-warning-strong">{{ $t('revenue.insights.creditBlocked') }}</p>
            <button
              type="button"
              data-testid="revenue-insight-credit-cta"
              class="btn-primary inline-flex w-full items-center justify-center gap-2"
              @click="showCreditModal = true"
            >
              {{ $t('revenue.insights.chargeCredits') }}
            </button>
          </div>

          <!-- 생성 오류(조회 오류와 분리): 일반 생성 실패 시 실제 메시지 보존 -->
          <div
            v-else-if="revenueStore.generateInsightError"
            class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong"
            role="alert"
          >
            <p>{{ revenueStore.generateInsightError }}</p>
            <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="handleGenerateInsight">다시 시도</button>
          </div>

          <!-- 로딩 -->
          <div v-if="revenueStore.insightsLoading" class="flex items-center justify-center py-8">
            <div class="text-body text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
          </div>

          <div
            v-else-if="revenueStore.apiSummary && !revenueStore.apiSummary.platformRevenueAvailable"
            class="rounded-lg border border-warning-subtle bg-warning-subtle p-4 text-body text-warning-strong"
            role="status"
          >
            <p>{{ $t('revenue.insights.unavailable') }}</p>
            <RouterLink
              v-if="revenueStore.apiSummary.platformRevenueReconnectRequired === true"
              to="/channels-v2?connect=1"
              class="mt-3 inline-flex rounded-md border border-warning-strong px-2 py-1 text-body-xs font-semibold transition-colors hover:bg-warning-strong hover:text-surface-base"
            >
              {{ $t('revenue.reconnectChannel') }}
            </RouterLink>
          </div>

          <div v-else-if="revenueStore.insightsError" class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong" role="alert">
            <p>{{ revenueStore.insightsError }}</p>
            <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="revenueStore.fetchInsights()">다시 시도</button>
          </div>

          <!-- 인사이트 목록 -->
          <div v-else-if="revenueStore.revenueInsights.length > 0" class="space-y-3">
            <RevenueInsightCard
              v-for="insight in revenueStore.revenueInsights"
              :key="insight.id"
              :insight="insight"
            />
          </div>

          <!-- 빈 상태 -->
          <div v-else class="flex flex-col items-center justify-center py-10 text-center">
            <SparklesIcon class="mb-3 h-10 w-10 text-content-quaternary" />
            <p class="text-body font-medium text-content-secondary">{{ $t('revenue.insights.empty') }}</p>
            <p class="mt-1 text-body-xs text-content-tertiary">{{ $t('revenue.insights.emptyHint') }}</p>
          </div>
        </SectionCard>

        <!-- Summary Cards -->
        <div class="grid gap-2.5 tablet:grid-cols-2 desktop:grid-cols-4">
          <!-- Total Revenue -->
          <KpiCard
            :label="$t('revenue.totalRevenue')"
            :value="formatCurrency(revenueStore.summary.totalRevenue)"
            :note="loadedPeriodLabel"
          />
          <!--
            성장률. 백엔드 growthPercent 는 **선택한 기간 vs 같은 길이의 직전 기간**이다.
            365일을 골랐는데 "월간 성장률"이라고 부르면 거짓이므로 라벨에 기간을 넣는다.
          -->
          <KpiCard
            :label="$t('revenue.periodGrowth', { period: loadedPeriodLabel })"
            :value="growthDisplay.text"
            :delta-variant="growthDisplay.variant"
            :note="growthDisplay.comparable ? loadedPeriodLabel : $t('revenue.growthUnavailable')"
            data-testid="revenue-growth-kpi"
          />
          <!--
            평균 RPM. 조회수를 주는 응답은 cpm-rpm 하나뿐이라, 그 표본이 없으면
            숫자를 만들지 않고 이유를 말한다.
          -->
          <KpiCard
            :label="$t('revenue.avgRpm')"
            :value="avgRpmDisplay.text"
            :note="avgRpmDisplay.note"
            data-testid="revenue-rpm-kpi"
          />
          <KpiCard
            :label="$t('revenue.topPlatform')"
            :value="topPlatformDisplay.text"
            :note="topPlatformDisplay.note"
            data-testid="revenue-top-platform-kpi"
          />
        </div>

        <!-- Revenue Trend Chart -->
        <!-- 일별 추세. 표(월 집계)와 단위가 다르다는 것을 meta 에 명시한다. -->
        <SectionCard
          :title="$t('revenue.revenueTrend')"
          :meta="`${loadedPeriodLabel} · ${$t('revenue.dailyGranularity')}`"
        >
          <div class="h-[400px]">
            <RevenueChart :data="filteredData" :period="selectedPeriod" />
          </div>
        </SectionCard>

        <!-- Platform Breakdown & Bar Chart -->
        <div class="page-grid page-grid--split">
          <!-- Platform Breakdown Doughnut -->
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">
              <!--
                `revenue.platformBreakdown` 은 **객체**다(`{ totalRevenue }`). 문자열로 쓰면
                키를 찾지 못해 화면에 키 경로가 그대로 노출된다. 제목 전용 키를 쓴다.
              -->
              {{ $t('revenue.platformBreakdownTitle') }}
            </h2>
            <div class="max-w-md">
              <RevenuePlatformBreakdown :data="revenueStore.platformBreakdown" />
            </div>
          </div>

          <!-- Platform Comparison Bar Chart -->
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenue.platformComparison') }}
            </h2>
            <!--
              데이터가 없으면 **빈 축만 있는 차트를 그리지 않는다.** 축과 눈금만 보이는
              그래프는 "측정했더니 0" 처럼 읽힌다. 없으면 없다고 쓴다.
            -->
            <p
              v-if="!revenueStore.loading && revenueStore.platformBreakdown.length === 0"
              data-testid="revenue-platform-empty"
              class="flex h-[300px] items-center justify-center rounded-lg border border-dashed border-gray-300 text-body text-muted-strong dark:border-gray-600"
            >
              {{ $t('revenue.noRevenueData') }}
            </p>
            <div v-else class="h-[300px]">
              <Bar :data="platformBarData" :options="barChartOptions" />
            </div>
          </div>
        </div>

        <!--
          월별 표. **일별 행을 그대로 넣지 않는다.**
          API 는 날짜별로 주므로 그대로 넣으면 하루치가 한 달처럼 보인다.
          스토어의 `monthlyAggregates` 가 YYYY-MM 으로 합친 값을 쓴다.
        -->
        <div class="card">
          <h2 class="mb-1 text-title font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('revenue.monthlyRevenueTable') }}
          </h2>
          <p class="mb-4 text-caption text-gray-500 dark:text-gray-400">
            {{ $t('revenue.monthlyAggregateNote', { period: loadedPeriodLabel }) }}
          </p>
          <p
            v-if="!revenueStore.loading && revenueStore.monthlyAggregates.length === 0"
            data-testid="revenue-table-empty"
            class="rounded-lg border border-dashed border-gray-300 p-4 text-center text-body text-muted-strong dark:border-gray-600"
          >
            {{ $t('revenue.noRevenueData') }}
          </p>
          <RevenueTable v-else :data="revenueStore.monthlyAggregates" />
        </div>
      </template>

      <!-- CPM·RPM 탭 -->
      <template v-if="activeTab === 'cpmRpm'">
        <div v-if="revenueStore.cpmRpmLoading" class="flex items-center justify-center py-12">
          <div class="text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
        </div>
        <div v-else-if="revenueStore.cpmRpmError" class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong" role="alert">
          <p>{{ revenueStore.cpmRpmError }}</p>
          <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="revenueStore.fetchCpmRpm()">다시 시도</button>
        </div>
        <template v-else-if="revenueStore.cpmRpmData">
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('revenue.cpmRpmTitle') }}</h2>
            <p class="mb-4 text-body text-gray-500 dark:text-gray-400">
              {{ $t('revenue.cpmRpmDesc') }}
            </p>
            <div v-if="revenueStore.cpmRpmData.platforms.length > 0" class="overflow-x-auto">
              <table class="w-full text-body">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-body-xs uppercase text-gray-500 dark:text-gray-400">
                    <th class="px-4 py-3 text-left font-medium">{{ $t('revenue.thPlatform') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thCpm') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thRpm') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thImpressions') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thViews') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thRevenue') }}</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
                  <tr
                    v-for="item in revenueStore.cpmRpmData.platforms"
                    :key="item.platform"
                    class="hover:bg-gray-50 dark:hover:bg-gray-700/50"
                  >
                    <td class="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      <div class="flex items-center gap-2">
                        <span
                          class="h-2 w-2 rounded-full"
                          :style="{ backgroundColor: getPlatformColor(item.platform) }"
                        />
                        {{ PLATFORM_CONFIG[item.platform as keyof typeof PLATFORM_CONFIG]?.label ?? item.platform }}
                      </div>
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      <span v-if="unitPrice(item.cpm)" data-testid="cpm-value">{{ unitPrice(item.cpm) }}</span>
                      <span
                        v-else
                        data-testid="cpm-unavailable"
                        class="text-gray-400 dark:text-gray-500"
                        :title="item.unavailableMetrics?.cpm ?? ''"
                      >{{ $t('revenue.unitPriceUnavailable') }}</span>
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      <span v-if="unitPrice(item.rpm)" data-testid="rpm-value">{{ unitPrice(item.rpm) }}</span>
                      <span
                        v-else
                        data-testid="rpm-unavailable"
                        class="text-gray-400 dark:text-gray-500"
                        :title="item.unavailableMetrics?.rpm ?? ''"
                      >{{ $t('revenue.unitPriceUnavailable') }}</span>
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      {{ item.impressions.toLocaleString() }}
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      {{ item.views.toLocaleString() }}
                    </td>
                    <td class="px-4 py-3 text-right font-medium text-gray-900 dark:text-gray-100">
                      {{ formatCurrency(item.revenueMicro / 1_000_000) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="flex items-center justify-center py-12 text-body text-gray-400 dark:text-gray-500">
              {{ $t('revenue.cpmRpmEmpty') }}
            </div>
          </div>
        </template>
      </template>

      <!-- 브랜드딜 탭 -->
      <template v-if="activeTab === 'brandDeals'">
        <div v-if="revenueStore.brandDealLoading" class="flex items-center justify-center py-12">
          <div class="text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
        </div>
        <div v-else-if="revenueStore.brandDealError" class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong" role="alert">
          <p>{{ revenueStore.brandDealError }}</p>
          <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="revenueStore.fetchBrandDealRevenue()">다시 시도</button>
        </div>
        <template v-else-if="revenueStore.brandDealData">
          <!-- 브랜드딜 요약 -->
          <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
            <div class="card border-t-4 border-warning">
              <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('revenue.brandDealTotalRevenue') }}</p>
              <p class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
                {{ formatCurrency(revenueStore.brandDealData.totalRevenueKrw) }}
              </p>
            </div>
            <div class="card border-t-4 border-warning">
              <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('revenue.brandDealCount') }}</p>
              <p class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
                {{ revenueStore.brandDealData.deals.length }}건
              </p>
            </div>
          </div>

          <!-- 브랜드딜 목록 -->
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('revenue.brandDealList') }}</h2>
            <div v-if="revenueStore.brandDealData.deals.length > 0" class="overflow-x-auto">
              <table class="w-full text-body">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-body-xs uppercase text-gray-500 dark:text-gray-400">
                    <th class="px-4 py-3 text-left font-medium">{{ $t('revenue.thBrand') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thAmount') }}</th>
                    <th class="px-4 py-3 text-center font-medium">{{ $t('revenue.thStatus') }}</th>
                    <th class="px-4 py-3 text-left font-medium">{{ $t('revenue.thPlatform') }}</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
                  <tr
                    v-for="deal in revenueStore.brandDealData.deals"
                    :key="deal.id"
                    class="hover:bg-gray-50 dark:hover:bg-gray-700/50"
                  >
                    <td class="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      {{ deal.brandName }}
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      {{ formatCurrency(deal.dealValueKrw) }}
                    </td>
                    <td class="px-4 py-3 text-center">
                      <span
                        class="inline-flex items-center rounded-full px-2 py-0.5 text-caption"
                        :class="dealStatusClass(deal.status)"
                      >
                        {{ dealStatusLabel(deal.status) }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-gray-700 dark:text-gray-300">
                      {{ deal.platform ? (PLATFORM_CONFIG[deal.platform as keyof typeof PLATFORM_CONFIG]?.label ?? deal.platform) : '-' }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="flex items-center justify-center py-12 text-body text-gray-400 dark:text-gray-500">
              {{ $t('revenue.brandDealEmpty') }}
            </div>
          </div>
        </template>
      </template>

    </template>

    <CreditPurchaseModal v-model="showCreditModal" @purchase="onCreditPurchase" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRevenueStore, DEFAULT_REVENUE_PERIOD, type RevenuePeriod } from '@/stores/revenue'
import { useCreditStore } from '@/stores/credit'
import {
  SparklesIcon,
  BellIcon,
} from '@heroicons/vue/24/outline'
import { PLATFORM_CONFIG } from '@/types/channel'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'
import RevenueChart from '@/components/revenue/RevenueChart.vue'
import RevenuePlatformBreakdown from '@/components/revenue/RevenuePlatformBreakdown.vue'
import RevenueTable from '@/components/revenue/RevenueTable.vue'
import RevenueInsightCard from '@/components/revenue/RevenueInsightCard.vue'
import RevenueAlertModal from '@/components/revenue/RevenueAlertModal.vue'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  type TooltipItem,
} from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

const { t } = useI18n()
const revenueStore = useRevenueStore()
const creditStore = useCreditStore()

// 알림 모달
const showAlertModal = ref(false)
// 크레딧 충전 모달
const showCreditModal = ref(false)

// AI 인사이트 생성
async function handleGenerateInsight() {
  // 차단 상태에서는 반복 요청을 막는다. 사용자가 충전 CTA 로 잔액을 채운 뒤
  // 직접 다시 눌러야만 재시도된다 (auto re-run 금지).
  if (revenueStore.creditBlocked) return
  try {
    await revenueStore.generateInsight()
  } catch (e) {
    console.error(t('revenue.insights.generateFailed'), e)
  }
}

async function onCreditPurchase() {
  await creditStore.fetchBalance()
  // 차단/생성 오류 상태만 해제하고 인사이트는 자동 재실행하지 않는다.
  revenueStore.creditBlocked = false
  revenueStore.generateInsightError = null
}

// ----- 탭 -----
type RevenueTab = 'overview' | 'cpmRpm' | 'brandDeals'
const activeTab = ref<RevenueTab>('overview')

const revenueTabs: { key: RevenueTab; label: string }[] = [
  { key: 'overview', label: t('revenue.tabOverview') },
  { key: 'cpmRpm', label: t('revenue.tabCpmRpm') },
  { key: 'brandDeals', label: t('revenue.tabBrandDeals') },
]

/**
 * 탭 데이터도 **헤더에서 고른 같은 기간**으로 부른다.
 *
 * 예전에는 기본값(CPM 30d, 브랜드딜 90d)을 그대로 썼다. 헤더가 "최근 365일"인데 탭
 * 숫자는 30일·90일이라, 기간 라벨과 탭 내용이 다시 어긋난다.
 */
watch(activeTab, (tab) => {
  if (tab === 'cpmRpm') {
    revenueStore.fetchCpmRpm(selectedPeriod.value)
  } else if (tab === 'brandDeals') {
    revenueStore.fetchBrandDealRevenue(selectedPeriod.value)
  }
})

// ----- 기간 필터 -----
/**
 * **값이 곧 API 가 받는 일수**다. 라벨도 일 단위로 말한다.
 *
 * 예전에는 값이 `'1','3','6','12'`(개월)인데 스토어는 항상 `'30d'` 를 불렀다. "1년"을
 * 골라도 30일치가 왔고, 화면은 그중 마지막 12행만 잘라 **12일치를 1년 총수익으로**
 * 보여줬다. 라벨을 개월로 두면 같은 착시가 반복되므로 일수로 말한다.
 */
const periodOptions: { value: RevenuePeriod; label: string }[] = [
  { value: '30d', label: t('revenue.period30Days') },
  { value: '90d', label: t('revenue.period90Days') },
  { value: '180d', label: t('revenue.period180Days') },
  { value: '365d', label: t('revenue.period365Days') },
]

const selectedPeriod = ref<RevenuePeriod>(DEFAULT_REVENUE_PERIOD)

/**
 * 라벨은 **실제로 불러온 기간**을 따른다.
 *
 * 선택 직후 아직 응답이 오지 않았을 때 선택값으로 라벨을 붙이면, 화면의 숫자는 이전
 * 기간인데 라벨만 새 기간이 된다. 그 짧은 순간이 바로 이번에 고치는 불일치와 같다.
 */
const loadedPeriodLabel = computed(
  () => periodOptions.find((option) => option.value === revenueStore.loadedPeriod)?.label ?? '',
)

/**
 * 차트에 그릴 **일별** 데이터. 자르지 않는다 — API 가 이미 요청한 기간만 준다.
 */
const filteredData = computed(() => revenueStore.dailyRevenue)

/**
 * 성장률 표시.
 *
 * 서버는 이전 기간 수익이 0 이면 `null` 을 준다 — **비율을 계산할 기준이 없다**는 뜻이다.
 * 그것을 퍼센트 숫자로 그리면 첫 수익이 발생한 크리에이터가 "+100%" 를 실제 성장률로
 * 읽는다. 색상(success/error)도 없는 판단을 만들어내므로 중립으로 둔다.
 */
const growthDisplay = computed(() => {
  const growth = revenueStore.summary.monthlyGrowth
  if (typeof growth !== 'number' || !Number.isFinite(growth)) {
    // KpiCard 는 success/error/warning/muted 만 받는다. 비교 불가는 좋고 나쁨이 아니므로
    // 중립인 muted 를 쓴다. success/error 를 주면 없는 판단을 색으로 주장하게 된다.
    return { comparable: false, text: t('revenue.growthUnavailableShort'), variant: 'muted' as const }
  }
  return {
    comparable: true,
    text: `${growth >= 0 ? '+' : ''}${growth.toFixed(1)}%`,
    variant: growth >= 0 ? ('success' as const) : ('error' as const),
  }
})

/**
 * 평균 RPM 카드. **표본이 없으면 숫자 대신 "측정 불가" 와 이유를 보여준다.**
 *
 * 예전에는 `₩${summary.averageRPM.toLocaleString()}` 이었고, 그 값은
 * `총수익 / (일수 × 10000)` 이었다. `10000` 은 어디서도 측정하지 않은 "하루 1만 조회"
 * 가정이라, 카드에 뜬 "₩1,234" 는 조회수를 한 번도 보지 않고 만든 숫자였다.
 */
const avgRpmDisplay = computed(() => {
  const rpm = revenueStore.summary.averageRPM
  if (typeof rpm !== 'number' || !Number.isFinite(rpm)) {
    const reason = revenueStore.summary.averageRpmUnavailableReason
    return {
      text: t('revenue.avgRpmUnavailableShort'),
      note: reason === 'loadFailed'
        ? t('revenue.avgRpmLoadFailed')
        : t('revenue.avgRpmNoViewSample'),
    }
  }
  return {
    text: `₩${rpm.toLocaleString('ko-KR')}`,
    note: t('revenue.avgRpmViewBased'),
  }
})

/**
 * 최고 수익 플랫폼 카드. **집계된 플랫폼이 없으면 플랫폼 이름을 만들지 않는다.**
 *
 * 예전 기본값은 `'YOUTUBE'` 였다. 수익 데이터가 하나도 없어도 "최고 수익 플랫폼:
 * YouTube · ₩0" 이 떴고, YouTube 를 연결한 적조차 없는 크리에이터에게도 그랬다.
 */
const topPlatformDisplay = computed(() => {
  const platform = revenueStore.summary.topPlatform
  const revenue = revenueStore.summary.topPlatformRevenue
  if (platform === null) {
    return { text: t('revenue.topPlatformUnavailableShort'), note: t('revenue.topPlatformUnavailable') }
  }
  const label = PLATFORM_CONFIG[platform as keyof typeof PLATFORM_CONFIG]?.label ?? platform
  return {
    text: label,
    note: typeof revenue === 'number' && Number.isFinite(revenue) ? formatCurrency(revenue) : undefined,
  }
})

/**
 * 기간을 바꾸면 **그 기간을 다시 불러온다.** 열려 있는 탭 데이터도 함께 맞춘다.
 *
 * 예전에는 감시자가 없어 버튼을 눌러도 API 를 다시 부르지 않았다. 30일 응답 안에서
 * 잘라내는 개수만 바뀌었을 뿐이라 "6개월"이 6일이었다.
 */
watch(selectedPeriod, (period) => {
  revenueStore.fetchRevenue(period)
  if (activeTab.value === 'cpmRpm') revenueStore.fetchCpmRpm(period)
  if (activeTab.value === 'brandDeals') revenueStore.fetchBrandDealRevenue(period)
})

const platformBarData = computed(() => {
  const breakdown = revenueStore.platformBreakdown
  const labels = breakdown.map(item =>
    PLATFORM_CONFIG[item.platform as keyof typeof PLATFORM_CONFIG]?.label ?? item.platform,
  )
  const data = breakdown.map(item => item.revenue)
  const colors = breakdown.map(item => getPlatformColor(item.platform))

  return {
    labels,
    datasets: [
      {
        label: t('revenue.chart.totalRevenue'),
        data,
        backgroundColor: colors,
      },
    ],
  }
})

const barChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      callbacks: {
        label: (context: TooltipItem<'bar'>) => {
          const value = context.parsed.y ?? 0
          return `수익: ₩${value.toLocaleString('ko-KR')}`
        },
      },
    },
  },
  scales: {
    x: {
      grid: {
        display: false,
      },
      ticks: {
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#6B7280',
      },
    },
    y: {
      beginAtZero: true,
      grid: {
        color: document.documentElement.classList.contains('dark') ? '#374151' : '#E5E7EB',
      },
      ticks: {
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#6B7280',
        callback: (value: string | number) => `₩${(Number(value) / 1000000).toFixed(1)}M`,
      },
    },
  },
}))

function getPlatformColor(platform: string): string {
  const platformKey = platform as keyof typeof PLATFORM_CONFIG
  return PLATFORM_CONFIG[platformKey]?.color || '#6B7280'
}

function formatCurrency(value: number): string {
  if (value >= 100000000) {
    return `₩${(value / 100000000).toFixed(1)}억`
  } else if (value >= 10000) {
    return `₩${(value / 10000).toFixed(0)}만`
  }
  return `₩${value.toLocaleString('ko-KR')}`
}

/**
 * 단가 칸에 넣을 문자열. **계산할 수 없으면 `null`** 을 돌려주고 호출부가 "측정 불가" 를 그린다.
 *
 * `null` 을 걸러내는 것만으로는 부족하다. 분모가 0 인 나눗셈이 어딘가에서 새어 들어오면
 * `NaN`/`Infinity` 가 되고, `toLocaleString` 은 그것을 "NaN"·"∞" 라는 **문자열로 성실히
 * 그려낸다.** 유한한 숫자만 통과시킨다.
 *
 * 측정된 0 원은 그대로 "₩0.00" 으로 보여준다 — 노출은 났는데 수익이 붙지 않았다는 실측이다.
 */
function unitPrice(value: number | null | undefined): string | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) return null
  return `₩${value.toLocaleString('ko-KR', { minimumFractionDigits: 2 })}`
}

function dealStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    NEGOTIATING: t('revenue.statusNegotiating'),
    CONFIRMED: t('revenue.statusConfirmed'),
    IN_PROGRESS: t('revenue.statusInProgress'),
    COMPLETED: t('revenue.statusCompleted'),
    CANCELLED: t('revenue.statusCancelled'),
  }
  return labels[status] ?? status
}

function dealStatusClass(status: string): string {
  const classes: Record<string, string> = {
    NEGOTIATING: 'bg-warning-subtle text-warning-strong',
    CONFIRMED: 'bg-info-subtle text-info-strong',
    IN_PROGRESS: 'bg-primary-100 text-primary-800 dark:bg-primary-900/30 dark:text-primary-400',
    COMPLETED: 'bg-success-subtle text-success-strong',
    CANCELLED: 'bg-error-subtle text-error-strong',
  }
  return classes[status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400'
}

onMounted(async () => {
  // 선택값을 **명시적으로** 넘긴다. 스토어 기본값에 기대면 첫 조회와 이후 기간 변경이
  // 서로 다른 출처를 보게 되고, 기본값이 바뀌는 순간 라벨과 데이터가 다시 어긋난다.
  await revenueStore.fetchRevenue(selectedPeriod.value)
  // 광고 수익을 수집하지 않는 플랫폼이면 AI가 빈 수익 데이터를 분석하지
  // 않도록 인사이트 API를 호출하지 않는다. 브랜드딜 수익은 별도 탭에서 본다.
  if (revenueStore.apiSummary?.platformRevenueAvailable === true) {
    await revenueStore.fetchInsights()
  }
})
</script>
