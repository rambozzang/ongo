<template>
  <div class="min-h-full space-y-5 py-5 text-content">
    <PageHeader :title="$t('ugc.title')" :description="$t('ugc.description')">
      <template #actions>
        <button class="btn-primary inline-flex items-center gap-2" :disabled="!hasWorkspace" @click="goCreate">
          <PlusIcon class="h-5 w-5" />
          {{ $t('ugc.newCampaign') }}
        </button>
      </template>
    </PageHeader>

    <div class="mb-6 grid gap-3 mobile:grid-cols-[minmax(0,1fr)_auto]">
      <div v-if="hasWorkspace" class="flex min-w-0 items-center gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-900">
        <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-100 text-body font-bold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">{{ workspaceInitial }}</div>
        <div class="min-w-0">
          <p class="text-[11px] font-semibold uppercase tracking-wide text-gray-400">{{ $t('ugc.campaignContext') }}</p>
          <p class="truncate text-body font-semibold text-gray-900 dark:text-gray-100">{{ workspaceStore.activeWorkspace?.name }}</p>
        </div>
        <span class="ml-auto inline-flex items-center gap-1.5 text-caption text-success-strong"><span class="h-1.5 w-1.5 rounded-full bg-success" />{{ $t('ugc.workspaceReady') }}</span>
      </div>
      <div v-else class="flex items-start gap-3 rounded-xl border border-warning bg-warning-subtle px-4 py-3">
        <ExclamationTriangleIcon class="mt-0.5 h-5 w-5 shrink-0 text-warning-strong" />
        <div class="min-w-0 text-body">
          <p class="font-semibold text-warning-strong">{{ $t('ugc.noWorkspace') }}</p>
          <p class="mt-1 text-warning-strong">{{ $t('ugc.noWorkspaceHint') }}</p>
          <router-link to="/settings?tab=workspaces" class="mt-2 inline-flex font-medium text-warning-strong underline underline-offset-2">{{ $t('ugc.manageWorkspace') }}</router-link>
        </div>
      </div>
      <div v-if="hasWorkspace" class="grid grid-cols-3 gap-2 rounded-xl border border-gray-200 bg-white p-2 dark:border-gray-700 dark:bg-gray-900">
        <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800"><p class="text-[11px] text-gray-400">{{ $t('ugc.totalCampaigns') }}</p><p class="mt-1 text-title font-semibold text-gray-900 dark:text-gray-100">{{ store.totalElements }}</p></div>
        <div class="rounded-lg bg-info-subtle px-3 py-2"><p class="text-[11px] text-info-strong">{{ $t('ugc.status.RECRUITING') }}</p><p class="mt-1 text-title font-semibold text-info-strong">{{ recruitingCount }}</p></div>
        <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800"><p class="text-[11px] text-gray-400">{{ $t('ugc.status.DRAFT') }}</p><p class="mt-1 text-title font-semibold text-gray-900 dark:text-gray-100">{{ draftCount }}</p></div>
      </div>
    </div>

    <!-- Filters -->
    <div class="mb-6 flex flex-col gap-3 mobile:flex-row mobile:items-center">
      <div class="relative flex-1">
        <MagnifyingGlassIcon class="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
        <input
          v-model="searchInput"
          type="text"
          class="input-field pl-10"
          :placeholder="$t('ugc.searchPlaceholder')"
          @keyup.enter="applySearch"
        />
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="opt in statusOptions"
          :key="opt.value ?? 'ALL'"
          :class="[
            'rounded-lg px-3 py-2 text-body font-medium transition-colors',
            store.statusFilter === opt.value
              ? 'bg-primary-600 text-white dark:bg-primary-500'
              : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700',
          ]"
          @click="store.setStatusFilter(opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="py-16 text-center text-body text-gray-400">
      {{ $t('action.loading') }}
    </div>

    <!-- Empty -->
    <div
      v-else-if="store.campaigns.length === 0"
      class="card flex flex-col items-center justify-center gap-3 py-16 text-center"
    >
      <MegaphoneIcon class="h-10 w-10 text-gray-300 dark:text-gray-600" />
      <p class="text-body text-gray-500 dark:text-gray-400">{{ $t('ugc.empty') }}</p>
      <router-link v-if="hasWorkspace" to="/ugc/campaigns/new" class="btn-primary mt-2 inline-flex items-center gap-2">
        <PlusIcon class="h-5 w-5" />
        {{ $t('ugc.newCampaign') }}
      </router-link>
      <router-link v-else to="/settings?tab=workspaces" class="btn-secondary mt-2 inline-flex items-center gap-2">
        {{ $t('ugc.manageWorkspace') }}
      </router-link>
    </div>

    <!-- List -->
    <div v-else class="space-y-3">
      <button
        v-for="c in store.campaigns"
        :key="c.id"
        class="card flex w-full items-center justify-between gap-4 text-left transition-colors hover:border-primary-300 dark:hover:border-primary-700"
        @click="goDetail(c.id)"
      >
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="truncate font-semibold text-gray-900 dark:text-gray-100">{{ c.name }}</span>
            <span :class="['rounded-full px-2 py-0.5 text-caption', statusClass(c.status)]">
              {{ $t(`ugc.status.${c.status}`) }}
            </span>
          </div>
          <p class="mt-1 truncate text-body text-gray-500 dark:text-gray-400">
            {{ c.description || $t('ugc.noDescription') }}
          </p>
          <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-body-xs text-gray-400">
            <span>{{ $t('ugc.budget') }}: {{ formatMoney(c.totalBudget, c.currency) }}</span>
            <span>{{ $t('ugc.reward') }}: {{ formatMoney(c.fixedRewardPerCreator, c.currency) }}</span>
            <span>{{ $t('ugc.period') }}: {{ formatPeriod(c.startAt, c.endAt) }}</span>
          </div>
        </div>
        <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-300 dark:text-gray-600" />
      </button>
    </div>

    <!-- Pagination -->
    <div v-if="!store.loading && store.totalPages > 1" class="mt-6 flex items-center justify-center gap-3">
      <button class="btn-secondary" :disabled="!store.hasPrevPage" @click="store.prevPage()">
        {{ $t('ugc.prev') }}
      </button>
      <span class="text-body text-gray-500 dark:text-gray-400">
        {{ store.page + 1 }} / {{ store.totalPages }}
      </span>
      <button class="btn-secondary" :disabled="!store.hasNextPage" @click="store.nextPage()">
        {{ $t('ugc.next') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUgcCampaignStore } from '@/stores/ugcCampaign'
import { useWorkspaceStore } from '@/stores/workspace'
import { useNotificationStore } from '@/stores/notification'
import type { CampaignStatus } from '@/api/ugcCampaign'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  PlusIcon,
  MagnifyingGlassIcon,
  MegaphoneIcon,
  ChevronRightIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const router = useRouter()
const store = useUgcCampaignStore()
const workspaceStore = useWorkspaceStore()
const notify = useNotificationStore()

const searchInput = ref('')
const hasWorkspace = computed(() => workspaceStore.activeWorkspace != null)
const workspaceInitial = computed(() => workspaceStore.activeWorkspace?.name?.charAt(0).toUpperCase() ?? '?')
const recruitingCount = computed(() => store.campaigns.filter(c => c.status === 'RECRUITING').length)
const draftCount = computed(() => store.campaigns.filter(c => c.status === 'DRAFT').length)

const statusOptions = computed<{ label: string; value: string | null }[]>(() => [
  { label: t('ugc.filterAll'), value: null },
  { label: t('ugc.status.DRAFT'), value: 'DRAFT' },
  { label: t('ugc.status.RECRUITING'), value: 'RECRUITING' },
  { label: t('ugc.status.ACTIVE'), value: 'ACTIVE' },
  { label: t('ugc.status.COMPLETED'), value: 'COMPLETED' },
])

function statusClass(status: CampaignStatus): string {
  switch (status) {
    case 'RECRUITING':
      return 'bg-info-subtle text-info-strong'
    case 'ACTIVE':
      return 'bg-success-subtle text-success-strong'
    case 'PAUSED':
      return 'bg-warning-subtle text-warning-strong'
    case 'CANCELLED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatMoney(amount: number, currency: string): string {
  return `${new Intl.NumberFormat('ko-KR').format(amount)} ${currency}`
}

function formatPeriod(startAt: string | null, endAt: string | null): string {
  if (!startAt || !endAt) return '-'
  return `${startAt.slice(0, 10)} ~ ${endAt.slice(0, 10)}`
}

function applySearch() {
  store.setQuery(searchInput.value)
}

function goCreate() {
  router.push('/ugc/campaigns/new')
}

function goDetail(id: number) {
  router.push(`/ugc/campaigns/${id}`)
}

onMounted(async () => {
  try {
    await store.fetchCampaigns()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  }
})
</script>
