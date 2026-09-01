<template>
  <div class="min-h-full py-5 text-content tablet:py-6">
    <PageHeader :title="$t('competitor.title')" :description="$t('competitor.description')">
      <template #actions>
        <button
          type="button"
          data-testid="competitor-sync-button"
          class="btn-secondary inline-flex items-center gap-2 disabled:opacity-50"
          :disabled="store.syncing"
          @click="store.syncCompetitors()"
        >
          <ArrowPathIcon class="h-5 w-5" />
          {{ store.syncing ? $t('competitor.syncing') : $t('competitor.sync') }}
        </button>
        <button
          type="button"
          data-testid="competitor-add-button"
          class="btn-primary inline-flex items-center gap-2"
          @click="showAddModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('competitor.addChannel') }}
        </button>
      </template>
    </PageHeader>

    <!-- 목록 로드 실패: 실제 오류 문구를 그대로 노출 -->
    <div
      v-if="store.loadError"
      data-testid="competitor-error"
      class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
      role="alert"
    >
      <span class="min-w-0 flex-1">{{ store.loadError }}</span>
      <button
        type="button"
        class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold"
        :disabled="store.loading"
        @click="store.fetchCompetitors()"
      >
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- 동기화 성공: 서버 수치로 만든 안내 문구(성공을 무조건 "완료"로 치지 않음) -->
    <div
      v-if="syncSummaryText"
      data-testid="competitor-sync-success"
      class="mb-4 rounded-lg border border-content-subtle bg-card px-3 py-2.5 text-body"
      role="status"
    >
      {{ syncSummaryText }}
    </div>
    <!-- 동기화 실패 -->
    <div
      v-if="store.syncError"
      data-testid="competitor-sync-error"
      class="mb-4 flex items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
      role="alert"
    >
      <span class="min-w-0 flex-1">{{ store.syncError }}</span>
      <button
        type="button"
        class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold"
        :disabled="store.syncing"
        @click="store.syncCompetitors()"
      >
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- 채널 목록 -->
    <SectionCard :title="$t('competitor.tabList')" body-class="p-4" class="mb-6">
      <div v-if="store.loading" data-testid="competitor-loading" class="flex justify-center py-8">
        <LoadingSpinner />
      </div>
      <EmptyState
        v-else-if="store.competitors.length === 0"
        data-testid="competitor-empty"
        :title="$t('competitor.emptyList')"
        :description="$t('competitor.emptyListDescription')"
      >
        <template #action>
          <button
            type="button"
            class="btn-primary"
            data-testid="competitor-empty-add"
            @click="showAddModal = true"
          >
            {{ $t('competitor.addChannelAction') }}
          </button>
        </template>
      </EmptyState>
      <div v-else data-testid="competitor-list" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <CompetitorCard
          v-for="c in store.competitors"
          :key="c.id"
          :competitor="c"
          :selected="c.id === selectedComparisonId"
          @select="selectedComparisonId = $event"
          @toggle-tracking="store.toggleTracking(c.id)"
          @remove="store.removeCompetitor(c.id)"
        />
      </div>
    </SectionCard>

    <!-- 비교 분석: 실제 값만 비교하고 측정 불가는 숫자로 만들지 않는다 -->
    <SectionCard :title="$t('competitor.tabComparison')" body-class="p-4" class="mb-6">
      <div
        v-if="store.competitors.length === 0"
        data-testid="competitor-comparison-empty"
        class="py-6 text-center text-body text-content-subtle"
      >
        {{ $t('competitor.selectChannel') }}
      </div>
      <div v-else data-testid="competitor-comparison">
        <label class="flex flex-col gap-1">
          <span class="text-body-sm font-medium">{{ $t('competitor.comparisonTarget') }}</span>
          <select
            v-model.number="selectedComparisonId"
            data-testid="competitor-comparison-select"
            class="rounded-md border border-content-subtle bg-card px-3 py-2 text-body"
          >
            <option :value="null" disabled>{{ $t('competitor.selectChannel') }}</option>
            <option v-for="c in store.competitors" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <ComparisonChart
          v-if="activeComparison.length"
          :comparisons="activeComparison"
          :my-name="$t('competitor.myChannel')"
          :competitor-name="selectedCompetitorName"
        />
        <p
          v-else
          data-testid="competitor-comparison-none"
          class="mt-4 text-body text-content-subtle"
        >
          {{ $t('competitor.selectChannel') }}
        </p>
      </div>
    </SectionCard>

    <!-- AI 벤치마킹 인사이트 -->
    <SectionCard :title="$t('competitor.aiInsightTitle')" body-class="p-4" class="mb-6">
      <div data-testid="competitor-ai">
        <!-- 크레딧 부족 차단: 안정 코드로만 판단, 자동 재호출 금지 -->
        <div
          v-if="store.creditBlocked"
          data-testid="competitor-ai-credit-cta"
          class="flex flex-col gap-2 rounded-lg border border-warning bg-warning-subtle px-4 py-3"
          role="alert"
        >
          <p class="text-body text-warning-strong">{{ $t('competitor.creditBlocked') }}</p>
          <button
            type="button"
            class="btn-primary inline-flex w-full items-center justify-center gap-2"
            @click="showCreditModal = true"
          >
            {{ $t('competitor.chargeCredits') }}
          </button>
        </div>
        <!-- 일반 오류: 크레딧 부족이 아닌 실패 -->
        <div
          v-else-if="store.insightError"
          data-testid="competitor-ai-error"
          class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
          role="alert"
        >
          <span class="min-w-0 flex-1">{{ store.insightError }}</span>
          <button
            type="button"
            class="rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold"
            :disabled="store.insightLoading"
            @click="store.fetchInsight()"
          >
            {{ $t('action.retry') }}
          </button>
        </div>
        <div
          v-else-if="store.insightLoading"
          data-testid="competitor-ai-loading"
          class="py-6 text-center text-body text-content-subtle"
        >
          {{ $t('competitor.aiAnalyzing') }}
        </div>
        <div v-else-if="store.aiInsight" data-testid="competitor-ai-result" class="space-y-4">
          <p class="text-body">{{ store.aiInsight.summary }}</p>
          <div v-if="store.aiInsight.strengths.length">
            <h4 class="mb-1 text-body-sm font-semibold">{{ $t('competitor.strengths') }}</h4>
            <ul class="list-disc space-y-1 pl-5 text-body">
              <li v-for="(s, i) in store.aiInsight.strengths" :key="i">{{ s }}</li>
            </ul>
          </div>
          <div v-if="store.aiInsight.weaknesses.length">
            <h4 class="mb-1 text-body-sm font-semibold">{{ $t('competitor.weaknesses') }}</h4>
            <ul class="list-disc space-y-1 pl-5 text-body">
              <li v-for="(w, i) in store.aiInsight.weaknesses" :key="i">{{ w }}</li>
            </ul>
          </div>
          <div v-if="store.aiInsight.opportunities.length">
            <h4 class="mb-1 text-body-sm font-semibold">{{ $t('competitor.opportunities') }}</h4>
            <ul class="list-disc space-y-1 pl-5 text-body">
              <li v-for="(o, i) in store.aiInsight.opportunities" :key="i">{{ o }}</li>
            </ul>
          </div>
          <div v-if="store.aiInsight.recommendations.length">
            <h4 class="mb-1 text-body-sm font-semibold">{{ $t('competitor.recommendations') }}</h4>
            <ul class="list-disc space-y-1 pl-5 text-body">
              <li v-for="(r, i) in store.aiInsight.recommendations" :key="i">{{ r }}</li>
            </ul>
          </div>
        </div>
        <!-- 빈 상태 -->
        <div
          v-else
          data-testid="competitor-ai-empty"
          class="flex flex-col items-center gap-3 py-6 text-center"
        >
          <p class="text-body text-content-subtle">{{ $t('competitor.aiInsightEmpty') }}</p>
          <button
            type="button"
            data-testid="competitor-ai-button"
            class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
            :disabled="store.competitors.length === 0 || store.insightLoading"
            @click="store.fetchInsight()"
          >
            <SparklesIcon class="h-5 w-5" />
            {{ $t('competitor.aiAnalyzeButton') }}
          </button>
          <p v-if="store.competitors.length === 0" class="text-body-xs text-content-subtle">
            {{ $t('competitor.noInsightData') }}
          </p>
        </div>
      </div>
    </SectionCard>

    <AddCompetitorModal :is-open="showAddModal" @close="showAddModal = false" @add="onAddCompetitor" />
    <CreditPurchaseModal v-model="showCreditModal" :required-credits="8" @purchase="onCreditPurchase" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ArrowPathIcon,
  PlusIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import { useI18n } from 'vue-i18n'
import { useCompetitorStore } from '@/stores/competitor'
import { useCreditStore } from '@/stores/credit'
import type { Competitor, CompetitorSyncResponse } from '@/types/competitor'
import PageHeader from '@/components/common/PageHeader.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import CompetitorCard from '@/components/competitor/CompetitorCard.vue'
import ComparisonChart from '@/components/competitor/ComparisonChart.vue'
import AddCompetitorModal from '@/components/competitor/AddCompetitorModal.vue'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'

const { t } = useI18n()
const store = useCompetitorStore()
const creditStore = useCreditStore()
const showAddModal = ref(false)
const showCreditModal = ref(false)
const selectedComparisonId = ref<number | null>(null)

const selectedCompetitor = computed(() =>
  store.competitors.find((c) => c.id === selectedComparisonId.value) ?? null,
)
const selectedCompetitorName = computed(() => selectedCompetitor.value?.name ?? '')
const activeComparison = computed(() =>
  selectedComparisonId.value != null ? store.getComparison(selectedComparisonId.value) : [],
)

// 서버가 준 실제 동기화 수치(requested/synced/unsupported/failed)로 안내 문구를 만든다.
// 성공을 무조건 "완료"로 치지 않고, 갱신된 건수·미지원·실패를 그대로 보여준다.
function buildSyncSummary(r: CompetitorSyncResponse | null): string {
  if (!r) return ''
  if (r.requested === 0) return t('competitor.syncEmpty')
  if (r.synced === 0) return t('competitor.syncNoUpdate', { unsupported: r.unsupported })
  if (r.unsupported === 0 && r.failed === 0) return t('competitor.syncDone', { synced: r.synced })
  return t('competitor.syncSummary', {
    synced: r.synced,
    unsupported: r.unsupported,
    failed: r.failed,
  })
}
const syncSummaryText = computed(() => buildSyncSummary(store.lastSync))

async function onAddCompetitor(data: Omit<Competitor, 'id' | 'addedAt'>) {
  await store.addCompetitor(data)
  showAddModal.value = false
}

// 크레딧 충전 후 차단만 해제한다. 인사이트는 자동 재호출하지 않는다.
async function onCreditPurchase() {
  await creditStore.fetchBalance()
  store.creditBlocked = false
}

onMounted(() => {
  void store.fetchCompetitors()
})
</script>
