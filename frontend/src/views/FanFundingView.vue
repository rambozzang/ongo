<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('fanFunding.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('fanFunding.description') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-for="option in periodOptions"
          :key="option.value"
          class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
          :class="
            selectedPeriod === option.value
              ? 'bg-primary-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
          "
          @click="handlePeriodChange(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('fanFunding.pageGuideTitle')"
      :items="($tm('fanFunding.pageGuide') as string[])"
    />

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else-if="store.summary">
      <!-- Summary Stats Row -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3 desktop:grid-cols-5">
        <!-- Total Revenue -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanFunding.totalRevenue') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatKRW(store.summary.totalRevenue) }}
          </p>
        </div>

        <!-- This Month -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanFunding.thisMonth') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatKRW(store.summary.thisMonthRevenue) }}
          </p>
        </div>

        <!-- Growth Rate -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanFunding.growthRate') }}
          </p>
          <p class="mt-1 text-xl font-bold" :class="store.summary.growthRate >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'">
            {{ store.summary.growthRate >= 0 ? '+' : '' }}{{ store.summary.growthRate.toFixed(1) }}%
          </p>
        </div>

        <!-- Membership Count -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanFunding.membershipCount') }}
          </p>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.membershipCount.toLocaleString('ko-KR') }}{{ $t('fanFunding.personUnit') }}
          </p>
        </div>

        <!-- MRR -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('fanFunding.mrr') }}
          </p>
          <p class="mt-1 text-xl font-bold text-primary-600 dark:text-primary-400">
            {{ formatKRW(store.summary.membershipMRR) }}
          </p>
        </div>
      </div>

      <!-- Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex space-x-6">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            @click="activeTab = tab.key"
            :class="[
              activeTab === tab.key
                ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
              'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
            ]"
          >
            {{ tab.label }}
          </button>
        </nav>
      </div>

      <!-- ========== Overview Tab ========== -->
      <template v-if="activeTab === 'overview'">
        <!-- Revenue Chart -->
        <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('fanFunding.dailyTrends') }}
          </h2>
          <FundingChart :trends="store.summary.dailyTrends" />
        </div>

        <div class="mb-6 grid grid-cols-1 gap-6 desktop:grid-cols-2">
          <!-- Source Breakdown -->
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('fanFunding.sourceBreakdown') }}
            </h2>
            <div class="space-y-3">
              <div
                v-for="src in store.summary.bySource"
                :key="src.source"
                class="flex items-center gap-3"
              >
                <span
                  class="inline-flex min-w-[100px] items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                  :class="sourceColorClass(src.source)"
                >
                  {{ sourceLabel(src.source) }}
                </span>
                <div class="flex-1">
                  <div class="h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
                    <div
                      class="h-full rounded-full"
                      :class="sourceBarColor(src.source)"
                      :style="{ width: `${src.percentage}%` }"
                    />
                  </div>
                </div>
                <span class="w-20 text-right text-xs font-medium text-gray-700 dark:text-gray-300">
                  {{ formatKRW(src.amount) }}
                </span>
                <span class="w-10 text-right text-xs text-gray-400 dark:text-gray-500">
                  {{ src.percentage.toFixed(1) }}%
                </span>
              </div>
            </div>
          </div>

          <!-- Platform Breakdown -->
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('fanFunding.platformBreakdown') }}
            </h2>
            <div class="space-y-3">
              <div
                v-for="pl in store.summary.byPlatform"
                :key="pl.platform"
                class="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2.5 dark:bg-gray-800"
              >
                <div class="flex items-center gap-2">
                  <span
                    class="inline-block h-2.5 w-2.5 rounded-full"
                    :style="{ backgroundColor: platformColor(pl.platform) }"
                  />
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ platformLabel(pl.platform) }}
                  </span>
                </div>
                <div class="text-right">
                  <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
                    {{ formatKRW(pl.amount) }}
                  </span>
                  <span class="ml-2 text-xs text-gray-400 dark:text-gray-500">
                    {{ pl.count.toLocaleString('ko-KR') }}{{ $t('fanFunding.countUnit') }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Top Donors -->
        <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('fanFunding.topDonors') }}
          </h2>
          <div class="grid grid-cols-1 gap-3 tablet:grid-cols-2 desktop:grid-cols-3">
            <DonorCard
              v-for="(donor, i) in store.summary.topDonors"
              :key="donor.name"
              :donor="donor"
              :rank="i + 1"
            />
          </div>
        </div>
      </template>

      <!-- ========== Transactions Tab ========== -->
      <template v-if="activeTab === 'transactions'">
        <!-- Filter -->
        <div class="mb-4 flex flex-col gap-3 tablet:flex-row tablet:items-center">
          <select
            v-model="sourceFilter"
            class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
          >
            <option value="">{{ $t('fanFunding.allSources') }}</option>
            <option v-for="src in sourceOptions" :key="src.value" :value="src.value">
              {{ src.label }}
            </option>
          </select>
          <select
            v-model="platformFilter"
            class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
          >
            <option value="">{{ $t('fanFunding.allPlatforms') }}</option>
            <option value="youtube">YouTube</option>
            <option value="tiktok">TikTok</option>
            <option value="instagram">Instagram</option>
            <option value="naverclip">Naver Clip</option>
          </select>
        </div>

        <!-- Table -->
        <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div v-if="filteredTransactions.length === 0" class="flex items-center justify-center py-12 text-sm text-gray-400 dark:text-gray-500">
            {{ $t('fanFunding.noTransactions') }}
          </div>
          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400">
                  <th class="px-4 py-3 text-left font-medium">{{ $t('fanFunding.thDate') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('fanFunding.thSource') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('fanFunding.thDonor') }}</th>
                  <th class="px-4 py-3 text-right font-medium">{{ $t('fanFunding.thAmount') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('fanFunding.thMessage') }}</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
                <tr
                  v-for="tx in filteredTransactions"
                  :key="tx.id"
                  class="hover:bg-gray-50 dark:hover:bg-gray-700/50"
                >
                  <td class="whitespace-nowrap px-4 py-3 text-gray-500 dark:text-gray-400">
                    {{ formatDateTime(tx.createdAt) }}
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                      :class="sourceColorClass(tx.source)"
                    >
                      {{ sourceLabel(tx.source) }}
                    </span>
                  </td>
                  <td class="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                    {{ tx.donorName }}
                  </td>
                  <td class="whitespace-nowrap px-4 py-3 text-right font-bold text-gray-900 dark:text-gray-100">
                    {{ formatKRW(tx.amount) }}
                  </td>
                  <td class="max-w-[200px] truncate px-4 py-3 text-gray-500 dark:text-gray-400">
                    {{ tx.message || '-' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>

      <!-- ========== Goals Tab ========== -->
      <template v-if="activeTab === 'goals'">
        <!-- Add Goal Form -->
        <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h2 class="mb-3 text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('fanFunding.addGoal') }}
          </h2>
          <form class="flex flex-col gap-3 tablet:flex-row tablet:items-end" @submit.prevent="handleCreateGoal">
            <div class="flex-1">
              <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                {{ $t('fanFunding.goalTitle') }}
              </label>
              <input
                v-model="newGoal.title"
                type="text"
                required
                :placeholder="$t('fanFunding.goalTitlePlaceholder')"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
              />
            </div>
            <div class="w-full tablet:w-40">
              <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                {{ $t('fanFunding.goalTargetAmount') }}
              </label>
              <input
                v-model.number="newGoal.targetAmount"
                type="number"
                required
                min="1"
                :placeholder="$t('fanFunding.goalAmountPlaceholder')"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
              />
            </div>
            <div class="w-full tablet:w-40">
              <label class="mb-1 block text-xs font-medium text-gray-700 dark:text-gray-300">
                {{ $t('fanFunding.goalDeadline') }}
              </label>
              <input
                v-model="newGoal.deadline"
                type="date"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
              />
            </div>
            <button
              type="submit"
              :disabled="!newGoal.title || !newGoal.targetAmount"
              class="rounded-lg bg-primary-600 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {{ $t('fanFunding.createGoal') }}
            </button>
          </form>
        </div>

        <!-- Goal Cards -->
        <div v-if="store.goals.length === 0" class="flex items-center justify-center py-12 text-sm text-gray-400 dark:text-gray-500">
          {{ $t('fanFunding.noGoals') }}
        </div>
        <div v-else class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <GoalCard
            v-for="goal in store.goals"
            :key="goal.id"
            :goal="goal"
            @delete="handleDeleteGoal"
          />
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFanFundingStore } from '@/stores/fanFunding'
import type { FundingSource } from '@/types/fanFunding'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import FundingChart from '@/components/fanfunding/FundingChart.vue'
import DonorCard from '@/components/fanfunding/DonorCard.vue'
import GoalCard from '@/components/fanfunding/GoalCard.vue'

const { t } = useI18n({ useScope: 'global' })
const store = useFanFundingStore()

// ---- Period ----
const selectedPeriod = ref('30d')
const periodOptions = [
  { value: '7d', label: t('fanFunding.period7d') },
  { value: '30d', label: t('fanFunding.period30d') },
  { value: '90d', label: t('fanFunding.period90d') },
]

function handlePeriodChange(period: string) {
  selectedPeriod.value = period
  store.fetchSummary(period)
}

// ---- Tabs ----
type TabKey = 'overview' | 'transactions' | 'goals'
const activeTab = ref<TabKey>('overview')

const tabs: { key: TabKey; label: string }[] = [
  { key: 'overview', label: t('fanFunding.tabOverview') },
  { key: 'transactions', label: t('fanFunding.tabTransactions') },
  { key: 'goals', label: t('fanFunding.tabGoals') },
]

// ---- Transaction filters ----
const sourceFilter = ref('')
const platformFilter = ref('')

const filteredTransactions = computed(() => {
  return store.transactions.filter((tx) => {
    if (sourceFilter.value && tx.source !== sourceFilter.value) return false
    if (platformFilter.value && tx.platform !== platformFilter.value) return false
    return true
  })
})

const sourceOptions: { value: FundingSource; label: string }[] = [
  { value: 'SUPER_CHAT', label: 'Super Chat' },
  { value: 'MEMBERSHIP', label: t('fanFunding.srcMembership') },
  { value: 'SUPER_THANKS', label: 'Super Thanks' },
  { value: 'TIKTOK_GIFT', label: 'TikTok Gift' },
  { value: 'INSTAGRAM_BADGE', label: 'Instagram Badge' },
  { value: 'NAVER_STAR', label: t('fanFunding.srcNaverStar') },
  { value: 'EXTERNAL_DONATION', label: t('fanFunding.srcExternalDonation') },
]

// ---- Goal creation ----
const newGoal = ref<{ title: string; targetAmount: number | null; deadline: string }>({
  title: '',
  targetAmount: null,
  deadline: '',
})

async function handleCreateGoal() {
  if (!newGoal.value.title || !newGoal.value.targetAmount) return
  await store.createGoal(
    newGoal.value.title,
    newGoal.value.targetAmount,
    newGoal.value.deadline || undefined,
  )
  newGoal.value = { title: '', targetAmount: null, deadline: '' }
}

async function handleDeleteGoal(id: number) {
  await store.deleteGoal(id)
}

// ---- Source styling ----
function sourceLabel(source: FundingSource): string {
  const labels: Record<FundingSource, string> = {
    SUPER_CHAT: 'Super Chat',
    MEMBERSHIP: t('fanFunding.srcMembership'),
    SUPER_THANKS: 'Super Thanks',
    TIKTOK_GIFT: 'TikTok Gift',
    INSTAGRAM_BADGE: 'Instagram Badge',
    NAVER_STAR: t('fanFunding.srcNaverStar'),
    EXTERNAL_DONATION: t('fanFunding.srcExternalDonation'),
  }
  return labels[source] ?? source
}

function sourceColorClass(source: FundingSource): string {
  const classes: Record<FundingSource, string> = {
    SUPER_CHAT: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    MEMBERSHIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    SUPER_THANKS: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    TIKTOK_GIFT: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    INSTAGRAM_BADGE: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    NAVER_STAR: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
    EXTERNAL_DONATION: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  }
  return classes[source] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
}

function sourceBarColor(source: FundingSource): string {
  const colors: Record<FundingSource, string> = {
    SUPER_CHAT: 'bg-red-500',
    MEMBERSHIP: 'bg-green-500',
    SUPER_THANKS: 'bg-blue-500',
    TIKTOK_GIFT: 'bg-pink-500',
    INSTAGRAM_BADGE: 'bg-purple-500',
    NAVER_STAR: 'bg-emerald-500',
    EXTERNAL_DONATION: 'bg-gray-500',
  }
  return colors[source] ?? 'bg-gray-500'
}

// ---- Platform styling ----
function platformColor(platform: string): string {
  const colors: Record<string, string> = {
    youtube: '#FF0000',
    tiktok: '#000000',
    instagram: '#E4405F',
    naverclip: '#03C75A',
  }
  return colors[platform] ?? '#6B7280'
}

function platformLabel(platform: string): string {
  const labels: Record<string, string> = {
    youtube: 'YouTube',
    tiktok: 'TikTok',
    instagram: 'Instagram',
    naverclip: 'Naver Clip',
  }
  return labels[platform] ?? platform
}

// ---- Formatting ----
function formatKRW(value: number): string {
  if (value >= 100000000) {
    return `${(value / 100000000).toFixed(1)}억원`
  }
  if (value >= 10000) {
    return `${(value / 10000).toFixed(0)}만원`
  }
  return `${value.toLocaleString('ko-KR')}원`
}

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}

// ---- Lifecycle ----
onMounted(() => {
  store.fetchSummary(selectedPeriod.value)
  store.fetchTransactions()
  store.fetchGoals()
})
</script>
